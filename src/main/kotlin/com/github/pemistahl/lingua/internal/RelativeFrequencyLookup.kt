package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.internal.util.extension.readCharArray
import com.github.pemistahl.lingua.internal.util.extension.readShortArray
import com.github.pemistahl.lingua.internal.util.extension.writeCharArray
import com.github.pemistahl.lingua.internal.util.extension.writeShortArray
import com.squareup.moshi.JsonReader
import it.unimi.dsi.fastutil.chars.Char2IntAVLTreeMap
import it.unimi.dsi.fastutil.chars.Char2IntMap
import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.source
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.math.abs

private fun openResourceInputStream(resourcePath: String): InputStream {
    return Language::class.java.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Resource '$resourcePath' does not exist")
}

private fun openBinaryDataInput(resourcePath: String): DataInputStream {
    return DataInputStream(openResourceInputStream(resourcePath).buffered())
}

private fun openBinaryDataOutput(
    resourcesDirectory: Path,
    resourcePath: String,
    changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
): DataOutputStream {
    val file = resourcesDirectory.resolve(resourcePath.removePrefix("/"))
    Files.createDirectories(file.parent)

    val oldSizeBytes = if (Files.isRegularFile(file)) Files.size(file) else null
    return object : DataOutputStream(Files.newOutputStream(file).buffered()) {
        override fun close() {
            super.close()
            val newSizeBytes = Files.size(file)
            changeSummaryCallback(oldSizeBytes, newSizeBytes)
        }
    }
}

@Suppress("ArrayInDataClass")
private data class CharOffsetsData(
    /** All of the chars used by the ngrams, sorted in ascending order */
    val chars: CharArray,
    /**
     * For each char at the corresponding position in [chars], stores the relative encoding offset for that char.
     * The encoding offsets are assigned in ascending order (starting at 0), starting with the most frequent chars.
     * This makes it likelier that for many ngrams a compact primitive encoding can be used.
     */
    val charOffsets: ShortArray,
)

