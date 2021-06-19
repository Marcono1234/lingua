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

/**
 * Ngram encoded as primitive [Long]. Ngrams which cannot be encoded as
 * primitive are represented as [ObjectNgram].
 *
 * This class is an _inline_ class, care must be taken to not accidentally
 * use it in contexts where an [Any] is used, otherwise the primitive
 * value would be wrapped in an object.
 */
@JvmInline
internal value class PrimitiveNgram(val value: Long) {
    private fun getLength(): Int {
        return (value and 0xFF).toInt()
    }

    operator fun component1() = getLength()
    operator fun component2() = (value shr 8).toInt() and 0xFFFF
    operator fun component3() = (value shr 24).toInt() and 0xFFFF
    operator fun component4() = (value shr 40).toInt() and 0xFFFF

    /**
     * Returns the next lower order ngram or [PrimitiveNgram.NONE] if there is no
     * lower order ngram.
     */
    fun getLowerOrderNgram(): PrimitiveNgram {
        return when(getLength()) {
            1 -> NONE
            // Overwrite length and copy over only chars
            2 -> PrimitiveNgram(1L or (value and 0xFFFF_00))
            3 -> PrimitiveNgram(2L or (value and 0xFFFF_FFFF_00))
            else -> throw IllegalStateException("No lower order ngram exists")
        }
    }

    companion object {
        /** Maximum ngram length supported by [PrimitiveNgram] */
        const val MAX_NGRAM_LENGTH = 3
        val NONE = PrimitiveNgram(0)

        fun of(string: String, startIndex: Int, length: Int): PrimitiveNgram {
            return when (length) {
                1 -> PrimitiveNgram(1L
                    or (string[startIndex + 0].code.toLong() shl 8)
                )
                2 -> PrimitiveNgram(2L
                    or (string[startIndex + 0].code.toLong() shl 8)
                    or (string[startIndex + 1].code.toLong() shl 24)
                )
                3 -> PrimitiveNgram(3L
                    or (string[startIndex + 0].code.toLong() shl 8)
                    or (string[startIndex + 1].code.toLong() shl 24)
                    or (string[startIndex + 2].code.toLong() shl 40)
                )
                // For now don't support larger ngrams, otherwise would complicate
                // encoding since there would not be 16bits per char
                else -> NONE
            }
        }
    }
}

/**
 * Ngram encoded as [String]. Only used for ngrams which cannot be represented
 * as [PrimitiveNgram].
 *
 * This class is an _inline_ class, care must be taken to not accidentally
 * use it in contexts where an [Any] is used, otherwise the `String`
 * value would be wrapped in an `ObjectNgram` instance.
 */
@JvmInline
internal value class ObjectNgram(val value: String) {
    init {
        require(value.length in (PrimitiveNgram.MAX_NGRAM_LENGTH + 1)..5) {
            "Unsupported ngram length"
        }
    }

    override fun toString() = value

    /**
     * Returns the next lower order ngram or `null` if the next lower
     * order ngrams are encoded as primitive and can be obtained from
     * [getLowerOrderPrimitiveNgram].
     */
    fun getLowerOrderNgram(): ObjectNgram? {
        // Switch to PrimitiveNgram if possible
        return if (value.length <= PrimitiveNgram.MAX_NGRAM_LENGTH + 1) null
        else ObjectNgram(value.substring(0, value.length - 1))
    }

    /**
     * Returns the next lower order ngram.
     *
     * Must only be called if [getLowerOrderNgram] returned `null`.
     */
    fun getLowerOrderPrimitiveNgram(): PrimitiveNgram {
        return PrimitiveNgram.of(value, 0, value.length - 1)
    }
}
