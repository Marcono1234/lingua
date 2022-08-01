package com.github.pemistahl.lingua.internal.util

/*
 * Custom `replaceAll` implementation which only supports plain text replacement strings
 * (and no references to captured groups). This implementation is also more efficient because:
 * - `Matcher.replaceAll` creates a new StringBuilder for every matcher
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
