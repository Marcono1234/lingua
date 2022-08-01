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

package com.github.pemistahl.lingua.internal.util.extension

internal fun CharSequence.containsAnyOf(characters: String): Boolean {
    // Check each char of `this` (instead of reverse, checking `characters`) to only iterate over `this` once
    // Don't use `any { ... }` to only evaluate `length` once
    val length = this.length
    for (i in 0 until length) {
        if (characters.indexOf(get(i)) != -1) {
            return true
        }
    }
    return false
}

internal fun CharSequence.isLogogram(): Boolean {
    return length == 1 && this[0].isLogogram()
}
