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
import com.github.pemistahl.lingua.internal.Constant.NUMBERS
import com.github.pemistahl.lingua.internal.Constant.PUNCTUATION
import com.github.pemistahl.lingua.internal.Constant.isJapaneseScript
import com.github.pemistahl.lingua.internal.Constant.languagesWithCharsIndexer
import com.github.pemistahl.lingua.internal.EnumDoubleMap
import com.github.pemistahl.lingua.internal.EnumIntMap
import com.github.pemistahl.lingua.internal.KeyIndexer
import com.github.pemistahl.lingua.internal.PrimitiveNgram
import com.github.pemistahl.lingua.internal.QuadriFivegramRelativeFrequencyLookup
import com.github.pemistahl.lingua.internal.TestDataLanguageModel
import com.github.pemistahl.lingua.internal.UniBiTrigramRelativeFrequencyLookup
import com.github.pemistahl.lingua.internal.loadLetterIndexMap
import com.github.pemistahl.lingua.internal.util.extension.containsAnyOf
import com.github.pemistahl.lingua.internal.util.extension.isLogogram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.function.LongConsumer
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
    internal val numberOfLoadedLanguages: Int = languages.size
) {
    private val languagesWithUniqueCharacters = languages.filterNot { it.uniqueCharacters.isNullOrBlank() }.asSequence()
    private val alphabetsSupportingExactlyOneLanguage = EnumMap(
        Language.scriptsSupportingExactlyOneLanguage.filterValues {
        it in languages
    })
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
        if (confidenceValues.size == 1) return confidenceValues.firstKey()

        val mostLikelyLanguage = confidenceValues.firstKey()
        val mostLikelyLanguageProbability = confidenceValues.getValue(mostLikelyLanguage)

        val secondMostLikelyLanguage = confidenceValues.filterNot {
            it.key == mostLikelyLanguage
        }.maxByOrNull { it.value }!!.key
        val secondMostLikelyLanguageProbability = confidenceValues.getValue(secondMostLikelyLanguage)

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
    fun computeLanguageConfidenceValues(text: String): SortedMap<Language, Double> {
        val cleanedUpText = cleanUpInputText(text)

        if (cleanedUpText.isEmpty() || !cleanedUpText.codePoints().anyMatch(Character::isLetter)) {
            return sortedMapOf()
        }

        val words = splitTextIntoWords(cleanedUpText)
        val languageDetectedByRules = detectLanguageWithRules(words)

        if (languageDetectedByRules != UNKNOWN) {
            return sortedMapOf(languageDetectedByRules to 1.0)
        }

        val filteredLanguages = filterLanguagesByRules(words)

        if (filteredLanguages.size == 1) {
            val filteredLanguage = filteredLanguages.iterator().next()
            return sortedMapOf(filteredLanguage to 1.0)
        }

        val ngramSizeRange = if (cleanedUpText.length >= 120) (3..3) else (1..5)
        val allProbabilitiesAndUnigramCounts = runBlocking {
            ngramSizeRange.filter { i -> cleanedUpText.length >= i }.map { i ->
                async(Dispatchers.Default) {
                    val testDataModel = TestDataLanguageModel.fromText(cleanedUpText, ngramLength = i)
                    val probabilities = computeLanguageProbabilities(testDataModel, filteredLanguages)

                    val unigramCounts = if (i == 1) {
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

                    Pair(probabilities, unigramCounts)
                }
            }.awaitAll()
        }

        val allProbabilities = allProbabilitiesAndUnigramCounts.map { (probabilities, _) -> probabilities }
        val unigramCounts = allProbabilitiesAndUnigramCounts[0].second ?: EnumIntMap.newMap(languagesSubsetIndexer)
        val summedUpProbabilities = sumUpProbabilities(allProbabilities, unigramCounts, filteredLanguages)
        val highestProbability = summedUpProbabilities.maxValueOrNull() ?: return sortedMapOf()
        val confidenceValues = summedUpProbabilities.mapNonZeroValues { highestProbability / it }
        return confidenceValues.sortedByNonZeroDescendingValue()
    }

    internal fun cleanUpInputText(text: String): String {
        return text.trim().lowercase()
            .replace(PUNCTUATION, "")
            .replace(NUMBERS, "")
            .replace(MULTIPLE_WHITESPACE, " ")
    }

    /** Splits text at spaces and between logograms */
    internal fun splitTextIntoWords(text: String): List<String> {
        val words = mutableListOf<String>()
        var nextWordStart = 0
        for (i in text.indices) {
            val char = text[i]

            if (char == ' ') {
                // If equal, skip consecutive whitespaces
                if (nextWordStart != i) {
                    words.add(text.substring(nextWordStart, i))
                }
                nextWordStart = i + 1
            } else if (char.isLogogram()) {
                words.add(text.substring(nextWordStart, i + 1))
                nextWordStart = i + 1
            }
        }

        if (nextWordStart != text.length) {
            words.add(text.substring(nextWordStart, text.length))
        }
        return words
    }

    internal fun countUnigramsOfInputText(
        unigramLanguageModel: TestDataLanguageModel,
        filteredLanguages: Set<Language>
    ): EnumIntMap<Language> {
        val unigramCounts = EnumIntMap.newMap(languagesSubsetIndexer)
        for (language in filteredLanguages) {
            val lookup = languageModels[language]!!.value.uniBiTrigramsLookup

            // Only have to check primitiveNgrams since unigrams are always encoded as primitive
            unigramLanguageModel.primitiveNgrams.forEach(LongConsumer {
                val probability = lookup.getFrequency(PrimitiveNgram(it))
                if (probability > 0) {
                    unigramCounts.increment(language)
                }
            })
        }
        return unigramCounts
    }

    internal fun sumUpProbabilities(
        probabilities: List<EnumDoubleMap<Language>>,
        unigramCountsOfInputText: EnumIntMap<Language>,
        filteredLanguages: Set<Language>
    ): EnumDoubleMap<Language> {
        val summedUpProbabilities = EnumDoubleMap.newMap(languagesSubsetIndexer)
        for (language in filteredLanguages) {
            summedUpProbabilities.set(language, probabilities.sumOf { it.getOrZero(language) })

            unigramCountsOfInputText.ifNonZero(language) { unigramCount ->
                summedUpProbabilities.set(language, summedUpProbabilities.getOrZero(language) / unigramCount)
            }
        }
        return summedUpProbabilities
    }

    internal fun detectLanguageWithRules(words: List<String>): Language {
        // Using Double because logograms are not counted as full word
        var adjustedWordCount = 0.0
        val totalLanguageCounts = EnumDoubleMap.newMap(wordLanguagesSubsetIndexer)

        for (word in words) {
            val wordLanguageCounts = EnumIntMap.newMap(wordLanguagesSubsetIndexer)

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
                            script == Character.UnicodeScript.DEVANAGARI ->
                            languagesWithUniqueCharacters.filter {
                                it.uniqueCharacters?.contains(character) ?: false
                            }.forEach {
                                wordLanguageCounts.increment(it)
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

    internal fun filterLanguagesByRules(words: List<String>): Set<Language> {
        // Using Double because logograms are not counted as full word
        var adjustedWordCount = 0.0
        val detectedAlphabets = EnumDoubleMap.newMap(Language.allScriptsIndexer)

        for (word in words) {
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
            it.unicodeScripts.any { script -> mostFrequentAlphabets.contains(script) }
        }
        val languageCounts = EnumIntMap.newMap(languagesWithCharsIndexer)

        for (word in words) {
            for ((characters, languages) in CHARS_TO_LANGUAGES_MAPPING) {
                if (word.containsAnyOf(characters)) {
                    for (language in languages) {
                        languageCounts.increment(language)
                    }
                    break
                }
            }
        }

        val languagesSubset = languageCounts.keysWithValueLargerEqualThan(adjustedWordCount / 2.0)

        return if (languagesSubset.isNotEmpty()) {
            filteredLanguages.filter { it in languagesSubset }.toSet()
        } else {
            filteredLanguages.toSet()
        }
    }

    internal fun computeLanguageProbabilities(
        testDataModel: TestDataLanguageModel,
        filteredLanguages: Set<Language>
    ): EnumDoubleMap<Language> {
        val probabilities = EnumDoubleMap.newMap(languagesSubsetIndexer)
        for (language in filteredLanguages) {
            val modelHolder = languageModels[language]!!.value
            val uniBiTrigramsLookup = modelHolder.uniBiTrigramsLookup
            val quadriFivegramLookup = when {
                // When model only contains primitives don't have to load quadriFivegramLookup
                testDataModel.hasOnlyPrimitives() -> QuadriFivegramRelativeFrequencyLookup.empty
                else -> modelHolder.quadriFivegramLookup.value
            }

            var probability = computeSumOfNgramProbabilities(
                uniBiTrigramsLookup,
                quadriFivegramLookup,
                testDataModel
            )
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
        return probabilities
    }

    internal fun computeSumOfNgramProbabilities(
        uniBiTrigramsLookup: UniBiTrigramRelativeFrequencyLookup,
        quadriFivegramLookup: QuadriFivegramRelativeFrequencyLookup,
        testDataModel: TestDataLanguageModel
    ): Double {
        var probabilitySum = 0.0

        ngramLoop@ for (ngram in testDataModel.objectNgrams) {
            var current = ngram
            var currentPrimitive: PrimitiveNgram
            while (true) {
                val probability = quadriFivegramLookup.getFrequency(current)
                if (probability > 0) {
                    probabilitySum += ln(probability)
                    continue@ngramLoop
                }

                val newCurrent = current.getLowerOrderNgram()
                if (newCurrent == null) {
                    currentPrimitive = current.getLowerOrderPrimitiveNgram()
                    break
                } else {
                    current = newCurrent
                }
            }

            do {
                val probability = uniBiTrigramsLookup.getFrequency(currentPrimitive)
                if (probability > 0) {
                    probabilitySum += ln(probability)
                    break
                }

                currentPrimitive = currentPrimitive.getLowerOrderNgram()
            } while (currentPrimitive.value != PrimitiveNgram.NONE.value)
        }

        // Must explicitly specify LongConsumer type, otherwise Kotlin picks the wrong overload
        testDataModel.primitiveNgrams.forEach(LongConsumer {
            var current = PrimitiveNgram(it)
            do {
                val probability = uniBiTrigramsLookup.getFrequency(current)
                if (probability > 0) {
                    probabilitySum += ln(probability)
                    break
                }

                current = current.getLowerOrderNgram()
            } while (current.value != PrimitiveNgram.NONE.value)
        })

        return probabilitySum
    }

    private fun preloadLanguageModels() {
        runBlocking {
            languages.map {
                async {
                    // Initialize values of Lazy objects
                    val modelHolder = languageModels[it]!!.value
                    modelHolder.quadriFivegramLookup.value
                }
            }.awaitAll()
        }
    }

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LanguageDetector -> false
        languages != other.languages -> false
        minimumRelativeDistance != other.minimumRelativeDistance -> false
        else -> true
    }

    override fun hashCode() = 31 * languages.hashCode() + minimumRelativeDistance.hashCode()

    internal data class LanguageModelHolder(
        val uniBiTrigramsLookup: UniBiTrigramRelativeFrequencyLookup,
        // Lookup for quadrigrams and fivegrams is lazy since it won't be used when
        // large texts are analyzed
        val quadriFivegramLookup: Lazy<QuadriFivegramRelativeFrequencyLookup>
    )

    internal companion object {
        internal var letterIndexMap = lazy { loadLetterIndexMap() }
        internal var languageModels: Map<Language, Lazy<LanguageModelHolder>> = EnumMap(Language.all().asSequence()
            .associateWith {
                lazy {
                    LanguageModelHolder(
                        runBlocking(Dispatchers.IO) {
                            UniBiTrigramRelativeFrequencyLookup.fromBinary(it, letterIndexMap.value)
                        },
                        lazy { runBlocking(Dispatchers.IO) {
                            QuadriFivegramRelativeFrequencyLookup.fromBinary(it, letterIndexMap.value)
                        }}
                    )
                }
            })
    }
}
