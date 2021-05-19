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
import it.unimi.dsi.fastutil.bytes.Byte2FloatOpenHashMap
import it.unimi.dsi.fastutil.chars.Char2FloatOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2FloatOpenHashMap
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.*
import kotlin.IllegalArgumentException


internal data class JsonLanguageModel(val language: Language, val ngrams: Map<Fraction, String>)

internal typealias ModelEncodingType = Int
internal const val UNIGRAM_AS_BYTE = 0
internal const val UNIGRAM_AS_CHAR = 1
internal const val BIGRAM_AS_SHORT = 2
internal const val BIGRAM_AS_INT = 3
internal const val TRIGRAM_AS_INT = 4
internal const val TRIGRAM_AS_LONG = 5

internal const val TRIGRAM_AS_INT_BITS_PER_CHAR = Int.SIZE_BITS / 3
/**
 * Maximum code point value (inclusive) a char of a trigram may have to
 * allow encoding the trigram as int.
 */
private const val TRIGRAM_AS_INT_MAX_CHAR = (2 shl (TRIGRAM_AS_INT_BITS_PER_CHAR - 1)) - 1
private const val TRIGRAM_AS_LONG_BITS_PER_CHAR = Long.SIZE_BITS / 3

private const val FIVEGRAM_AS_LONG_BITS_PER_CHAR = Long.SIZE_BITS / 5
/**
 * Maximum code point value (inclusive) a char of a fivegram may have to
 * allow encoding the fivegram as long.
 */
private const val FIVEGRAM_AS_LONG_MAX_CHAR = (2 shl (FIVEGRAM_AS_LONG_BITS_PER_CHAR - 1)) - 1

