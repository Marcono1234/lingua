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
import com.github.pemistahl.lingua.internal.model.QuadriFivegramRelativeFrequencyLookup
import com.github.pemistahl.lingua.internal.model.UniBiTrigramRelativeFrequencyLookup
import com.github.pemistahl.lingua.internal.util.EnumDoubleMap
import com.github.pemistahl.lingua.internal.util.EnumIntMap
import com.github.pemistahl.lingua.internal.util.KeyIndexer
import com.github.pemistahl.lingua.internal.util.ResettableLazy
import com.github.pemistahl.lingua.internal.util.WordList
import com.github.pemistahl.lingua.internal.util.extension.containsAnyOf
import com.github.pemistahl.lingua.internal.util.extension.enumMapOf
import com.github.pemistahl.lingua.internal.util.extension.filter
import com.github.pemistahl.lingua.internal.util.extension.intersect
import com.github.pemistahl.lingua.internal.util.extension.isLogogram
import com.github.pemistahl.lingua.internal.util.extension.replaceAll
import java.util.EnumMap
import java.util.EnumSet
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.CompletableFuture
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
     * @param text The input text to detect the language for.
     * @return A map of all possible languages, sorted by their confidence value in descending order.
     */
    @Suppress("unused") // public API
    fun computeLanguageConfidenceValues(text: String): SortedMap<Language, Double> {
        val cleanedUpText = cleanUpInputText(text)

        if (cleanedUpText.isEmpty() || !cleanedUpText.codePoints().anyMatch(Character::isLetter)) {
            return TreeMap()
        }

        val wordList = WordList.build(text)
        val languageDetectedByRules = detectLanguageWithRules(wordList)

        if (languageDetectedByRules != UNKNOWN) {
            return sortedMapOf(languageDetectedByRules to 1.0)
        }

        val filteredLanguages = filterLanguagesByRules(wordList)

        if (filteredLanguages.size == 1) {
            val filteredLanguage = filteredLanguages.iterator().next()
            return sortedMapOf(filteredLanguage to 1.0)
        }

        if (isLowAccuracyModeEnabled && cleanedUpText.length < 3) {
            return TreeMap()
        }

        val isLongText = cleanedUpText.length >= HIGH_ACCURACY_MODE_MAX_TEXT_LENGTH
        val ngramSizeRange = if (isLongText || isLowAccuracyModeEnabled) {
            (3..3)
        } else {
            (1..5)
        }
        val allProbabilitiesAndUnigramCounts = ngramSizeRange.filter { i -> cleanedUpText.length >= i }
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
            .map { it.join() }

        val allProbabilities = allProbabilitiesAndUnigramCounts.map { (probabilities, _) -> probabilities }
        val unigramCounts = allProbabilitiesAndUnigramCounts[0].second ?: EnumIntMap.newMap(languagesSubsetIndexer)
        val summedUpProbabilities = sumUpProbabilities(allProbabilities, unigramCounts, filteredLanguages)
        val highestProbability = summedUpProbabilities.maxValueOrNull() ?: return TreeMap()
        val confidenceValues = summedUpProbabilities.mapNonZeroValues { highestProbability / it }
        return confidenceValues.sortedByNonZeroDescendingValue()
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

    private fun UniBiTrigramRelativeFrequencyLookup.getFrequency(ngram: PrimitiveNgram): Double {
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

    private fun detectLanguageWithRules(wordList: WordList): Language {
        // Using Double because logograms are not counted as full word
        var adjustedWordCount = 0.0
        val totalLanguageCounts = EnumDoubleMap.newMap(wordLanguagesSubsetIndexer)
        val wordLanguageCounts = EnumIntMap.newMap(wordLanguagesSubsetIndexer)

        wordList.forEach { word ->
            // Reuse same map to avoid creating new objects
            wordLanguageCounts.clear()

            for (character in word) {
                val script = Character.UnicodeScript.of(character.code)

                val alphabetLanguage = alphabetsSupportingExactlyOneLanguage[script]
                if (alphabetLanguage != null) {
                    wordLanguageCounts.increment(alphabetLanguage)
                } else {
                    when {
                        script == Character.UnicodeScript.HAN -> wordLanguageCounts.increment(CHINESE)
                        isJapaneseScript(script) -> wordLanguageCounts.increment(JAPANESE)
                        script == Character.UnicodeScript.LATIN ||
                            script == Character.UnicodeScript.CYRILLIC ||
                            // TODO: ktlint incorrectly indents lambda below; might be fixed in newer version
                            script == Character.UnicodeScript.DEVANAGARI -> {
                            // Note: Don't use any `filter` or `forEach` here because it might end up creating
                            // a lot of objects
                            for (language in languagesWithUniqueCharacters) {
                                if (language.uniqueCharacters?.contains(character) == true) {
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

    private fun filterLanguagesByRules(wordList: WordList): Set<Language> {
        // Using Double because logograms are not counted as full word
        var adjustedWordCount = 0.0
        val detectedAlphabets = EnumDoubleMap.newMap(Language.allScriptsIndexer)

        wordList.forEach { word ->
            var wordValue = FULL_WORD_VALUE
            for (unicodeScript in Language.allScripts) {
                if (word.all { Character.UnicodeScript.of(it.code) == unicodeScript }) {
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

        for ((characters, languages) in CHARS_TO_LANGUAGES_MAPPING) {
            // Uses array to reduce object creation during iteration
            val relevantLanguages = languages.intersect(filteredLanguages).toTypedArray()

            wordList.forEach { word ->
                if (word.containsAnyOf(characters)) {
                    for (language in relevantLanguages) {
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
        val probabilityFutures = filteredLanguages.map { language ->
            CompletableFuture.supplyAsync(
                {
                    val modelHolder = languageModels[language]!!.value()
                    val uniBiTrigramsLookup = modelHolder.uniBiTrigramsLookup
                    val quadriFivegramLookup = when {
                        // When model only contains primitives don't have to load quadriFivegramLookup
                        testDataModel.hasOnlyPrimitives() -> QuadriFivegramRelativeFrequencyLookup.empty
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
        }.toTypedArray()

        /*
         * Uses CompletableFuture.allOf instead of submitting another future with supplyAsync to executor
         * and having it wait for all previous futures to avoid deadlock: Executor might choose to first
         * run this combined future (instead of probability futures), and depending on free number of workers
         * probability futures might therefore wait for combined future to finish -> deadlock
         */
        return CompletableFuture.allOf(*probabilityFutures).thenApply {
            val probabilities = EnumDoubleMap.newMap(languagesSubsetIndexer)
            // These `join` calls should not block because `allOf` should have made sure all futures are completed
            probabilityFutures.map { it.join() }.forEach { (language, p) ->
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
        uniBiTrigramsLookup: UniBiTrigramRelativeFrequencyLookup,
        quadriFivegramLookup: QuadriFivegramRelativeFrequencyLookup,
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
        else -> true
    }

    override fun hashCode() =
        31 * languages.hashCode() + minimumRelativeDistance.hashCode() + isLowAccuracyModeEnabled.hashCode() +
            executor.hashCode()

    internal data class LanguageModelHolder(
        val uniBiTrigramsLookup: UniBiTrigramRelativeFrequencyLookup,
        // Lookup for quadrigrams and fivegrams is lazy since it won't be used when
        // large texts are analyzed
        val quadriFivegramLookup: Lazy<QuadriFivegramRelativeFrequencyLookup>
    )

    internal companion object {
        private const val HIGH_ACCURACY_MODE_MAX_TEXT_LENGTH = 120

        internal var languageModels: Map<Language, ResettableLazy<LanguageModelHolder>> = EnumMap(
            Language.all().asSequence()
                .associateWith {
                    val languageCode = it.isoCode639_1.toString()
                    ResettableLazy {
                        LanguageModelHolder(
                            UniBiTrigramRelativeFrequencyLookup.fromBinary(languageCode),
                            lazy {
                                QuadriFivegramRelativeFrequencyLookup.fromBinary(languageCode)
                            }
                        )
                    }
                }
        )
    }
}
