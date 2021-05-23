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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet

internal data class TestDataLanguageModel(val objectNgrams: Set<ObjectNgram>, val primitiveNgrams: LongSet) {
    fun hasOnlyPrimitives(): Boolean {
        return objectNgrams.isEmpty()
    }

    companion object {
        fun fromText(text: String, ngramLength: Int): TestDataLanguageModel {
            require(ngramLength in 1..5) {
                "ngram length $ngramLength is not in range 1..5"
            }

            val ngrams = hashSetOf<ObjectNgram>()
            val primitiveNgrams = LongOpenHashSet()

            sliceLoop@ for (i in 0..text.length - ngramLength) {
                for (sliceIndex in i until i + ngramLength) {
                    if (!Character.isLetter(text[sliceIndex])) {
                        continue@sliceLoop
                    }
                }

                val primitiveNgram = PrimitiveNgram.of(text, i, ngramLength)
                when (primitiveNgram.value) {
                    PrimitiveNgram.NONE.value -> ngrams.add(ObjectNgram(text.substring(i, i + ngramLength)))
                    else -> primitiveNgrams.add(primitiveNgram.value)
                }
            }
            return TestDataLanguageModel(ngrams, primitiveNgrams)
        }
    }
}
