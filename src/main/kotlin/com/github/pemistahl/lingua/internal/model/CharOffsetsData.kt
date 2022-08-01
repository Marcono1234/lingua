package com.github.pemistahl.lingua.internal.model

import com.github.pemistahl.lingua.internal.model.extension.readCharArray
import com.github.pemistahl.lingua.internal.model.extension.readShortArray
import com.github.pemistahl.lingua.internal.model.extension.writeCharArray
import com.github.pemistahl.lingua.internal.model.extension.writeShortArray
import it.unimi.dsi.fastutil.chars.Char2IntAVLTreeMap
import it.unimi.dsi.fastutil.chars.Char2IntMap
import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.io.DataInputStream
import java.io.DataOutput
import java.util.TreeSet

internal class CharOffsetsData(
    /** All of the chars used by the ngrams, sorted in ascending order */
    private val chars: CharArray,
    /**
     * For each char at the corresponding position in [chars], stores the relative encoding offset for that char.
     * The encoding offsets are assigned in ascending order (starting at 0), starting with the most frequent chars.
     * This makes it likelier that for many ngrams a compact primitive encoding can be used.
     */
    private val charOffsets: ShortArray,
) {
    companion object {
        @JvmStatic
        fun createCharOffsetsData(vararg ngrams: Object2IntMap<String>): CharOffsetsData {
            val charCounts = Char2IntOpenHashMap()
            ngrams.asSequence().flatMap(Object2IntMap<String>::keys)
                .forEach { ngram -> ngram.chars().forEach { charCounts.addTo(it.toChar(), 1) } }

            // Sort by occurrence count; most frequent chars first
            val charRanks = TreeSet(
                Comparator.comparingInt(Char2IntMap.Entry::getIntValue).reversed()
                    .thenComparingInt { e -> e.charKey.code }
            )
            charCounts.char2IntEntrySet().forEach(charRanks::add)

            // Sort by char value, mapping to its occurrence index
            val charsToOffset = Char2IntAVLTreeMap()
            charRanks.forEachIndexed { index, entry -> charsToOffset.put(entry.charKey, index) }

            val chars = charsToOffset.keys.toCharArray()
            val charOffsets = ShortArray(chars.size)
            charsToOffset.values.forEachIndexed { index, i -> charOffsets[index] = i.toShort() }

            return CharOffsetsData(chars, charOffsets)
        }

        @JvmStatic
        fun fromBinary(dataIn: DataInputStream): CharOffsetsData {
            val charsCount = dataIn.readUnsignedShort()
            val chars = dataIn.readCharArray(charsCount)
            val charOffsets = dataIn.readShortArray(charsCount)

            return CharOffsetsData(chars, charOffsets)
        }
    }

    init {
        // Assume that language uses at most 65535 chars (otherwise binary encoding would overflow)
        check(chars.size <= 65535)
    }

    private fun getCharOffset(char: Char): Int {
        val charIndex = chars.binarySearch(char)
        if (charIndex < 0) return -1
        return charOffsets[charIndex].toInt().and(0xFFFF) // UShort
    }

    inline fun <R> useEncodedUnigram(
        char0: Char,
        asByte: (encodedNgram: Byte) -> R,
        asShort: (encodedNgram: Short) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }

        return if (charOffset0 <= 255) asByte(charOffset0.toByte()) else asShort(char0.code.toShort())
    }

    inline fun <R> useEncodedUnigram(
        unigram: String,
        asByte: (encodedNgram: Byte) -> R,
        asShort: (encodedNgram: Short) -> R,
        notEncodable: () -> R,
    ): R = useEncodedUnigram(unigram[0], asByte, asShort, notEncodable)

    inline fun <R> useEncodedBigram(
        char0: Char,
        char1: Char,
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(char1).also { if (it == -1) return notEncodable() }

        return if (charOffset0 <= 255 && charOffset1 <= 255) {
            val encoded = charOffset0 or (charOffset1 shl 8)
            asShort(encoded.toShort())
        } else {
            val encoded = char0.code or (char1.code shl 16)
            asInt(encoded)
        }
    }

    inline fun <R> useEncodedBigram(
        bigram: String,
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R,
        notEncodable: () -> R,
    ): R = useEncodedBigram(bigram[0], bigram[1], asShort, asInt, notEncodable)

    inline fun <R> useEncodedTrigram(
        char0: Char,
        char1: Char,
        char2: Char,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(char1).also { if (it == -1) return notEncodable() }
        val charOffset2 = getCharOffset(char2).also { if (it == -1) return notEncodable() }

        // Int encoding: First two get 11 bits each, last gets 10 bits (2*11 + 10 = 32)
        return if (
            charOffset0 < (1 shl 11) &&
            charOffset1 < (1 shl 11) &&
            charOffset2 < (1 shl 10)
        ) {
            val encoded = (
                charOffset0
                    or (charOffset1 shl 11)
                    or (charOffset2 shl 11 * 2)
                )
            asInt(encoded)
        } else {
            val encoded = (
                char0.code.toLong()
                    or (char1.code.toLong() shl 16)
                    or (char2.code.toLong() shl 32)
                )
            asLong(encoded)
        }
    }

    inline fun <R> useEncodedTrigram(
        trigram: String,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R = useEncodedTrigram(trigram[0], trigram[1], trigram[2], asInt, asLong, notEncodable)

    inline fun <R> useEncodedQuadrigram(
        char0: Char,
        char1: Char,
        char2: Char,
        char3: Char,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(char1).also { if (it == -1) return notEncodable() }
        val charOffset2 = getCharOffset(char2).also { if (it == -1) return notEncodable() }
        val charOffset3 = getCharOffset(char3).also { if (it == -1) return notEncodable() }

        return if (
            charOffset0 <= 255 &&
            charOffset1 <= 255 &&
            charOffset2 <= 255 &&
            charOffset3 <= 255
        ) {
            val encoded = (
                charOffset0
                    or (charOffset1 shl 8)
                    or (charOffset2 shl 16)
                    or (charOffset3 shl 24)
                )
            asInt(encoded)
        } else {
            val encoded = (
                char0.code.toLong()
                    or (char1.code.toLong() shl 16)
                    or (char2.code.toLong() shl 32)
                    or (char3.code.toLong() shl 48)
                )
            asLong(encoded)
        }
    }

    inline fun <R> useEncodedQuadrigram(
        quadrigram: String,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R {
        return useEncodedQuadrigram(
            quadrigram[0], quadrigram[1], quadrigram[2], quadrigram[3],
            asInt,
            asLong,
            notEncodable
        )
    }

    inline fun <R> useEncodedFivegram(
        char0: Char,
        char1: Char,
        char2: Char,
        char3: Char,
        char4: Char,
        fivegramAsString: () -> String,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        asObject: (encodedNgram: String) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(char1).also { if (it == -1) return notEncodable() }
        val charOffset2 = getCharOffset(char2).also { if (it == -1) return notEncodable() }
        val charOffset3 = getCharOffset(char3).also { if (it == -1) return notEncodable() }
        val charOffset4 = getCharOffset(char4).also { if (it == -1) return notEncodable() }

        /*
         * Int encoding: First two get 7 bits each, last three get 6 bits (2*7 + 3*6 = 32)
         */
        return if (
            charOffset0 < (1 shl 7) &&
            charOffset1 < (1 shl 7) &&
            charOffset2 < (1 shl 6) &&
            charOffset3 < (1 shl 6) &&
            charOffset4 < (1 shl 6)
        ) {
            val encoded = (
                charOffset0
                    or (charOffset1 shl 7)
                    or (charOffset2 shl (7 * 2))
                    or (charOffset3 shl (7 * 2 + 6))
                    or (charOffset4 shl (7 * 2 + 6 * 2))
                )
            asInt(encoded)
        }
        /*
         * Long encoding: First four get 13 bits each, last gets 12 bits (4*13 + 12 = 64)
         */
        else if (
            charOffset0 < (1 shl 13) &&
            charOffset1 < (1 shl 13) &&
            charOffset2 < (1 shl 13) &&
            charOffset3 < (1 shl 13) &&
            charOffset4 < (1 shl 12)
        ) {
            val encoded = (
                charOffset0.toLong()
                    or (charOffset1.toLong() shl 13)
                    or (charOffset2.toLong() shl (13 * 2))
                    or (charOffset3.toLong() shl (13 * 3))
                    or (charOffset4.toLong() shl (13 * 4))
                )
            asLong(encoded)
        } else {
            // Fall back to using ngram object
            asObject(fivegramAsString())
        }
    }

    inline fun <R> useEncodedFivegram(
        fivegram: String,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        asObject: (encodedNgram: String) -> R,
        notEncodable: () -> R,
    ): R {
        return useEncodedFivegram(
            fivegram[0], fivegram[1], fivegram[2], fivegram[3], fivegram[4],
            { fivegram },
            asInt,
            asLong,
            asObject,
            notEncodable
        )
    }

    fun writeBinary(dataOut: DataOutput) {
        dataOut.writeShort(chars.size)
        dataOut.writeCharArray(chars)
        dataOut.writeShortArray(charOffsets)
    }
}
