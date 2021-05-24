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
import com.github.pemistahl.lingua.internal.Alphabet
import com.github.pemistahl.lingua.internal.Constant.CHARS_TO_LANGUAGES_MAPPING
import com.github.pemistahl.lingua.internal.Constant.MULTIPLE_WHITESPACE
import com.github.pemistahl.lingua.internal.Constant.NUMBERS
import com.github.pemistahl.lingua.internal.Constant.PUNCTUATION
import com.github.pemistahl.lingua.internal.Constant.isJapaneseCharacter
import com.github.pemistahl.lingua.internal.PrimitiveNgram
import com.github.pemistahl.lingua.internal.QuadriFivegramRelativeFrequencyLookup
import com.github.pemistahl.lingua.internal.TestDataLanguageModel
import com.github.pemistahl.lingua.internal.UniBiTrigramRelativeFrequencyLookup
import com.github.pemistahl.lingua.internal.util.extension.containsAnyOf
import com.github.pemistahl.lingua.internal.util.extension.incrementCounter
import com.github.pemistahl.lingua.internal.util.extension.isLogogram
import it.unimi.dsi.fastutil.objects.Object2DoubleMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.function.LongConsumer
import java.util.stream.Collectors
import kotlin.math.ln

/**
 * Detects the language of given input text.
 */
