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

internal fun CharSequence.isLogogram(): Boolean {
    return length == 1 && this[0].isLogogram()
}

/**
 * Custom `replaceAll` implementation which only supports plain text replacement strings
 * (and no references to captured groups). This implementation is also more efficient because:
 * - `Matcher.replaceAll` creates a new StringBuilder for every match
 *   (https://bugs.openjdk.org/browse/JDK-8291598)
 * - it does not create intermediate String results but directly applies the next replacement
 *   on the previous result
 */
internal fun CharSequence.replaceAll(replacements: List<Pair<Regex, String>>): CharSequence {
    var currentInput = this
    replacements.forEach {
        val matcher = it.first.toPattern().matcher(currentInput)

        if (matcher.find()) {
            val replacement = it.second
            var nextRangeStart = 0
            val builder = StringBuilder()
            do {
                builder.append(currentInput, nextRangeStart, matcher.start())
                builder.append(replacement)
                nextRangeStart = matcher.end()
            } while (matcher.find())

            if (nextRangeStart < currentInput.length) {
                // Append remainder
                builder.append(currentInput, nextRangeStart, currentInput.length)
            }
            currentInput = builder
        }
    }

    return currentInput
}
