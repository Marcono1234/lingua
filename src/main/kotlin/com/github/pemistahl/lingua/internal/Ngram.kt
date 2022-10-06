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

import com.github.pemistahl.lingua.internal.util.lazyAssert

/**
 * Ngram encoded as primitive [Long]. Ngrams which cannot be encoded as
 * primitive are represented as String and can be processed as [ReusableObjectNgram].
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
    operator fun component2() = (value shr 8).toInt().toChar()
    operator fun component3() = (value shr 24).toInt().toChar()
    operator fun component4() = (value shr 40).toInt().toChar()

    /**
     * Returns the next lower order ngram or [PrimitiveNgram.NONE] if there is no
     * lower order ngram.
     */
    fun getLowerOrderNgram(): PrimitiveNgram {
        return when (getLength()) {
            1 -> PrimitiveNgram(NONE)
            // Overwrite length and copy over only chars
            2 -> PrimitiveNgram(1L or (value and 0xFFFF_00))
            3 -> PrimitiveNgram(2L or (value and 0xFFFF_FFFF_00))
            else -> throw IllegalStateException("No lower order ngram exists")
        }
    }

    companion object {
        /** Maximum ngram length supported by [PrimitiveNgram] */
        const val MAX_NGRAM_LENGTH = 3
        const val NONE = 0L

        fun of(string: CharSequence, startIndex: Int, length: Int): PrimitiveNgram {
            return when (length) {
                1 -> PrimitiveNgram(
                    1L
                        or (string[startIndex + 0].code.toLong() shl 8)
                )
                2 -> PrimitiveNgram(
                    2L
                        or (string[startIndex + 0].code.toLong() shl 8)
                        or (string[startIndex + 1].code.toLong() shl 24)
                )
                3 -> PrimitiveNgram(
                    3L
                        or (string[startIndex + 0].code.toLong() shl 8)
                        or (string[startIndex + 1].code.toLong() shl 24)
                        or (string[startIndex + 2].code.toLong() shl 40)
                )
                // For now don't support larger ngrams, otherwise would complicate
                // encoding since there would not be 16bits per char
                else -> PrimitiveNgram(NONE)
            }
        }

        fun of(char0: Char, char1: Char, char2: Char): PrimitiveNgram {
            return PrimitiveNgram(
                3L
                    or (char0.code.toLong() shl 8)
                    or (char1.code.toLong() shl 24)
                    or (char2.code.toLong() shl 40)
            )
        }
    }
}

@JvmInline
internal value class ReusableObjectNgram(
    /** First element is the length encoded as Char, other elements are the chars */
    val value: CharArray = CharArray(1 + 5)
) {
    fun setNgram(ngram: CharSequence) {
        val length = ngram.length
        assert(length > PrimitiveNgram.MAX_NGRAM_LENGTH)
        value[0] = length.toChar()
        for (i in 0 until length) {
            value[i + 1] = ngram[i]
        }
    }

    fun length() = value[0].code

    operator fun component1() = length()
    operator fun component2() = value[1]
    operator fun component3() = value[2]
    operator fun component4() = value[3]
    operator fun component5() = value[4]
    operator fun component6() = value[5]

    /**
     * Tries to update this ngram to the next lower order ngram. Returns `true` if successful.
     * Otherwise, the next lower order ngrams are encoded as primitive and can be obtained from
     * [getLowerOrderPrimitiveNgram].
     */
    fun toLowerOrderNgram(): Boolean {
        val length = length()
        // Switch to PrimitiveNgram if possible
        return if (length <= PrimitiveNgram.MAX_NGRAM_LENGTH + 1) {
            false
        } else {
            value[0] = (length - 1).toChar()
            true
        }
    }

    /**
     * Returns the next lower order ngram.
     *
     * Must only be called if [toLowerOrderNgram] returned `false`.
     */
    fun getLowerOrderPrimitiveNgram(): PrimitiveNgram {
        lazyAssert { length() == PrimitiveNgram.MAX_NGRAM_LENGTH + 1 && PrimitiveNgram.MAX_NGRAM_LENGTH == 3 }
        return PrimitiveNgram.of(value[1], value[2], value[3])
    }

    override fun toString(): String {
        return String(value, 1, length())
    }
}
