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

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet

internal class TestDataLanguageModel(
    // Note: These are Arrays to make iteration of them later more efficient
    /** Ngrams which could not be encoded as [PrimitiveNgram] */
    val objectNgrams: Array<String>,
    /** [PrimitiveNgram] values */
    val primitiveNgrams: LongArray
) {
    fun hasOnlyPrimitives(): Boolean {
        return objectNgrams.isEmpty()
    }

    companion object {
        fun fromText(text: CharSequence, ngramLength: Int): TestDataLanguageModel {
            require(ngramLength in 1..5) {
                "ngram length $ngramLength is not in range 1..5"
            }

            // Uses ObjectLinkedOpenHashSet instead of regular JDK LinkedHashSet to avoid creation of HashMap$Node
            // (maybe that is slightly premature optimization, unless language detection is called extremely
            // often for small texts)
            // Uses linked set for consistent order
            val ngrams = ObjectLinkedOpenHashSet<String>()
            val primitiveNgrams = LongLinkedOpenHashSet()

            var i = 0
            var nextLetterCheckIndex = 0
            sliceLoop@ while (i <= text.length - ngramLength) {
                while (nextLetterCheckIndex < i + ngramLength) {
                    // TODO: Should this `fromText` method work on the split words instead (and also remove letter
                    //      check here because text was already cleaned up?)
                    if (!Character.isLetter(text[nextLetterCheckIndex++])) {
                        // Skip all potential ngrams which would include the non-letter
                        i = nextLetterCheckIndex
                        continue@sliceLoop
                    }
                }

                val primitiveNgram = PrimitiveNgram.of(text, i, ngramLength)
                when (primitiveNgram.value) {
                    PrimitiveNgram.NONE -> ngrams.add(text.substring(i, i + ngramLength))
                    else -> primitiveNgrams.add(primitiveNgram.value)
                }
                i++
            }
            return TestDataLanguageModel(ngrams.toTypedArray(), primitiveNgrams.toLongArray())
        }
    }
}