private fun createCharOffsetsData(vararg ngrams: Object2IntMap<String>): CharOffsetsData {
    val charCounts = Char2IntOpenHashMap()
    ngrams.asSequence().flatMap(Object2IntMap<String>::keys).forEach { ngram -> ngram.chars().forEach { charCounts.addTo(it.toChar(), 1) } }

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

private fun fromJson(language: Language, jsonStream: InputStream): Object2IntOpenHashMap<String> {
    val jsonReader = JsonReader.of(jsonStream.source().buffer())
    jsonReader.beginObject()

    var isLanguageMissing = true
    var ngramsMap: Object2IntOpenHashMap<String>? = null

    while (jsonReader.hasNext()) {
        when (val name = jsonReader.nextName()) {
            "language" -> {
                if (isLanguageMissing) {
                    isLanguageMissing = false
                    if (Language.valueOf(jsonReader.nextString()) != language) {
                        throw IllegalArgumentException("JSON file is for wrong language")
                    }
                } else throw IllegalArgumentException("Duplicate language at ${jsonReader.path}")
            }
            "ngrams" -> {
                if (ngramsMap == null) {
                    ngramsMap = Object2IntOpenHashMap()
                    jsonReader.beginObject()
                    while (jsonReader.hasNext()) {
                        val (numerator, denominator) = jsonReader.nextName().split('/')
                            .map(String::toInt)
                        val encodedFrequency = encodeFrequency(numerator, denominator)
                        val ngrams = jsonReader.nextString().split(' ')
                        ngrams.forEach { ngram ->
                            ngramsMap.put(ngram, encodedFrequency)
                        }
                    }
                    jsonReader.endObject()
                } else throw IllegalArgumentException("Duplicate ngrams at ${jsonReader.path}")
            }
            else -> throw IllegalArgumentException("Unknown name '$name' at ${jsonReader.path}")
        }
    }
    jsonReader.endObject()

    if (isLanguageMissing) throw IllegalArgumentException("Model data is missing language")
    if (ngramsMap == null) throw IllegalArgumentException("Model data is missing ngrams")
    return ngramsMap
}

private fun fromJson(language: Language, jsonName: String): Object2IntOpenHashMap<String> {
    val resourcePath = "/language-models/${language.isoCode639_1}/$jsonName"
    openResourceInputStream(resourcePath).use {
        return fromJson(language, it)
    }
}

private fun getBinaryModelResourceName(language: Language, fileName: String): String {
    return "/language-models/${language.isoCode639_1}/$fileName"
}

/**
 * Multiply frequency, which is in range (0.0, 1.0), with UInt.MAX_VALUE + 1 to
 * map the decimal places to a 32-bit integer.
 */
private const val ENCODING_MULTIPLIER = 1L shl 32

private fun encodeFrequency(numerator: Int, denominator: Int): Int {
    // Use custom encoding since 32-bit Float 'wastes' bits for sign and exponent
    val encoded = (numerator * ENCODING_MULTIPLIER) / denominator
    return when {
        // For values which round to >= 1.0 pretend they are slightly < 1.0
        // Otherwise they would be encoded as 0
        encoded > UInt.MAX_VALUE.toLong() -> UInt.MAX_VALUE.toInt()
        // For values equal to 0 pretend they are slightly > 0.0 to allow using
        // 0 as special value for binary model files
        encoded == 0L -> 1
        else -> encoded.toInt()
    }
}

/** Counterpart to [encodeFrequency] */
private fun decodeFrequency(encoded: Int): Double {
    return encoded.toUInt().toDouble() / ENCODING_MULTIPLIER
}

/*
 * Implementation note:
 * Declares two types of lookups (uni-, bi- and trigrams, and quadri- and fivegrams)
 * since that is how LanguageDetector currently uses the lookups; for short texts
 * it creates ngrams of all lengths (1 - 5), for long texts it only creates trigrams
 * and then lower order ngrams. Therefore these two lookup types allow lazily loading
 * the required models into memory.
 */

private inline fun getCharOffset(chars: CharArray, charOffsets: ShortArray, char: Char): Int {
    val charIndex = chars.binarySearch(char)
    if (charIndex < 0) return -1
    return charOffsets[charIndex].toUShort().toInt()
}

/**
 * Frequency lookup for uni-, bi- and trigrams.
 */
internal class UniBiTrigramRelativeFrequencyLookup private constructor(
    /** Sorted array representing all chars used by stored the uni-, bi- and trigrams */
    private val chars: CharArray,
    /** For each char in [chars] contains the encoding offset for that char, see [createCharOffsetsData] */
    private val charOffsets: ShortArray,
    private val unigramsAsByte: ImmutableByte2IntMap,
    private val unigramsAsShort: ImmutableShort2IntTrieMap,
    private val bigramsAsShort: ImmutableShort2IntTrieMap,
    private val bigramsAsInt: ImmutableInt2IntTrieMap,
    private val trigramsAsShort: ImmutableShort2IntTrieMap,
    private val trigramsAsInt: ImmutableInt2IntTrieMap,
    private val trigramsAsLong: ImmutableLong2IntMap,
    // Temporary builders; TODO: solve this in a cleaner way
    private val unigramsAsByteBuilder: ImmutableByte2IntMap.Builder = ImmutableByte2IntMap.Builder(),
    private val unigramsAsShortBuilder: ImmutableShort2IntTrieMap.Builder = ImmutableShort2IntTrieMap.Builder(),
    private val bigramsAsShortBuilder: ImmutableShort2IntTrieMap.Builder = ImmutableShort2IntTrieMap.Builder(),
    private val bigramsAsIntBuilder: ImmutableInt2IntTrieMap.Builder = ImmutableInt2IntTrieMap.Builder(),
    private val trigramsAsShortBuilder: ImmutableShort2IntTrieMap.Builder = ImmutableShort2IntTrieMap.Builder(),
    private val trigramsAsIntBuilder: ImmutableInt2IntTrieMap.Builder = ImmutableInt2IntTrieMap.Builder(),
    private val trigramsAsLongBuilder: ImmutableLong2IntMap.Builder = ImmutableLong2IntMap.Builder(),
) {
    companion object {
        fun fromJson(language: Language): UniBiTrigramRelativeFrequencyLookup {
            val unigrams = fromJson(language, "unigrams.json")
            val bigrams = fromJson(language, "bigrams.json")
            val trigrams = fromJson(language, "trigrams.json")

            val (chars, charOffsets) = createCharOffsetsData(unigrams, bigrams, trigrams)

            val lookup = UniBiTrigramRelativeFrequencyLookup(
                chars,
                charOffsets,
            )
            unigrams.object2IntEntrySet().fastForEach {
                lookup.putUnigramFrequency(it.key, it.intValue)
            }
            bigrams.object2IntEntrySet().fastForEach {
                lookup.putBigramFrequency(it.key, it.intValue)
            }
            trigrams.object2IntEntrySet().fastForEach {
                lookup.putTrigramFrequency(it.key, it.intValue)
            }
            return lookup.finishCreation()
        }

        private fun getBinaryModelResourceName(language: Language): String {
            return getBinaryModelResourceName(language, "uni-bi-trigrams.bin")
        }

        fun fromBinary(language: Language): UniBiTrigramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val charsCount = it.readUnsignedShort()
                val chars = it.readCharArray(charsCount)
                val charOffsets = it.readShortArray(charsCount)

                val unigramsAsByte = ImmutableByte2IntMap.fromBinary(it)
                val unigramsAsShort = ImmutableShort2IntTrieMap.fromBinary(it)

                val bigramsAsShort = ImmutableShort2IntTrieMap.fromBinary(it)
                val bigramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)

                val trigramsAsShort = ImmutableShort2IntTrieMap.fromBinary(it)
                val trigramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)
                val trigramsAsLong = ImmutableLong2IntMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return UniBiTrigramRelativeFrequencyLookup(
                    chars,
                    charOffsets,
                    unigramsAsByte,
                    unigramsAsShort,
                    bigramsAsShort,
                    bigramsAsInt,
                    trigramsAsShort,
                    trigramsAsInt,
                    trigramsAsLong
                )
            }
        }
    }

    private constructor(
        chars: CharArray,
        charOffsets: ShortArray,
    ) : this(
        chars,
        charOffsets,
        ImmutableByte2IntMap.Builder().build(),
        ImmutableShort2IntTrieMap.Builder().build(),
        ImmutableShort2IntTrieMap.Builder().build(),
        ImmutableInt2IntTrieMap.Builder().build(),
        ImmutableShort2IntTrieMap.Builder().build(),
        ImmutableInt2IntTrieMap.Builder().build(),
        ImmutableLong2IntMap.Builder().build()
    )

    private fun getCharOffset(char: Char) = getCharOffset(chars, charOffsets, char)

    private inline fun <R> useEncodedUnigram(
        char0: Char,
        asByte: (encodedNgram: Byte) -> R,
        asShort: (encodedNgram: Short) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }

        return if (charOffset0 <= 255) asByte(charOffset0.toByte()) else asShort(char0.code.toShort())
    }

    private inline fun <R> String.useEncodedUnigram(
        asByte: (encodedNgram: Byte) -> R,
        asShort: (encodedNgram: Short) -> R,
        notEncodable: () -> R,
    ): R = useEncodedUnigram(this[0], asByte, asShort, notEncodable)

    private inline fun <R> useEncodedBigram(
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

    private inline fun <R> String.useEncodedBigram(
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R,
        notEncodable: () -> R,
    ): R = useEncodedBigram(this[0], this[1], asShort, asInt, notEncodable)

    private inline fun <R> useEncodedTrigram(
        char0: Char,
        char1: Char,
        char2: Char,
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(char1).also { if (it == -1) return notEncodable() }
        val charOffset2 = getCharOffset(char2).also { if (it == -1) return notEncodable() }

        // Short encoding: First one gets 6 bits, last two get 5 bits (6 + 2*5 = 16)
        return if (
            charOffset0 < (1 shl 6)
            && charOffset1 < (1 shl 5)
            && charOffset2 < (1 shl 5)
        ) {
            val encoded = (
                charOffset0
                or (charOffset1 shl 6)
                or (charOffset2 shl 6 + 5)
            ).toShort()
            asShort(encoded)
        }
        // Int encoding: First two get 11 bits each, last gets 10 bits (2*11 + 10 = 32)
        else if (
            charOffset0 < (1 shl 11)
            && charOffset1 < (1 shl 11)
            && charOffset2 < (1 shl 10)
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

    private inline fun <R> String.useEncodedTrigram(
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R = useEncodedTrigram(this[0], this[1], this[2], asShort, asInt, asLong, notEncodable)

    private fun putUnigramFrequency(unigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$unigram'")
        }
        if (unigram.length != 1) {
            throw IllegalArgumentException("Invalid ngram length ${unigram.length}")
        }

        unigram.useEncodedUnigram(
            { unigramsAsByteBuilder.add(it, encodedFrequency) },
            { unigramsAsShortBuilder.add(it, encodedFrequency) },
            { throw AssertionError("Char offsets don't include chars of: $unigram") }
        )
    }

    private fun putBigramFrequency(bigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$bigram'")
        }
        if (bigram.length != 2) {
            throw IllegalArgumentException("Invalid ngram length ${bigram.length}")
        }

        bigram.useEncodedBigram(
            { bigramsAsShortBuilder.add(it, encodedFrequency) },
            { bigramsAsIntBuilder.add(it, encodedFrequency) },
            { throw AssertionError("Char offsets don't include chars of: $bigram") }
        )
    }

    private fun putTrigramFrequency(trigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$trigram'")
        }
        if (trigram.length != 3) {
            throw IllegalArgumentException("Invalid ngram length ${trigram.length}")
        }

        trigram.useEncodedTrigram(
            { trigramsAsShortBuilder.add(it, encodedFrequency) },
            { trigramsAsIntBuilder.add(it, encodedFrequency) },
            { trigramsAsLongBuilder.add(it, encodedFrequency) },
            { throw AssertionError("Char offsets don't include chars of: $trigram") }
        )
    }

    private fun finishCreation(): UniBiTrigramRelativeFrequencyLookup {
        return UniBiTrigramRelativeFrequencyLookup(
            chars,
            charOffsets,
            unigramsAsByteBuilder.build(),
            unigramsAsShortBuilder.build(),
            bigramsAsShortBuilder.build(),
            bigramsAsIntBuilder.build(),
            trigramsAsShortBuilder.build(),
            trigramsAsIntBuilder.build(),
            trigramsAsLongBuilder.build()
        )
    }

    fun getFrequency(ngram: PrimitiveNgram): Double {
        val (length, char0, char1, char2) = ngram
        return decodeFrequency(when (length) {
            1 -> useEncodedUnigram(
                char0,
                { unigramsAsByte.get(it) },
                { unigramsAsShort.get(it) },
                { 0 }
            )
            2 -> useEncodedBigram(
                char0, char1,
                { bigramsAsShort.get(it) },
                { bigramsAsInt.get(it) },
                { 0 }
            )
            3 -> useEncodedTrigram(
                char0, char1, char2,
                { trigramsAsShort.get(it) },
                { trigramsAsInt.get(it) },
                { trigramsAsLong.get(it) },
                { 0 }
            )
            else -> throw AssertionError("Invalid ngram length $length")
        })
    }

    /**
     * Writes the binary representation of the model data to a sub directory for
     * the specified language of the resources directory.
     *
     * @see fromBinary
     */
    fun writeBinary(
        resourcesDirectory: Path,
        language: Language,
        changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
    ) {
        val resourceName = getBinaryModelResourceName(language)

        openBinaryDataOutput(resourcesDirectory, resourceName, changeSummaryCallback).use { dataOut ->
            // Assume that language uses at most 65535 chars (otherwise would overflow)
            check(chars.size <= 65535)
            dataOut.writeShort(chars.size)
            dataOut.writeCharArray(chars)
            dataOut.writeShortArray(charOffsets)

            unigramsAsByte.writeBinary(dataOut)
            unigramsAsShort.writeBinary(dataOut)

            bigramsAsShort.writeBinary(dataOut)
            bigramsAsInt.writeBinary(dataOut)

            trigramsAsShort.writeBinary(dataOut)
            trigramsAsInt.writeBinary(dataOut)
            trigramsAsLong.writeBinary(dataOut)
        }
    }
}

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal class QuadriFivegramRelativeFrequencyLookup private constructor(
    /** Sorted array representing all chars used by stored the quadri- and fivegrams */
    private val chars: CharArray,
    /** For each char in [chars] contains the encoding offset for that char, see [createCharOffsetsData] */
    private val charOffsets: ShortArray,
    private val quadrigramsAsShort: ImmutableShort2IntTrieMap,
    private val quadrigramsAsInt: ImmutableInt2IntTrieMap,
    private val quadrigramsAsLong: ImmutableLong2IntMap,
    private val fivegramsAsInt: ImmutableInt2IntTrieMap,
    private val fivegramsAsLong: ImmutableLong2IntMap,
    private val fivegramsAsObject: ImmutableFivegram2IntMap,
    // Temporary builders; TODO: solve this in a cleaner way
    private val quadrigramsAsShortBuilder: ImmutableShort2IntTrieMap.Builder = ImmutableShort2IntTrieMap.Builder(),
    private val quadrigramsAsIntBuilder: ImmutableInt2IntTrieMap.Builder = ImmutableInt2IntTrieMap.Builder(),
    private val quadrigramsAsLongBuilder: ImmutableLong2IntMap.Builder = ImmutableLong2IntMap.Builder(),
    private val fivegramsAsIntBuilder: ImmutableInt2IntTrieMap.Builder = ImmutableInt2IntTrieMap.Builder(),
    private val fivegramsAsLongBuilder: ImmutableLong2IntMap.Builder = ImmutableLong2IntMap.Builder(),
    private val fivegramsAsObjectBuilder: ImmutableFivegram2IntMap.Builder = ImmutableFivegram2IntMap.Builder(),
) {
    companion object {
        val empty = QuadriFivegramRelativeFrequencyLookup(CharArray(0), ShortArray(0))

        fun fromJson(language: Language): QuadriFivegramRelativeFrequencyLookup {
            val quadrigrams = fromJson(language, "quadrigrams.json")
            val fivegrams = fromJson(language, "fivegrams.json")

            val (chars, charOffsets) = createCharOffsetsData(quadrigrams, fivegrams)

            val lookup = QuadriFivegramRelativeFrequencyLookup(
                chars,
                charOffsets
            )
            quadrigrams.object2IntEntrySet().fastForEach {
                lookup.putQuadrigramFrequency(it.key, it.intValue)
            }
            fivegrams.object2IntEntrySet().fastForEach {
                lookup.putFivegramFrequency(it.key, it.intValue)
            }
            return lookup.finishCreation()
        }

        private fun getBinaryModelResourceName(language: Language): String {
            return getBinaryModelResourceName(language, "quadri-fivegrams.bin")
        }

        fun fromBinary(language: Language): QuadriFivegramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val charsCount = it.readUnsignedShort()
                val chars = it.readCharArray(charsCount)
                val charOffsets = it.readShortArray(charsCount)

                val quadrigramsAsShort = ImmutableShort2IntTrieMap.fromBinary(it)
                val quadrigramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)
                val quadrigramsAsLong = ImmutableLong2IntMap.fromBinary(it)

                val fivegramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)
                val fivegramsAsLong = ImmutableLong2IntMap.fromBinary(it)
                val fivegramsAsObject = ImmutableFivegram2IntMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return QuadriFivegramRelativeFrequencyLookup(
                    chars,
                    charOffsets,
                    quadrigramsAsShort,
                    quadrigramsAsInt,
                    quadrigramsAsLong,
                    fivegramsAsInt,
                    fivegramsAsLong,
                    fivegramsAsObject
                )
            }
        }
    }

    private constructor(
        chars: CharArray,
        charOffsets: ShortArray,
    ) : this(
        chars,
        charOffsets,
        ImmutableShort2IntTrieMap.Builder().build(),
        ImmutableInt2IntTrieMap.Builder().build(),
        ImmutableLong2IntMap.Builder().build(),
        ImmutableInt2IntTrieMap.Builder().build(),
        ImmutableLong2IntMap.Builder().build(),
        ImmutableFivegram2IntMap.Builder().build()
    )

    private fun getCharOffset(char: Char) = getCharOffset(chars, charOffsets, char)

    private inline fun <R> String.useEncodedQuadrigram(
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        notEncodable: () -> R,
    ): R {
        val char0 = this[0]
        val char1 = this[1]
        val char2 = this[2]
        val char3 = this[3]
        val charOffset0 = getCharOffset(char0).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(char1).also { if (it == -1) return notEncodable() }
        val charOffset2 = getCharOffset(char2).also { if (it == -1) return notEncodable() }
        val charOffset3 = getCharOffset(char3).also { if (it == -1) return notEncodable() }

        return if (
            charOffset0 <= 15
            && charOffset1 <= 15
            && charOffset2 <= 15
            && charOffset3 <= 15
        ) {
            val encoded = (
                charOffset0
                or (charOffset1 shl 4)
                or (charOffset2 shl 8)
                or (charOffset3 shl 12)
            ).toShort()
            asShort(encoded)
        }
        else if (
            charOffset0 <= 255
            && charOffset1 <= 255
            && charOffset2 <= 255
            && charOffset3 <= 255
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

    private inline fun <R> String.useEncodedFivegram(
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        asObject: (encodedNgram: String) -> R,
        notEncodable: () -> R,
    ): R {
        val charOffset0 = getCharOffset(this[0]).also { if (it == -1) return notEncodable() }
        val charOffset1 = getCharOffset(this[1]).also { if (it == -1) return notEncodable() }
        val charOffset2 = getCharOffset(this[2]).also { if (it == -1) return notEncodable() }
        val charOffset3 = getCharOffset(this[3]).also { if (it == -1) return notEncodable() }
        val charOffset4 = getCharOffset(this[4]).also { if (it == -1) return notEncodable() }

        // Int encoding: First two get 7 bits each, last three get 6 bits (2*7 + 3*6 = 32)
        return if (
            charOffset0 < (1 shl 7)
            && charOffset1 < (1 shl 7)
            && charOffset2 < (1 shl 6)
            && charOffset3 < (1 shl 6)
            && charOffset4 < (1 shl 6)
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
        // Long encoding: First four get 13 bits each, last gets 12 bits (4*13 + 12 = 64)
        else if (
            charOffset0 < (1 shl 13)
            && charOffset1 < (1 shl 13)
            && charOffset2 < (1 shl 13)
            && charOffset3 < (1 shl 13)
            && charOffset4 < (1 shl 12)
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
            asObject(this)
        }
    }

    private fun putQuadrigramFrequency(quadrigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$quadrigram'")
        }
        if (quadrigram.length != 4) {
            throw IllegalArgumentException("Invalid ngram length ${quadrigram.length}")
        }

        quadrigram.useEncodedQuadrigram(
            { quadrigramsAsShortBuilder.add(it, encodedFrequency) },
            { quadrigramsAsIntBuilder.add(it, encodedFrequency) },
            { quadrigramsAsLongBuilder.add(it, encodedFrequency) },
            { throw AssertionError("Char offsets don't include chars of: $quadrigram") }
        )
    }

    private fun putFivegramFrequency(fivegram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$fivegram'")
        }
        if (fivegram.length != 5) {
            throw IllegalArgumentException("Invalid ngram length ${fivegram.length}")
        }

        fivegram.useEncodedFivegram(
            { fivegramsAsIntBuilder.add(it, encodedFrequency) },
            { fivegramsAsLongBuilder.add(it, encodedFrequency) },
            { fivegramsAsObjectBuilder.add(it, encodedFrequency) },
            { throw AssertionError("Char offsets don't include chars of: $fivegram") }
        )
    }

    private fun finishCreation(): QuadriFivegramRelativeFrequencyLookup {
        return QuadriFivegramRelativeFrequencyLookup(
            chars,
            charOffsets,
            quadrigramsAsShortBuilder.build(),
            quadrigramsAsIntBuilder.build(),
            quadrigramsAsLongBuilder.build(),
            fivegramsAsIntBuilder.build(),
            fivegramsAsLongBuilder.build(),
            fivegramsAsObjectBuilder.build()
        )
    }

    fun getFrequency(ngram: ObjectNgram): Double {
        val ngramStr = ngram.value

        return decodeFrequency(when (ngramStr.length) {
            4 -> ngramStr.useEncodedQuadrigram(
                { quadrigramsAsShort.get(it) },
                { quadrigramsAsInt.get(it) },
                { quadrigramsAsLong.get(it) },
                { 0 }
            )
            5 -> ngramStr.useEncodedFivegram(
                { fivegramsAsInt.get(it) },
                { fivegramsAsLong.get(it) },
                { fivegramsAsObject.get(it) },
                { 0 }
            )
            else -> throw IllegalArgumentException("Invalid Ngram length")
        })
    }

    /**
     * Writes the binary representation of the model data to a sub directory for
     * the specified language of the resources directory.
     *
     * @see fromBinary
     */
    fun writeBinary(
        resourcesDirectory: Path,
        language: Language,
        changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
    ) {
        val resourceName = getBinaryModelResourceName(language)

        openBinaryDataOutput(resourcesDirectory, resourceName, changeSummaryCallback).use { dataOut ->
            // Assume that language uses at most 65535 chars (otherwise would overflow)
            check(chars.size <= 65535)
            dataOut.writeShort(chars.size)
            dataOut.writeCharArray(chars)
            dataOut.writeShortArray(charOffsets)

            quadrigramsAsShort.writeBinary(dataOut)
            quadrigramsAsInt.writeBinary(dataOut)
            quadrigramsAsLong.writeBinary(dataOut)

            fivegramsAsInt.writeBinary(dataOut)
            fivegramsAsLong.writeBinary(dataOut)
            fivegramsAsObject.writeBinary(dataOut)
        }
    }
}