internal data class TrainingDataLanguageModel(
    val language: Language,
    val absoluteFrequencies: Map<Ngram, Int>,
    val relativeFrequencies: Map<Ngram, Fraction>,
    val jsonRelativeFrequencies: RelativeFrequencyLookup
) {
    fun getRelativeFrequency(ngram: Ngram): Double = jsonRelativeFrequencies.getFrequency(ngram).toDouble()
    fun getRelativeFrequency(ngram: PrimitiveNgram): Double = jsonRelativeFrequencies.getFrequency(ngram).toDouble()

    fun toJson(): String {
        val ngrams = mutableMapOf<Fraction, MutableList<Ngram>>()

        for ((ngram, fraction) in relativeFrequencies) {
            ngrams.computeIfAbsent(fraction) { mutableListOf() }.add(ngram)
        }

        val jsonLanguageModel = JsonLanguageModel(language, ngrams.mapValues { it.value.joinToString(separator = " ") })

        TODO()
    }

    internal class RelativeFrequencyLookup {
        private val loadFactor = 0.9f
        private val initialCapacity = 16

        private val unigramsAsByte = Byte2FloatOpenHashMap(initialCapacity, loadFactor)
        private val unigramsAsChar = Char2FloatOpenHashMap(initialCapacity, loadFactor)

        private val bigramsAsShort = Short2FloatOpenHashMap(initialCapacity, loadFactor)
        private val bigramsAsInt = Int2FloatOpenHashMap(initialCapacity, loadFactor)

        private val trigramsAsInt = Int2FloatOpenHashMap(initialCapacity, loadFactor)
        private val trigramsAsLong = Long2FloatOpenHashMap(initialCapacity, loadFactor)

        private val quadrigramsAsInt = Int2FloatOpenHashMap(initialCapacity, loadFactor)
        private val quadrigramsAsLong = Long2FloatOpenHashMap(initialCapacity, loadFactor)

        private val fivegramsAsLong = Long2FloatOpenHashMap(initialCapacity, loadFactor)
        private val fivegramsAsObject = Object2FloatOpenHashMap<String>(initialCapacity, loadFactor)

        private fun String.bigramFitsShort(): Boolean {
            return this[0].code <= 255 && this[1].code <= 255
        }

        private fun String.bigramToShort(): Short {
            return (
                (this[1].code shl 8)
                or this[0].code
            ).toShort()
        }

        private fun String.bigramToInt(): Int {
            return (
                (this[1].code shl 16)
                or this[0].code
            )
        }

        private fun String.trigramFitsInt(): Boolean {
            return this[0].code <= TRIGRAM_AS_INT_MAX_CHAR
                && this[1].code <= TRIGRAM_AS_INT_MAX_CHAR
                && this[2].code <= TRIGRAM_AS_INT_MAX_CHAR
        }

        private fun String.trigramToInt(): Int {
            return (
                (this[2].code shl (TRIGRAM_AS_INT_BITS_PER_CHAR * 2))
                or (this[1].code shl TRIGRAM_AS_INT_BITS_PER_CHAR)
                or this[0].code
            )
        }

        private fun String.trigramToLong(): Long {
            return (
                (this[2].code.toLong() shl (TRIGRAM_AS_LONG_BITS_PER_CHAR * 2))
                or (this[1].code.toLong() shl TRIGRAM_AS_LONG_BITS_PER_CHAR)
                or this[0].code.toLong()
            )
        }

        private fun String.quadrigramFitsInt(): Boolean {
            return this[0].code <= 255
                && this[1].code <= 255
                && this[2].code <= 255
                && this[3].code <= 255
        }

        private fun String.quadrigramToInt(): Int {
            return (
                (this[3].code shl 24)
                or (this[2].code shl 16)
                or (this[1].code shl 8)
                or this[0].code
            )
        }

        private fun String.quadrigramToLong(): Long {
            return (
                (this[1].code.toLong() shl 48)
                or (this[1].code.toLong() shl 32)
                or (this[1].code.toLong() shl 16)
                or this[0].code.toLong()
            )
        }

        private fun String.fivegramFitsLong(): Boolean {
            return this[0].code <= FIVEGRAM_AS_LONG_MAX_CHAR
                && this[1].code <= FIVEGRAM_AS_LONG_MAX_CHAR
                && this[2].code <= FIVEGRAM_AS_LONG_MAX_CHAR
                && this[3].code <= FIVEGRAM_AS_LONG_MAX_CHAR
                && this[4].code <= FIVEGRAM_AS_LONG_MAX_CHAR
        }

        private fun String.fivegramToLong(): Long {
            return (
                (this[4].code.toLong() shl (FIVEGRAM_AS_LONG_BITS_PER_CHAR * 4))
                or (this[3].code.toLong() shl (FIVEGRAM_AS_LONG_BITS_PER_CHAR * 3))
                or (this[2].code.toLong() shl (FIVEGRAM_AS_LONG_BITS_PER_CHAR * 2))
                or (this[1].code.toLong() shl FIVEGRAM_AS_LONG_BITS_PER_CHAR)
                or this[0].code.toLong()
            )
        }

        fun putFrequency(ngram: String, frequency: Float) {
            when (ngram.length) {
                1 -> {
                    val char0 = ngram[0].code
                    when {
                        char0 <= 255 -> unigramsAsByte[char0.toByte()] = frequency
                        else -> unigramsAsChar[char0.toChar()] = frequency
                    }
                }
                2 -> when {
                    ngram.bigramFitsShort() -> bigramsAsShort[ngram.bigramToShort()] = frequency
                    else -> bigramsAsInt[ngram.bigramToInt()] = frequency
                }
                3 -> when {
                    ngram.trigramFitsInt() -> trigramsAsInt[ngram.trigramToInt()] = frequency
                    else -> trigramsAsLong[ngram.trigramToLong()] = frequency
                }
                4 -> when {
                    ngram.quadrigramFitsInt() -> quadrigramsAsInt[ngram.quadrigramToInt()] = frequency
                    else -> quadrigramsAsLong[ngram.quadrigramToLong()] = frequency
                }
                5 -> when {
                    ngram.fivegramFitsLong() -> fivegramsAsLong[ngram.fivegramToLong()] = frequency
                    // Fall back to storing Ngram object
                    else -> fivegramsAsObject[ngram] = frequency
                }
                else -> throw IllegalArgumentException("Invalid Ngram length")
            }
        }

        fun finishCreation() {
            unigramsAsByte.trim()
            unigramsAsChar.trim()

            bigramsAsShort.trim()
            bigramsAsInt.trim()

            trigramsAsInt.trim()
            trigramsAsLong.trim()

            quadrigramsAsInt.trim()
            quadrigramsAsLong.trim()

            fivegramsAsLong.trim()
            fivegramsAsObject.trim()
        }

        fun getFrequency(ngram: Ngram): Float {
            val ngramStr = ngram.value

            return when (ngramStr.length) {
                1 -> {
                    val char0 = ngramStr[0].code
                    when {
                        char0 <= 255 -> unigramsAsByte[char0.toByte()]
                        else -> unigramsAsChar[char0.toChar()]
                    }
                }
                2 -> when {
                    ngramStr.bigramFitsShort() -> bigramsAsShort[ngramStr.bigramToShort()]
                    else -> bigramsAsInt[ngramStr.bigramToInt()]
                }
                3 -> when {
                    ngramStr.trigramFitsInt() -> trigramsAsInt[ngramStr.trigramToInt()]
                    else -> trigramsAsLong[ngramStr.trigramToLong()]
                }
                4 -> when {
                    ngramStr.quadrigramFitsInt() -> quadrigramsAsInt[ngramStr.quadrigramToInt()]
                    else -> quadrigramsAsLong[ngramStr.quadrigramToLong()]
                }
                5 -> when {
                    ngramStr.fivegramFitsLong() -> fivegramsAsLong[ngramStr.fivegramToLong()]
                    else -> fivegramsAsObject.getFloat(ngramStr)
                }
                else -> throw IllegalArgumentException("Invalid Ngram length")
            }
        }

        fun getFrequency(ngram: PrimitiveNgram): Float {
            return when (ngram.getEncodingType()) {
                UNIGRAM_AS_BYTE -> unigramsAsByte[ngram.unigramToByte()]
                UNIGRAM_AS_CHAR -> unigramsAsChar[ngram.unigramToChar()]
                BIGRAM_AS_SHORT -> bigramsAsShort[ngram.bigramToShort()]
                BIGRAM_AS_INT -> bigramsAsInt[ngram.bigramToInt()]
                TRIGRAM_AS_INT -> trigramsAsInt[ngram.trigramToInt()]
                TRIGRAM_AS_LONG -> trigramsAsLong[ngram.trigramToLong()]
                else -> throw AssertionError("Unknown encoding type")
            }
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
                RelativeFrequencyLookup()
            )
        }

        fun fromJson(json: InputStream): TrainingDataLanguageModel {
            val jsonReader = JsonReader.of(json.source().buffer())
            jsonReader.beginObject()

            var language: Language? = null
            var jsonRelativeFrequencies: RelativeFrequencyLookup? = null

            while (jsonReader.hasNext()) {
                when (jsonReader.selectName(jsonModelNameOptions)) {
                    -1 -> throw IllegalArgumentException("Unknown name '${jsonReader.nextName()}' at ${jsonReader.path}")
                    0 -> if (language == null) {
                        language = Language.valueOf(jsonReader.nextString())
                    } else throw IllegalArgumentException("Duplicate language at ${jsonReader.path}")
                    1 -> if (jsonRelativeFrequencies == null) {
                        jsonRelativeFrequencies = RelativeFrequencyLookup()
                        jsonReader.beginObject()
                        while (jsonReader.hasNext()) {
                            val (numerator, denominator) = jsonReader.nextName().split('/').map(String::toInt)
                            val frequency = numerator / denominator.toFloat()
                            jsonReader.nextString().split(' ')
                                .forEach { jsonRelativeFrequencies.putFrequency(it, frequency) }
                        }
                        jsonReader.endObject()
                    } else throw IllegalArgumentException("Duplicate ngrams at ${jsonReader.path}")
                }
            }
            jsonReader.endObject()

            //jsonRelativeFrequencies!!.trim()
            //println("$language: " + jsonRelativeFrequencies!!.keys.map(Ngram::value).filter{it.length == 5 && it.codePoints().allMatch { it < 4096 }}.count())
            //println("$language: " + jsonRelativeFrequencies!!.keys.map(Ngram::value).filter{it.codePoints().allMatch { it < 256 }}.groupBy { it.length }.map { "${it.key}: ${it.value.size}" }.joinToString(", "))
            //jsonRelativeFrequencies!!.keys.map(Ngram::value).filter{it.codePoints().allMatch { it < 4096 }}.groupBy({it.length}).forEach({println("${it.key}: " + it.value.size)})
            //println(jsonRelativeFrequencies!!.keys.stream().map(Ngram::value).flatMapToInt(String::codePoints).filter{it < 7000 && it !in intArrayOf(65357, 64258, 64257, 64257, 64256) }.max()!!)
            //println(jsonRelativeFrequencies!!.keys.stream().map(Ngram::value).filter{it.codePoints().anyMatch{it > 60000}}.forEach(::println))

            jsonRelativeFrequencies?.finishCreation() ?: throw IllegalArgumentException("Model data is missing ngrams")

            return TrainingDataLanguageModel(
                language = language ?: throw IllegalArgumentException("Language is missing"),
                absoluteFrequencies = emptyMap(),
                relativeFrequencies = emptyMap(),
                jsonRelativeFrequencies = jsonRelativeFrequencies
            )
        }

        private fun computeAbsoluteFrequencies(
            text: Sequence<String>,
            ngramLength: Int,
            charClass: String
        ): Object2IntMap<Ngram> {

            val absoluteFrequencies = Object2IntOpenHashMap<Ngram>()
            val regex = Regex("[$charClass]+")

            for (line in text) {
                val lowerCasedLine = line.toLowerCase(Locale.ROOT)
                for (i in 0..lowerCasedLine.length - ngramLength) {
                    val textSlice = lowerCasedLine.substring(i, i + ngramLength)
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
                    lowerNgramAbsoluteFrequencies.getValue(Ngram(ngram.value.substring(0, ngramLength - 1)))
                }
                ngramProbabilities[ngram] = Fraction(frequency, denominator)
            }

            return ngramProbabilities
        }
    }
}
