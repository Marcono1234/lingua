/*
 * Copyright © 2018-today Peter M. Stahl pemistahl@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pemistahl.lingua.api

import com.github.pemistahl.lingua.api.Language.CHINESE
import com.github.pemistahl.lingua.api.Language.JAPANESE
import com.github.pemistahl.lingua.api.Language.UNKNOWN
import com.github.pemistahl.lingua.internal.Constant.CHARS_TO_LANGUAGES_MAPPING
import com.github.pemistahl.lingua.internal.Constant.LANGUAGES_SUPPORTING_LOGOGRAMS
import com.github.pemistahl.lingua.internal.Constant.MULTIPLE_WHITESPACE
import com.github.pemistahl.lingua.internal.Constant.NUMBERS_AND_PUNCTUATION
import com.github.pemistahl.lingua.internal.Constant.isJapaneseScript
import com.github.pemistahl.lingua.internal.Constant.languagesWithCharsIndexer
import com.github.pemistahl.lingua.internal.PrimitiveNgram
import com.github.pemistahl.lingua.internal.ReusableObjectNgram
import com.github.pemistahl.lingua.internal.TestDataLanguageModel
import com.github.pemistahl.lingua.internal.internalDetectMultiLanguageOf
import com.github.pemistahl.lingua.internal.model.lookup.QuadriFivegramBinarySearchLookup
import com.github.pemistahl.lingua.internal.model.lookup.QuadriFivegramLookup
import com.github.pemistahl.lingua.internal.model.lookup.UniBiTrigramBinarySearchLookup
import com.github.pemistahl.lingua.internal.model.lookup.UniBiTrigramLookup
import com.github.pemistahl.lingua.internal.util.EnumDoubleMap
import com.github.pemistahl.lingua.internal.util.EnumIntMap
import com.github.pemistahl.lingua.internal.util.KeyIndexer
import com.github.pemistahl.lingua.internal.util.ResettableLazy
import com.github.pemistahl.lingua.internal.util.WordList
import com.github.pemistahl.lingua.internal.util.extension.allOfToList
import com.github.pemistahl.lingua.internal.util.extension.enumMapOf
import com.github.pemistahl.lingua.internal.util.extension.filter
import com.github.pemistahl.lingua.internal.util.extension.intersect
import com.github.pemistahl.lingua.internal.util.extension.isLogogram
import com.github.pemistahl.lingua.internal.util.extension.replaceAll
import it.unimi.dsi.fastutil.objects.Object2DoubleSortedMap
import it.unimi.dsi.fastutil.objects.Object2DoubleSortedMaps
import java.lang.Character.UnicodeScript
import java.util.EnumMap
import java.util.EnumSet
import java.util.SortedMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Executor
import kotlin.math.ln

private const val FULL_WORD_VALUE = 1.0
/**
 * Word value for a logogram.
 *
 * At least compared to English it appears for languages with logograms, such as Chinese,
 * more words (=logograms) are needed to express the same thing, therefore don't count a
 * logogram as [full word][FULL_WORD_VALUE]
 */
private const val LOGOGRAM_WORD_VALUE = 0.7

/**
 * Detects the language of given input text.
 */
