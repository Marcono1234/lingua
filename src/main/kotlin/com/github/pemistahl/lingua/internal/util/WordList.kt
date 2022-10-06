package com.github.pemistahl.lingua.internal.util

import com.github.pemistahl.lingua.internal.util.extension.isLogogram
import kotlin.math.max
import kotlin.math.min

/**
 * Stores information about a string being split at spaces and logograms.
 * This is a custom data structure to avoid all the separate String instances when
 * splitting a String the normal way.
 */
internal class WordList private constructor(
    private val text: CharSequence,
    private val maxPositionsIndex: Int,
    private val positions: ByteArray,
) {
    companion object {
        fun build(text: CharSequence): WordList {
            var currentPositionsIndex = 0
            var positions = ByteArray(min(text.length / 2, 32))

            var textIndex = 0

            fun addOffset(offset: Int) {
                // Make sure there are at least two elements free
                if (currentPositionsIndex + 2 >= positions.size) {
                    // Grow array
                    val remainingChars = text.length - textIndex
                    positions = positions.copyOf(positions.size + max(8, min(remainingChars / 2, 200)))
                }

                if (offset <= Byte.MAX_VALUE) {
                    positions[currentPositionsIndex++] = offset.toByte()
                } else if (offset <= 1.shl(15)) {
                    positions[currentPositionsIndex++] = 0b1000_0000.or(offset.and(0b0111_1111)).toByte()
                    positions[currentPositionsIndex++] = offset.shr(7).toByte()
                } else {
                    // Well-formed text should not have words or whitespace sections with length >= 1^15
                    throw IllegalArgumentException("Unsupported offset $offset")
                }
            }

            var lastWordEnd = 0
            var nextWordStart = 0
            while (textIndex < text.length) {
                val char = text[textIndex]

                if (char == ' ') {
                    // If equal, skip consecutive whitespaces
                    if (nextWordStart != textIndex) {
                        addOffset(nextWordStart - lastWordEnd) // start offset
                        addOffset(textIndex - nextWordStart) // length
                        lastWordEnd = textIndex
                    }
                    nextWordStart = textIndex + 1
                } else if (char.isLogogram()) {
                    if (nextWordStart != textIndex) {
                        // Add previous word excluding trailing logogram
                        addOffset(nextWordStart - lastWordEnd) // start offset
                        addOffset(textIndex - nextWordStart) // length
                        lastWordEnd = textIndex
                    }

                    // Add logogram on its own
                    addOffset(textIndex - lastWordEnd) // start offset
                    addOffset(1) // length
                    lastWordEnd = textIndex + 1
                    nextWordStart = textIndex + 1
                }

                textIndex++
            }

            if (nextWordStart != text.length) {
                addOffset(nextWordStart - lastWordEnd) // start offset
                addOffset(text.length - nextWordStart) // length
            }

            return WordList(text, currentPositionsIndex, positions)
        }
    }

    internal data class ReuseableCharSequence(
        private val text: CharSequence,
        var start: Int = 0,
        var end: Int = 0,
    ) : CharSequence {
        override val length: Int
            get() = end - start

        override fun get(index: Int): Char {
            val i = start + index
            assert(index >= 0 && i < end)
            return text[i]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            // Currently not needed by this project
            throw UnsupportedOperationException()
        }

        override fun toString(): String {
            // Currently not needed by this project
            throw UnsupportedOperationException()
        }
    }

    inline fun forEach(consumer: (word: CharSequence) -> Unit) {
        val charSequence = ReuseableCharSequence(text)

        val positions = this@WordList.positions
        val maxPositionsIndex = this@WordList.maxPositionsIndex
        var positionsIndex = 0

        var lastEnd = 0

        while (positionsIndex < maxPositionsIndex) {
            var byte0 = positions[positionsIndex++]
            val offset = if (byte0 < 0) {
                val byte1 = positions[positionsIndex++]
                byte0.toInt().and(0b0111_1111).or(byte1.toInt().and(0b1111_1111).shl(7))
            } else {
                byte0.toInt()
            }

            byte0 = positions[positionsIndex++]
            val length = if (byte0 < 0) {
                val byte1 = positions[positionsIndex++]
                byte0.toInt().and(0b0111_1111).or(byte1.toInt().and(0b1111_1111).shl(7))
            } else {
                byte0.toInt()
            }

            val start = lastEnd + offset
            val end = start + length
            lastEnd = end

            charSequence.start = start
            charSequence.end = end
            consumer(charSequence)
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append('[')
        var isNotEmpty = false
        forEach {
            // Use loop because custom CharSequence intentionally does not support toString
            it.forEach(builder::append)
            builder.append(',')
            isNotEmpty = true
        }

        if (isNotEmpty) {
            // Delete last ','
            builder.deleteCharAt(builder.length - 1)
        }
        builder.append(']')
        return builder.toString()
    }
}
