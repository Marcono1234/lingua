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

package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.internal.util.extension.incrementCounter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import it.unimi.dsi.fastutil.bytes.Byte2FloatAVLTreeMap
import it.unimi.dsi.fastutil.bytes.Byte2FloatArrayMap
import it.unimi.dsi.fastutil.bytes.Byte2FloatMap
import it.unimi.dsi.fastutil.bytes.Byte2FloatOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import okio.buffer
import okio.source
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.util.*


internal data class JsonLanguageModel(val language: Language, val ngrams: Map<Fraction, String>)

internal data class TrainingDataLanguageModel(
    val language: Language,
    val absoluteFrequencies: Map<Ngram, Int>,
    val relativeFrequencies: Map<Ngram, Fraction>,
    val jsonRelativeFrequencies: Object2DoubleMap<Ngram>
) {
    fun getRelativeFrequency(ngram: Ngram): Double = jsonRelativeFrequencies.getDouble(ngram)

    fun toJson(): String {
        val ngrams = mutableMapOf<Fraction, MutableList<Ngram>>()

        for ((ngram, fraction) in relativeFrequencies) {
            ngrams.computeIfAbsent(fraction) { mutableListOf() }.add(ngram)
        }

        val jsonLanguageModel = JsonLanguageModel(language, ngrams.mapValues { it.value.joinToString(separator = " ") })

        TODO()
    }

    class RelativeFrequencyLookup {
        val loadFactory = 0.9f
        val unigrams = Int2FloatOpenHashMap(10, loadFactory)


        fun finishCreation() {
            unigrams.trim()
        }
    }

    companion object {
        private val jsonModelNameOptions = JsonReader.Options.of("language", "ngrams")

        fun fromText(
            text: Sequence<String>,
            language: Language,
            ngramLength: Int,
            charClass: String,
            lowerNgramAbsoluteFrequencies: Map<Ngram, Int>
        ): TrainingDataLanguageModel {

            require(ngramLength in 1..5) {
                "ngram length $ngramLength is not in range 1..5"
            }

            val absoluteFrequencies = computeAbsoluteFrequencies(
                text,
                ngramLength,
                charClass
            )

            val relativeFrequencies = computeRelativeFrequencies(
                ngramLength,
                absoluteFrequencies,
                lowerNgramAbsoluteFrequencies
            )

            return TrainingDataLanguageModel(
                language,
                absoluteFrequencies,
                relativeFrequencies,
                Object2DoubleOpenHashMap()
            )
        }

        fun fromJson(json: InputStream): TrainingDataLanguageModel {
            val jsonReader = JsonReader.of(json.source().buffer())
            jsonReader.beginObject()

            var language: Language? = null
            var jsonRelativeFrequencies: Object2DoubleOpenHashMap<Ngram>? = null

            while (jsonReader.hasNext()) {
                when (jsonReader.selectName(jsonModelNameOptions)) {
                    -1 -> throw IllegalArgumentException("Unknown name '${jsonReader.nextName()}' at ${jsonReader.path}")
                    0 -> if (language == null) {
                        language = Language.valueOf(jsonReader.nextString())
                    } else throw IllegalArgumentException("Duplicate language at ${jsonReader.path}")
                    1 -> if (jsonRelativeFrequencies == null) {
                        jsonRelativeFrequencies = Object2DoubleOpenHashMap(1000, 0.99f)
                        jsonReader.beginObject()
                        while (jsonReader.hasNext()) {
                            val (numerator, denominator) = jsonReader.nextName().split('/').map(String::toInt)
                            val frequency = numerator / denominator.toDouble()
                            jsonReader.nextString().split(' ')
                                .map(::Ngram)
                                .forEach { jsonRelativeFrequencies[it] = frequency }
                        }
                        jsonReader.endObject()
                    } else throw IllegalArgumentException("Duplicate ngrams at ${jsonReader.path}")
                }
            }
            jsonReader.endObject()

            jsonRelativeFrequencies!!.trim()
            println(jsonRelativeFrequencies!!.keys.map(Ngram::value).filter{it.codePoints().allMatch { it < 4096 }}.groupBy({it.length}).forEach({println("${it.key}: " + it.value.size)}))
            //println(jsonRelativeFrequencies!!.keys.stream().map(Ngram::value).flatMapToInt(String::codePoints).filter{it < 7000 && it !in intArrayOf(65357, 64258, 64257, 64257, 64256) }.max()!!)
            //println(jsonRelativeFrequencies!!.keys.stream().map(Ngram::value).filter{it.codePoints().anyMatch{it > 60000}}.forEach(::println))

            return TrainingDataLanguageModel(
                language = language ?: throw IllegalArgumentException("Language is missing"),
                absoluteFrequencies = emptyMap(),
                relativeFrequencies = emptyMap(),
                jsonRelativeFrequencies = jsonRelativeFrequencies ?: throw IllegalArgumentException("Ngrams are missing")
            )
        }

        private fun computeAbsoluteFrequencies(
            text: Sequence<String>,
            ngramLength: Int,
            charClass: String
        ): Map<Ngram, Int> {

            val absoluteFrequencies = hashMapOf<Ngram, Int>()
            val regex = Regex("[$charClass]+")

            for (line in text) {
                val lowerCasedLine = line.toLowerCase(Locale.ROOT)
                for (i in 0..lowerCasedLine.length - ngramLength) {
                    val textSlice = lowerCasedLine.slice(i until i + ngramLength)
                    if (regex.matches(textSlice)) {
                        val ngram = Ngram(textSlice)
                        absoluteFrequencies.incrementCounter(ngram)
                    }
                }
            }

            return absoluteFrequencies
        }

        private fun computeRelativeFrequencies(
            ngramLength: Int,
            absoluteFrequencies: Map<Ngram, Int>,
            lowerNgramAbsoluteFrequencies: Map<Ngram, Int>
        ): Map<Ngram, Fraction> {

            val ngramProbabilities = hashMapOf<Ngram, Fraction>()
            val totalNgramFrequency = absoluteFrequencies.values.sum()

            for ((ngram, frequency) in absoluteFrequencies) {
                val denominator = if (ngramLength == 1 || lowerNgramAbsoluteFrequencies.isEmpty()) {
                    totalNgramFrequency
                } else {
                    lowerNgramAbsoluteFrequencies.getValue(Ngram(ngram.value.slice(0..ngramLength - 2)))
                }
                ngramProbabilities[ngram] = Fraction(frequency, denominator)
            }

            return ngramProbabilities
        }
    }
}