class LanguageDetector internal constructor(
    internal val languages: EnumSet<Language>,
    internal val minimumRelativeDistance: Double,
    isEveryLanguageModelPreloaded: Boolean,
    internal val isLowAccuracyModeEnabled: Boolean,
    private val executor: Executor,
    private val increasedDetectionSpeed: Boolean,
    internal val numberOfLoadedLanguages: Int = languages.size,
) {
    // Stored as Array to reduce object creation during iteration
    private val languagesWithUniqueCharacters = languages.filterNot { it.uniqueCharacters.isNullOrBlank() }
        .toTypedArray()
    private val alphabetsSupportingExactlyOneLanguage = enumMapOf(
        Language.scriptsSupportingExactlyOneLanguage.filterValues {
            it in languages
        }
    )
    /** Indexer for maps containing only the constants of [languages] as key */
    private val languagesSubsetIndexer = KeyIndexer.fromEnumConstants(languages)
    /** Indexer for maps used as part of rule based word detection */
    private val wordLanguagesSubsetIndexer = KeyIndexer.fromEnumConstants(
        languagesWithUniqueCharacters
            .plus(alphabetsSupportingExactlyOneLanguage.values)
            // Japanese and Chinese have custom detection
            .plus(listOf(UNKNOWN, JAPANESE, CHINESE))
            .toSet()
    )

    init {
        languages.forEach {
            languageModels[it]!!.updateParameter(increasedDetectionSpeed)
        }
        if (isEveryLanguageModelPreloaded) {
            preloadLanguageModels()
        }
    }

    /**
     * Detects the language of given input text.
     *
     * @param text The input text to detect the language for.
     * @return The identified language or [Language.UNKNOWN].
     */
    fun detectLanguageOf(text: String): Language {
        val confidenceValues = computeLanguageConfidenceValues(text)
        return getLanguageFromConfidenceValues(confidenceValues)
    }

    internal fun getLanguageFromConfidenceValues(confidenceValues: SortedMap<Language, Double>): Language {
        if (confidenceValues.isEmpty()) return UNKNOWN

        val mostLikelyLanguage = confidenceValues.firstKey()
        if (confidenceValues.size == 1) return mostLikelyLanguage

        val mostLikelyLanguageProbability = confidenceValues.getValue(mostLikelyLanguage)
        val secondMostLikelyLanguageProbability = confidenceValues.values.elementAt(1)

        return when {
            mostLikelyLanguageProbability == secondMostLikelyLanguageProbability -> UNKNOWN
            (mostLikelyLanguageProbability - secondMostLikelyLanguageProbability) < minimumRelativeDistance -> UNKNOWN
            else -> mostLikelyLanguage
        }
    }

    /**
     * Language detection result for a section of the input text.
     *
     * The start and end indices are estimates and might not be completely accurate. They usually
     * mark the first and the last letter of the section.
     *
     * **Important:** For short sections (about [< 25 letters][lettersCount]) the accuracy of the detected
     * language and the [confidence values][confidenceValues] might not be very good; unless the confidence
     * values only contain a single entry. Such short sections can occur when the language detector detected
     * that a section is in a different language (for example because a different script is used), but it
     * was unable to tell which language exactly is used.
     */
    data class LanguageSection(
        /** Start index (inclusive, starting at 0) of this section within the complete text. */
        val start: Int,
        /** End index (exclusive, starting at 0) of this section within the complete text. */
        val end: Int,
        /** Numbers of letters in this section. */
        val lettersCount: Int,
        /** Text of this section (from [start] to [end] of the complete input text). */
        val sectionText: String,
        /**
         * The identified language or [Language.UNKNOWN]. This is affected by the
         * [minimum relative distance][LanguageDetectorBuilder.withMinimumRelativeDistance].
         *
         * @see LanguageDetector.detectLanguageOf
         */
        val language: Language,
        /**
         * The relative confidence values for every language considered possible for this section.
         * Can be empty if no language was considered possible.
         *
         * @see LanguageDetector.computeLanguageConfidenceValues
         */
        val confidenceValues: SortedMap<Language, Double>,
    )

    /**
     * Detects the languages of text which is potentially written in multiple languages.
     *
     * The result is a list of sections from the original text which were detected as being written
     * in a certain language. The list can be empty if no languages were detected (this is usually
     * the case when the text contains no letters). The sections might not cover the complete input
     * text, text pieces which consist of non-letters might not be part of any of the returned sections.
     * The accuracy of this function is greatly reduced when the
     * [low accuracy mode][LanguageDetectorBuilder.withLowAccuracyMode] is used.
     *
     * **Warning:** This feature is experimental. The performance of this function might not be
     * good, especially for long texts.
     */
    fun detectMultiLanguageOf(text: String): List<LanguageSection> {
        // Note: The implementation is extracted to a separate file to avoid reducing readability of this class
        // too much, and to making merging in changes from original Lingua easier
        return internalDetectMultiLanguageOf(text)
    }

    /**
     * Computes confidence values for every language considered possible for the given input text.
     *
     * The values that this method computes are part of a **relative** confidence metric, not of an absolute one.
     * Each value is a number between 0.0 and 1.0. The most likely language is always returned with value 1.0.
     * All other languages get values assigned which are lower than 1.0, denoting how less likely those languages
     * are in comparison to the most likely language.
     *
     * The map returned by this method does not necessarily contain all languages which the calling instance of
     * [LanguageDetector] was built from. If the rule-based engine decides that a specific language is truly impossible,
     * then it will not be part of the returned map. Likewise, if no ngram probabilities can be found within the
     * detector's languages for the given input text, the returned map will be empty. The confidence value for
     * each language not being part of the returned map is assumed to be 0.0.
     *
     * The returned map is a [SortedMap] instead of a regular [Map] to provide convenience functions such as
     * [SortedMap.firstKey] (note however that the returned map might be empty). The map might not implement
     * submap functions such as [SortedMap.tailMap], and [SortedMap.comparator] might return `null`.
     *
     * @param text The input text to detect the language for.
     * @return A map of all possible languages, sorted by their confidence value in descending order.
     */
    @Suppress("unused") // public API
    fun computeLanguageConfidenceValues(text: String): SortedMap<Language, Double> {
        return computeLanguageConfidenceValuesFuture(text).join()
    }

    private fun singleLanguageConfidenceMap(language: Language): Object2DoubleSortedMap<Language> {
        return Object2DoubleSortedMaps.singleton(language, 1.0)
    }

    internal fun computeLanguageConfidenceValuesFuture(text: String):
        CompletableFuture<Object2DoubleSortedMap<Language>> {

        val cleanedUpText = cleanUpInputText(text)

        if (cleanedUpText.isEmpty() || !cleanedUpText.codePoints().anyMatch(Character::isLetter)) {
            return completedFuture(Object2DoubleSortedMaps.emptyMap())
        }

        val wordList = WordList.build(text)
        val languageDetectedByRules = detectLanguageWithRules(wordList)

        if (languageDetectedByRules != UNKNOWN) {
            return completedFuture(singleLanguageConfidenceMap(languageDetectedByRules))
        }

        val filteredLanguages = filterLanguagesByRules(wordList)

        if (filteredLanguages.size == 1) {
            val filteredLanguage = filteredLanguages.iterator().next()
            return completedFuture(singleLanguageConfidenceMap(filteredLanguage))
        }

        if (isLowAccuracyModeEnabled && cleanedUpText.length < 3) {
            return completedFuture(Object2DoubleSortedMaps.emptyMap())
        }

        val isLongText = cleanedUpText.length >= HIGH_ACCURACY_MODE_MAX_TEXT_LENGTH
        val ngramSizeRange = if (isLongText || isLowAccuracyModeEnabled) {
            (3..3)
        } else {
            (1..5)
        }
        return ngramSizeRange.filter { i -> cleanedUpText.length >= i }
            .map { ngramLength ->
                val testDataModel = TestDataLanguageModel.fromText(cleanedUpText, ngramLength)
                computeLanguageProbabilities(testDataModel, filteredLanguages)
                    .thenApply { probabilities ->
                        val unigramCounts = if (ngramLength == 1) {
                            val languages = probabilities.getNonZeroKeys()

                            val unigramFilteredLanguages =
                                if (languages.isNotEmpty()) filteredLanguages.asSequence()
                                    .filter { languages.contains(it) }
                                    .toSet()
                                else filteredLanguages
                            countUnigramsOfInputText(testDataModel, unigramFilteredLanguages)
                        } else {
                            null
                        }

                        return@thenApply Pair(probabilities, unigramCounts)
                    }
            }
            .allOfToList()
            .thenApply { allProbabilitiesAndUnigramCounts ->
                val allProbabilities = allProbabilitiesAndUnigramCounts.map { (probabilities, _) -> probabilities }
                val unigramCounts = allProbabilitiesAndUnigramCounts[0].second
                    ?: EnumIntMap.newMap(languagesSubsetIndexer)
                val summedUpProbabilities = sumUpProbabilities(allProbabilities, unigramCounts, filteredLanguages)
                val highestProbability = summedUpProbabilities.maxValueOrNull()
                    ?: return@thenApply Object2DoubleSortedMaps.emptyMap()
                val confidenceValues = summedUpProbabilities.mapNonZeroValues { highestProbability / it }
                return@thenApply confidenceValues.sortedByNonZeroDescendingValue()
            }
    }

    /**
     * Unloads all language models loaded by this [LanguageDetector] instance
     * and frees associated resources.
     *
     * This will be useful if the library is used within a web application inside
     * an application server. By calling this method prior to undeploying the
     * web application, the language models are removed and memory is freed.
     * This prevents exceptions such as [OutOfMemoryError] when the web application
     * is redeployed multiple times.
     *
     * This function should be used with care. Loaded language models are shared
     * between all language detector instances. Unloading models which are still
     * used by other language detector instances requires them to load these
     * models again which will decrease their performance.
     */
    @Suppress("unused") // public API
    fun unloadLanguageModels() {
        languages.forEach {
            languageModels[it]!!.reset()
        }
    }

    private fun cleanUpInputText(text: String): CharSequence {
        return text.trim().lowercase()
            .replaceAll(
                listOf(
                    NUMBERS_AND_PUNCTUATION to "",
                    MULTIPLE_WHITESPACE to " "
                )
            )
    }

    private fun UniBiTrigramLookup.getFrequency(ngram: PrimitiveNgram): Double {
        val (length, char0, char1, char2) = ngram
        return getFrequency(length, char0, char1, char2)
    }

    private fun countUnigramsOfInputText(
        unigramLanguageModel: TestDataLanguageModel,
        filteredLanguages: Set<Language>
    ): EnumIntMap<Language> {
        val unigramCounts = EnumIntMap.newMap(languagesSubsetIndexer)
        for (language in filteredLanguages) {
            val lookup = languageModels[language]!!.value().uniBiTrigramsLookup

            // Only have to check primitiveNgrams since unigrams are always encoded as primitive
            unigramLanguageModel.primitiveNgrams.forEach {
                val probability = lookup.getFrequency(PrimitiveNgram(it))
                if (probability > 0) {
                    unigramCounts.increment(language)
                }
            }
        }
        return unigramCounts
    }

    private fun sumUpProbabilities(
        probabilities: List<EnumDoubleMap<Language>>,
        unigramCountsOfInputText: EnumIntMap<Language>,
        filteredLanguages: Set<Language>
    ): EnumDoubleMap<Language> {
        val summedUpProbabilities = EnumDoubleMap.newMap(languagesSubsetIndexer)
        for (language in filteredLanguages) {
            var sum = 0.0
            for (probabilityMap in probabilities) {
                sum += probabilityMap.getOrZero(language)
            }
            summedUpProbabilities.set(language, sum)

            unigramCountsOfInputText.ifNonZero(language) { unigramCount ->
                summedUpProbabilities.set(language, summedUpProbabilities.getOrZero(language) / unigramCount)
            }
        }
        return summedUpProbabilities
    }

    /**
     * @return [Language.UNKNOWN] if language could not be detected
     */
    internal fun detectLanguageWithRules(wordList: WordList): Language {
        // Using Double because logograms are not counted as full word
        var adjustedWordCount = 0.0
        val totalLanguageCounts = EnumDoubleMap.newMap(wordLanguagesSubsetIndexer)
        val wordLanguageCounts = EnumIntMap.newMap(wordLanguagesSubsetIndexer)

        wordList.forEach { word ->
            // Reuse same map to avoid creating new objects
            wordLanguageCounts.clear()

            for (char in word) {
                val script = UnicodeScript.of(char.code)

                val alphabetLanguage = alphabetsSupportingExactlyOneLanguage[script]
                if (alphabetLanguage != null) {
                    wordLanguageCounts.increment(alphabetLanguage)
                } else {
                    when {
                        script == UnicodeScript.HAN -> wordLanguageCounts.increment(CHINESE)
                        isJapaneseScript(script) -> wordLanguageCounts.increment(JAPANESE)
                        script == UnicodeScript.LATIN ||
                            script == UnicodeScript.CYRILLIC ||
                            // TODO: ktlint incorrectly indents lambda below; might be fixed in newer version
                            script == UnicodeScript.DEVANAGARI -> {
                            // Note: Don't use any `filter` or `forEach` here because it might end up creating
                            // a lot of objects
                            for (language in languagesWithUniqueCharacters) {
                                if (language.uniqueCharacters?.contains(char) == true) {
                                    wordLanguageCounts.increment(language)
                                }
                            }
                        }
                    }
                }
            }

            var wordValue = FULL_WORD_VALUE
            val languageCounts = wordLanguageCounts.countNonZeroValues()

            if (languageCounts == 0) {
                totalLanguageCounts.increment(UNKNOWN, wordValue)
            } else if (languageCounts == 1) {
                val language = wordLanguageCounts.firstNonZero()!!
                if (language in languages) {
                    if (word.isLogogram()) {
                        wordValue = LOGOGRAM_WORD_VALUE
                    }
                    totalLanguageCounts.increment(language, wordValue)
                } else {
                    totalLanguageCounts.increment(UNKNOWN, wordValue)
                }
            } else {
                val sortedWordLanguageCounts = wordLanguageCounts.descendingIterator()
                val mostFrequent = sortedWordLanguageCounts.next()
                val mostFrequentLanguage = mostFrequent.key
                val firstCharCount = mostFrequent.value
                val secondCharCount = sortedWordLanguageCounts.next().value

                if (firstCharCount > secondCharCount && mostFrequentLanguage in languages) {
                    totalLanguageCounts.increment(mostFrequentLanguage, wordValue)
                } else {
                    totalLanguageCounts.increment(UNKNOWN, wordValue)
                }
            }

            adjustedWordCount += wordValue
        }

        val unknownLanguageCount = totalLanguageCounts.getOrZero(UNKNOWN)
        if (unknownLanguageCount < (0.4 * adjustedWordCount)) {
            totalLanguageCounts.set(UNKNOWN, 0.0)
        }

        val languagesCount = totalLanguageCounts.countNonZeroValues()
        if (languagesCount == 0) {
            return UNKNOWN
        }
        if (languagesCount == 1) {
            return totalLanguageCounts.firstNonZero()!!
        }
        if (languagesCount == 2 &&
            totalLanguageCounts.hasNonZeroValue(CHINESE) &&
            totalLanguageCounts.hasNonZeroValue(JAPANESE)
        ) {
            return JAPANESE
        }
        val sortedTotalLanguageCounts = totalLanguageCounts.descendingIterator()
        val mostFrequent = sortedTotalLanguageCounts.next()
        val mostFrequentLanguage = mostFrequent.key
        val firstWordCount = mostFrequent.value
        val secondWordCount = sortedTotalLanguageCounts.next().value

        return when {
            // If word counts are too close to each other return UNKNOWN
            secondWordCount / firstWordCount > 0.8 -> UNKNOWN
            else -> mostFrequentLanguage
        }
    }

    internal fun filterLanguagesByRules(wordList: WordList): EnumSet<Language> {
        // Using Double because logograms are not counted as full word
        var adjustedWordCount = 0.0
        val detectedAlphabets = EnumDoubleMap.newMap(Language.allScriptsIndexer)

        wordList.forEach { word ->
            var wordValue = FULL_WORD_VALUE
            for (unicodeScript in Language.allScripts) {
                if (word.all { UnicodeScript.of(it.code) == unicodeScript }) {
                    if (word.isLogogram()) {
                        wordValue = LOGOGRAM_WORD_VALUE
                    }
                    detectedAlphabets.increment(unicodeScript, wordValue)
                    break
                }
            }
            adjustedWordCount += wordValue
        }

        if (detectedAlphabets.hasOnlyZeroValues()) {
            return languages
        }

        val alphabetsIterator = detectedAlphabets.descendingIterator()
        val mostFrequentAlphabet = alphabetsIterator.next()
        val mostFrequentAlphabets = EnumSet.of(mostFrequentAlphabet.key)
        val mostFrequentAlphabetCount = mostFrequentAlphabet.value

        // Add all alphabets which are close to the most frequent one
        while (alphabetsIterator.hasNext()) {
            val nextMostFrequent = alphabetsIterator.next()
            if (nextMostFrequent.value / mostFrequentAlphabetCount >= 0.8) {
                mostFrequentAlphabets.add(nextMostFrequent.key)
            } else {
                break
            }
        }

        val filteredLanguages = languages.filter {
            it.unicodeScriptsArray.any { script -> mostFrequentAlphabets.contains(script) }
        }
        val languageCounts = EnumIntMap.newMap(languagesWithCharsIndexer)

        // Reuse same EnumSet to avoid creating new instances per word
        val languagesToCount = EnumSet.noneOf(Language::class.java)
        wordList.forEach { word ->
            languagesToCount.clear()
            languagesToCount.addAll(filteredLanguages)

            for (char in word) {
                val languages = CHARS_TO_LANGUAGES_MAPPING.get(char)
                languages?.forEach { language ->
                    // Use `remove` to only count a language at most once per word
                    if (languagesToCount.remove(language)) {
                        languageCounts.increment(language)
                    }
                }
            }
        }

        val languagesSubset = languageCounts.keysWithValueLargerEqualThan(adjustedWordCount / 2.0)

        return if (languagesSubset.isNotEmpty()) {
            filteredLanguages.intersect(languagesSubset)
        } else {
            filteredLanguages
        }
    }

    private fun computeLanguageProbabilities(
        testDataModel: TestDataLanguageModel,
        filteredLanguages: Set<Language>,
    ): CompletableFuture<EnumDoubleMap<Language>> {
        return filteredLanguages
            .map { language ->
                CompletableFuture.supplyAsync(
                    {
                        val modelHolder = languageModels[language]!!.value()
                        val uniBiTrigramsLookup = modelHolder.uniBiTrigramsLookup
                        val quadriFivegramLookup = when {
                            // When model only contains primitives don't have to load quadriFivegramLookup
                            testDataModel.hasOnlyPrimitives() -> QuadriFivegramLookup.empty
                            else -> modelHolder.quadriFivegramLookup.value
                        }

                        return@supplyAsync language to computeSumOfNgramProbabilities(
                            uniBiTrigramsLookup,
                            quadriFivegramLookup,
                            testDataModel
                        )
                    },
                    executor
                )
            }
            .allOfToList()
            .thenApply { languageProbabilities ->
                val probabilities = EnumDoubleMap.newMap(languagesSubsetIndexer)
                languageProbabilities.forEach { (language, p) ->
                    var probability = p
                    if (probability < 0.0) {
                        // For languages with logograms increase probability since their words (=logograms)
                        // consist only of a single char compared to other languages whose words consist
                        // of multiple chars
                        if (language in LANGUAGES_SUPPORTING_LOGOGRAMS) {
                            // Multiply by value < 1.0 since a smaller probability is better
                            probability *= 0.85
                        }
                        probabilities.set(language, probability)
                    }
                }

                return@thenApply probabilities
            }
    }

    private fun computeSumOfNgramProbabilities(
        uniBiTrigramsLookup: UniBiTrigramLookup,
        quadriFivegramLookup: QuadriFivegramLookup,
        testDataModel: TestDataLanguageModel
    ): Double {
        var probabilitySum = 0.0
        // Reuse same object to avoid creating new objects for sub-ngrams
        val objectNgram = ReusableObjectNgram()

        ngramLoop@ for (ngram in testDataModel.objectNgrams) {
            // Reuse same object to reduce memory allocations for substring creation
            objectNgram.setNgram(ngram)

            var currentPrimitive: PrimitiveNgram
            while (true) {
                val (length, char0, char1, char2, char3, char4) = objectNgram
                val probability = quadriFivegramLookup.getFrequency(
                    length,
                    char0, char1, char2, char3, char4
                ) {
                    assert(ngram.length == 5)
                    // Return the original ngram String (assuming it is a fivegram)
                    ngram
                }

                if (probability > 0) {
                    probabilitySum += ln(probability)
                    continue@ngramLoop
                }

                if (!objectNgram.toLowerOrderNgram()) {
                    currentPrimitive = objectNgram.getLowerOrderPrimitiveNgram()
                    break
                }
            }

            do {
                val probability = uniBiTrigramsLookup.getFrequency(currentPrimitive)
                if (probability > 0) {
                    probabilitySum += ln(probability)
                    break
                }

                currentPrimitive = currentPrimitive.getLowerOrderNgram()
            } while (currentPrimitive.value != PrimitiveNgram.NONE)
        }

        testDataModel.primitiveNgrams.forEach {
            var current = PrimitiveNgram(it)
            do {
                val probability = uniBiTrigramsLookup.getFrequency(current)
                if (probability > 0) {
                    probabilitySum += ln(probability)
                    break
                }

                current = current.getLowerOrderNgram()
            } while (current.value != PrimitiveNgram.NONE)
        }

        return probabilitySum
    }

    private fun preloadLanguageModels() {
        val futures = languages.map {
            CompletableFuture.runAsync(
                {
                    // Initialize values of Lazy objects
                    val modelHolder = languageModels[it]!!.value()
                    if (!isLowAccuracyModeEnabled) {
                        modelHolder.quadriFivegramLookup.value
                    }
                },
                executor
            )
        }.toTypedArray()
        // Wait for futures to finish, to let caller know in case loading fails
        CompletableFuture.allOf(*futures).join()
    }

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LanguageDetector -> false
        languages != other.languages -> false
        minimumRelativeDistance != other.minimumRelativeDistance -> false
        isLowAccuracyModeEnabled != other.isLowAccuracyModeEnabled -> false
        executor != other.executor -> false
        increasedDetectionSpeed != other.increasedDetectionSpeed -> false
        else -> true
    }

    override fun hashCode() =
        31 * languages.hashCode() + minimumRelativeDistance.hashCode() + isLowAccuracyModeEnabled.hashCode() +
            executor.hashCode() + increasedDetectionSpeed.hashCode()

    /**
     * Has two types of lookups (uni-, bi- and trigrams, and quadri- and fivegrams)
     * since that is how LanguageDetector currently uses the lookups; for short texts
     * it creates ngrams of all lengths (1 - 5), for long texts it only creates trigrams
     * and then lower order ngrams. Therefore these two lookup types allow lazily loading
     * the required models into memory.
     */
    internal data class LanguageModelHolder(
        val uniBiTrigramsLookup: UniBiTrigramLookup,
        // Lookup for quadrigrams and fivegrams is lazy since it won't be used when
        // large texts are analyzed
        val quadriFivegramLookup: Lazy<QuadriFivegramLookup>
    )

    internal companion object {
        private const val HIGH_ACCURACY_MODE_MAX_TEXT_LENGTH = 120

        internal val languageModels: Map<Language, ResettableLazy<LanguageModelHolder, Boolean>> = EnumMap(
            Language.all().asSequence()
                .associateWith {
                    val languageCode = it.isoCode639_1.toString()
                    // Initially create without increased detection speed (false)
                    ResettableLazy(false) { increasedDetectionSpeed ->
                        LanguageModelHolder(
                            UniBiTrigramBinarySearchLookup.fromBinary(languageCode).let { lookup ->
                                if (increasedDetectionSpeed) lookup.asHashMapLookup() else lookup
                            },
                            lazy {
                                QuadriFivegramBinarySearchLookup.fromBinary(languageCode).let { lookup ->
                                    if (increasedDetectionSpeed) lookup.asHashMapLookup() else lookup
                                }
                            }
                        )
                    }
                }
        )
    }
}