internal fun writeBinaryModels(resourcesDirectory: Path) {
    runBlocking {
        Language.all().map { language ->
            async(Dispatchers.IO) {
                UniBiTrigramRelativeFrequencyLookup.fromJson(language)
                    .writeBinary(resourcesDirectory, language, printingSizeChange(language, "uni-bi-trigram"))
                QuadriFivegramRelativeFrequencyLookup.fromJson(language)
                    .writeBinary(resourcesDirectory, language, printingSizeChange(language, "quadri-fivegram"))
            }
        }.awaitAll()
    }
}

private fun printingSizeChange(language: Language, name: String): (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit {
    return { oldSizeBytes: Long?, newSizeBytes: Long ->
        if (oldSizeBytes == null) {
            println("NEW: ${language.isoCode639_1} $name ${formatFileSize(newSizeBytes, false)}")
        } else if (oldSizeBytes != newSizeBytes) {
            val sizeDiff = newSizeBytes - oldSizeBytes
            val sizeDiffStr = formatFileSize(sizeDiff, true)
            val percentage = String.format(Locale.ENGLISH, "%+.1f", (sizeDiff / oldSizeBytes.toDouble()) * 100)
            println("CHANGE: ${language.isoCode639_1} $name $sizeDiffStr ($percentage%)")
        }
    }
}

private fun formatFileSize(sizeBytes: Long, addSign: Boolean): String {
    if (abs(sizeBytes) < 1024) {
        return if (addSign && sizeBytes > 0) {
            "+$sizeBytes B"
        } else {
            // Only adds sign for negative values
            "$sizeBytes B"
        }
    }

    // Kilo, Mega, Giga, Tera, Peta, Exa
    val prefixes = "KMGTPE"
    var convertedSize = sizeBytes
    for (i in prefixes.indices) {
        val preConvertedSize = convertedSize
        convertedSize /= 1024
        if (abs(convertedSize) < 1024 || i  >= prefixes.length - 1) {
            val pattern = if (addSign) "%+.2f" else "%.2f"
            val formatted = String.format(Locale.ENGLISH, pattern, preConvertedSize / 1024.0)
            return "$formatted ${prefixes[i]}iB"
        }
    }
    throw AssertionError("unreachable")
}