class LanguageDetector internal constructor(
    internal val languages: MutableSet<Language>,
    internal val minimumRelativeDistance: Double,
    isEveryLanguageModelPreloaded: Boolean,
    internal val numberOfLoadedLanguages: Int = languages.size
) {
    private val languagesWithUniqueCharacters = languages.filterNot { it.uniqueCharacters.isNullOrBlank() }.asSequence()
    private val alphabetsSupportingExactlyOneLanguage = Alphabet.allSupportingExactlyOneLanguage().filterValues {
        it in languages
    }

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
        val values = TreeMap<Language, Double>()
        val cleanedUpText = cleanUpInputText(text)

        if (cleanedUpText.isEmpty() || !cleanedUpText.codePoints().anyMatch(Character::isLetter)) return values

        val words = splitTextIntoWords(cleanedUpText)
        val languageDetectedByRules = detectLanguageWithRules(words)

        if (languageDetectedByRules != UNKNOWN) {
            values[languageDetectedByRules] = 1.0
            return values
        }

        var filteredLanguages = filterLanguagesByRules(words)

        if (filteredLanguages.size == 1) {
            val filteredLanguage = filteredLanguages.iterator().next()
            values[filteredLanguage] = 1.0
            return values
        }

        val ngramSizeRange = if (cleanedUpText.length >= 120) (3..3) else (1..5)
        val allProbabilitiesAndUnigramCounts = runBlocking {
            ngramSizeRange.filter { i -> cleanedUpText.length >= i }.map { i ->
                async(Dispatchers.Default) {
                    val testDataModel = TestDataLanguageModel.fromText(cleanedUpText, ngramLength = i)
                    val probabilities = computeLanguageProbabilities(testDataModel, filteredLanguages)
                    val languages = probabilities.keys

                    if (languages.isNotEmpty()) {
                        filteredLanguages = filteredLanguages.asSequence().filter { languages.contains(it) }.toSet()
                    }

                    val unigramCounts = if (i == 1) {
                        countUnigramsOfInputText(testDataModel, filteredLanguages)
                    } else {
                        null
                    }

                    Pair(probabilities, unigramCounts)
                }
            }.awaitAll()
        }

        val allProbabilities = allProbabilitiesAndUnigramCounts.map { (probabilities, _) -> probabilities }
        val unigramCounts = allProbabilitiesAndUnigramCounts[0].second ?: Object2IntMaps.emptyMap()
        val summedUpProbabilities = sumUpProbabilities(allProbabilities, unigramCounts, filteredLanguages)
        val highestProbability = summedUpProbabilities.maxByOrNull { it.value }?.value ?: return sortedMapOf()
        val confidenceValues = summedUpProbabilities.mapValues { highestProbability / it.value }
        val sortedByConfidenceValue = compareByDescending<Language> { language -> confidenceValues[language] }
        val sortedByConfidenceValueThenByLanguage = sortedByConfidenceValue.thenBy { language -> language }

        return confidenceValues.toSortedMap(sortedByConfidenceValueThenByLanguage)
    }

    internal fun cleanUpInputText(text: String): String {
        return text.trim().toLowerCase(Locale.ROOT)
            .replace(PUNCTUATION, "")
            .replace(NUMBERS, "")
            .replace(MULTIPLE_WHITESPACE, " ")
    }

    internal fun splitTextIntoWords(text: String): List<String> {
        val normalizedTextBuilder = StringBuilder()
        for (chr in text) {
            normalizedTextBuilder.append(chr)
            if (chr.isLogogram()) {
                normalizedTextBuilder.append(' ')
            }
        }
        val normalizedText = normalizedTextBuilder.toString()
        return if (normalizedText.contains(' ')) {
            normalizedText.split(' ').filter { it.isNotBlank() }
        } else {
            listOf(normalizedText)
        }
    }

    internal fun countUnigramsOfInputText(
        unigramLanguageModel: TestDataLanguageModel,
        filteredLanguages: Set<Language>
    ): Object2IntMap<Language> {
        val unigramCounts = Object2IntOpenHashMap<Language>()
        for (language in filteredLanguages) {
            val lookup = languageModels[language]!!.value.uniBiTrigramsLookup

            // Only have to check primitiveNgrams since unigrams are always encoded as primitive
            unigramLanguageModel.primitiveNgrams.forEach(LongConsumer {
                val probability = lookup.getFrequency(PrimitiveNgram(it))
                if (probability > 0) {
                    unigramCounts.incrementCounter(language)
                }
            })
        }
        return unigramCounts
    }

    internal fun sumUpProbabilities(
        probabilities: List<Object2DoubleMap<Language>>,
        unigramCountsOfInputText: Object2IntMap<Language>,
        filteredLanguages: Set<Language>
    ): Map<Language, Double> {
        val summedUpProbabilities = hashMapOf<Language, Double>()
        for (language in filteredLanguages) {
            summedUpProbabilities[language] = probabilities.sumOf { it.getOrDefault(language as Any, 0.0) }

            if (unigramCountsOfInputText.containsKey(language)) {
                summedUpProbabilities[language] = summedUpProbabilities.getValue(language) /
                    unigramCountsOfInputText.getInt(language)
            }
        }
        return summedUpProbabilities.filter { it.value != 0.0 }
    }

    internal fun detectLanguageWithRules(words: List<String>): Language {
        val totalLanguageCounts = Object2IntOpenHashMap<Language>()

        for (word in words) {
            val wordLanguageCounts = Object2IntOpenHashMap<Language>()

            for (character in word) {
                var isMatch = false
                for ((alphabet, language) in alphabetsSupportingExactlyOneLanguage) {
                    if (alphabet.matches(character)) {
                        wordLanguageCounts.incrementCounter(language)
                        isMatch = true
                    }
                }
                if (!isMatch) {
                    when {
                        Alphabet.HAN.matches(character) -> wordLanguageCounts.incrementCounter(CHINESE)
                        isJapaneseCharacter(character) -> wordLanguageCounts.incrementCounter(JAPANESE)
                        Alphabet.LATIN.matches(character) ||
                            Alphabet.CYRILLIC.matches(character) ||
                            Alphabet.DEVANAGARI.matches(character) ->
                            languagesWithUniqueCharacters.filter {
                                it.uniqueCharacters?.contains(character) ?: false
                            }.forEach {
                                wordLanguageCounts.incrementCounter(it)
                            }
                    }
                }
            }

            if (wordLanguageCounts.isEmpty()) {
                totalLanguageCounts.incrementCounter(UNKNOWN)
            } else if (wordLanguageCounts.size == 1) {
                val language = wordLanguageCounts.toList().first().first
                if (language in languages) {
                    totalLanguageCounts.incrementCounter(language)
                } else {
                    totalLanguageCounts.incrementCounter(UNKNOWN)
                }
            } else if (wordLanguageCounts.containsKey(CHINESE) && wordLanguageCounts.containsKey(JAPANESE)) {
                totalLanguageCounts.incrementCounter(JAPANESE)
            } else {
                val sortedWordLanguageCounts = wordLanguageCounts.toList().sortedByDescending { it.second }
                val (mostFrequentLanguage, firstCharCount) = sortedWordLanguageCounts[0]
                val (_, secondCharCount) = sortedWordLanguageCounts[1]

                if (firstCharCount > secondCharCount && mostFrequentLanguage in languages) {
                    totalLanguageCounts.incrementCounter(mostFrequentLanguage)
                } else {
                    totalLanguageCounts.incrementCounter(UNKNOWN)
                }
            }
        }

        val unknownLanguageCount = totalLanguageCounts.getOrDefault(UNKNOWN as Any, 0)
        val filteredLanguageCounts = if (unknownLanguageCount >= (0.5 * words.size)) {
            totalLanguageCounts
        } else {
            totalLanguageCounts.filterNot { it.key == UNKNOWN }
        }

        if (filteredLanguageCounts.isEmpty()) {
            return UNKNOWN
        }
        if (filteredLanguageCounts.size == 1) {
            return filteredLanguageCounts.toList().first().first
        }
        if (filteredLanguageCounts.size == 2 &&
            filteredLanguageCounts.containsKey(CHINESE) &&
            filteredLanguageCounts.containsKey(JAPANESE)
        ) {
            return JAPANESE
        }
        val sortedTotalLanguageCounts = filteredLanguageCounts.toList().sortedByDescending { it.second }
        val (mostFrequentLanguage, firstCharCount) = sortedTotalLanguageCounts[0]
        val (_, secondCharCount) = sortedTotalLanguageCounts[1]

        return when {
            firstCharCount == secondCharCount -> UNKNOWN
            else -> mostFrequentLanguage
        }
    }

    internal fun filterLanguagesByRules(words: List<String>): Set<Language> {
        val detectedAlphabets = Object2IntOpenHashMap<Alphabet>()

        for (word in words) {
            for (alphabet in Alphabet.values()) {
                if (alphabet.matches(word)) {
                    detectedAlphabets.incrementCounter(alphabet)
                    break
                }
            }
        }

        if (detectedAlphabets.isEmpty()) {
            return languages
        }

        // Note: Using wrapping call maxByOrNull is fine since number of alphabets is small
        val mostFrequentAlphabet = detectedAlphabets.maxByOrNull { it.value }!!.key
        val filteredLanguages = languages.filter { it.alphabets.contains(mostFrequentAlphabet) }
        val languageCounts = Object2IntOpenHashMap<Language>()

        for (word in words) {
            for ((characters, languages) in CHARS_TO_LANGUAGES_MAPPING) {
                if (word.containsAnyOf(characters)) {
                    for (language in languages) {
                        languageCounts.incrementCounter(language)
                    }
                    break
                }
            }
        }

        val languagesSubset = languageCounts.filterValues { it >= words.size / 2.0 }.keys

        return if (languagesSubset.isNotEmpty()) {
            filteredLanguages.filter { it in languagesSubset }.toSet()
        } else {
            filteredLanguages.toSet()
        }
    }

    internal fun computeLanguageProbabilities(
        testDataModel: TestDataLanguageModel,
        filteredLanguages: Set<Language>
    ): Object2DoubleMap<Language> {
        val probabilities = Object2DoubleOpenHashMap<Language>()
        for (language in filteredLanguages) {
            val modelHolder = languageModels[language]!!.value
            val uniBiTrigramsLookup = modelHolder.uniBiTrigramsLookup
            val quadriFivegramLookup = when {
                // When model only contains primitives don't have to load quadriFivegramLookup
                testDataModel.hasOnlyPrimitives() -> QuadriFivegramRelativeFrequencyLookup.empty
                else -> modelHolder.quadriFivegramLookup.value
            }

            val probability = computeSumOfNgramProbabilities(
                uniBiTrigramsLookup,
                quadriFivegramLookup,
                testDataModel
            )
            if (probability < 0.0) {
                // Note: Don't convert to assignment, would choose wrong overload then (?)
                probabilities.put(language, probability)
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
            languageModels.map {
                async {
                    // Initialize values of Lazy objects
                    val modelHolder = it.value.value
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
        internal var languageModels = Language.all().stream()
            .collect(Collectors.toMap(
                { language -> language },
                { language -> lazy {
                    LanguageModelHolder(
                        runBlocking(Dispatchers.IO) {
                            UniBiTrigramRelativeFrequencyLookup.fromBinary(language)
                        },
                        lazy { runBlocking(Dispatchers.IO) {
                            QuadriFivegramRelativeFrequencyLookup.fromBinary(language)
                        }}
                    )
                }}
            ))
    }
}
