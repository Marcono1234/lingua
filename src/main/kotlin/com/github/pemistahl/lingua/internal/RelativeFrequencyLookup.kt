package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.squareup.moshi.JsonReader
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
import kotlin.math.min

/*
 * Currently as part of input text preprocessing it is lower cased and ngrams
 * containing non-letters are skipped. Therefore instead of encoding Unicode
 * code points, maintain a map (in form of an unsigned ShortArray) from code
 * point to 'letter index'; this allows more compact model storage.
 *
 * However, because Java versions adhere to different Unicode standard versions
 * write the 'letter index map' to file to get consistent results regardless
 * of Java version.
 */
private const val LETTER_INDEX_MAP_RESOURCE_NAME = "/letter-index-map.bin"
private const val NO_LETTER_INDEX = 65535 // UShort.MAX_VALUE

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

private data class MinIndexAndNgrams(
    val minLetterIndex: Int,
    /** Mapping from ngram to [encoded frequency][encodeFrequency] */
    val ngramsMap: Object2IntOpenHashMap<String>
)

private fun fromJson(language: Language, letterIndexMap: ShortArray, jsonStream: InputStream): MinIndexAndNgrams {
    val jsonReader = JsonReader.of(jsonStream.source().buffer())
    jsonReader.beginObject()

    var isLanguageMissing = true
    var ngramsMap: Object2IntOpenHashMap<String>? = null
    var minLetterIndex = NO_LETTER_INDEX

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
                            ngram.chars().forEach {
                                minLetterIndex = min(minLetterIndex, letterIndexMap[it].toUShort().toInt())
                            }
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
    return MinIndexAndNgrams(minLetterIndex, ngramsMap)
}

private fun fromJson(language: Language, letterIndexMap: ShortArray, jsonName: String): MinIndexAndNgrams {
    val resourcePath = "/language-models/${language.isoCode639_1}/$jsonName"
    openResourceInputStream(resourcePath).use {
        return fromJson(language, letterIndexMap, it)
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

/**
 * Frequency lookup for uni-, bi- and trigrams.
 */
internal class UniBiTrigramRelativeFrequencyLookup private constructor(
    private val letterIndexMap: ShortArray,
    private val unigramBaseIndex: Int,
    private val unigramsAsByte: ImmutableByte2IntMap,
    private val unigramsAsShort: ImmutableShort2IntMap,
    private val bigramBaseIndex: Int,
    private val bigramsAsShort: ImmutableShort2IntMap,
    private val bigramsAsInt: ImmutableInt2IntMap,
    private val trigramBaseIndex: Int,
    private val trigramsAsInt: ImmutableInt2IntMap,
    private val trigramsAsLong: ImmutableLong2IntMap,
    // Temporary builders; TODO: solve this in a cleaner way
    private val unigramsAsByteBuilder: ImmutableByte2IntMap.Builder = ImmutableByte2IntMap.Builder(),
    private val unigramsAsShortBuilder: ImmutableShort2IntMap.Builder = ImmutableShort2IntMap.Builder(),
    private val bigramsAsShortBuilder: ImmutableShort2IntMap.Builder = ImmutableShort2IntMap.Builder(),
    private val bigramsAsIntBuilder: ImmutableInt2IntMap.Builder = ImmutableInt2IntMap.Builder(),
    private val trigramsAsIntBuilder: ImmutableInt2IntMap.Builder = ImmutableInt2IntMap.Builder(),
    private val trigramsAsLongBuilder: ImmutableLong2IntMap.Builder = ImmutableLong2IntMap.Builder(),
) {
    companion object {
        /**
         * Number of bits used for encoding the offset for the 2nd and 3rd char compared
         * to the 1st one.
         */
        private const val TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR = 10
        private const val TRIGRAM_AS_INT_OFFSET_BIT_MASK = (1 shl TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR) - 1

        /**
         * Number of bits used for encoding the code point value of the 1st char (the "base char").
         */
        private const val TRIGRAM_AS_INT_BASE_CHAR_BITS = Int.SIZE_BITS - 2 * TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR

        fun fromJson(language: Language, letterIndexMap: ShortArray): UniBiTrigramRelativeFrequencyLookup {
            val (unigramBaseIndex, unigrams) = fromJson(language, letterIndexMap, "unigrams.json")
            val (bigramBaseIndex, bigrams) = fromJson(language, letterIndexMap, "bigrams.json")
            val (trigramBaseIndex, trigrams) = fromJson(language, letterIndexMap, "trigrams.json")

            val lookup = UniBiTrigramRelativeFrequencyLookup(
                letterIndexMap,
                unigramBaseIndex,
                bigramBaseIndex,
                trigramBaseIndex
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

        fun fromBinary(language: Language, letterIndexMap: ShortArray): UniBiTrigramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val unigramBaseIndex = it.readUnsignedShort()
                val bigramBaseIndex = it.readUnsignedShort()
                val trigramBaseIndex = it.readUnsignedShort()

                val unigramsAsByte = ImmutableByte2IntMap.fromBinary(it)
                val unigramsAsShort = ImmutableShort2IntMap.fromBinary(it)

                val bigramsAsShort = ImmutableShort2IntMap.fromBinary(it)
                val bigramsAsInt = ImmutableInt2IntMap.fromBinary(it)

                val trigramsAsInt = ImmutableInt2IntMap.fromBinary(it)
                val trigramsAsLong = ImmutableLong2IntMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return UniBiTrigramRelativeFrequencyLookup(
                    letterIndexMap,
                    unigramBaseIndex,
                    unigramsAsByte,
                    unigramsAsShort,
                    bigramBaseIndex,
                    bigramsAsShort,
                    bigramsAsInt,
                    trigramBaseIndex,
                    trigramsAsInt,
                    trigramsAsLong
                )
            }
        }
    }

    private constructor(
        letterIndexMap: ShortArray,
        unigramBaseIndex: Int,
        bigramBaseIndex: Int,
        trigramBaseIndex: Int
    ) : this(
        letterIndexMap,
        unigramBaseIndex,
        ImmutableByte2IntMap(ByteArray(0), IntArray(0)),
        ImmutableShort2IntMap(ShortArray(0), IntArray(0)),
        bigramBaseIndex,
        ImmutableShort2IntMap(ShortArray(0), IntArray(0)),
        ImmutableInt2IntMap(IntArray(0), IntArray(0)),
        trigramBaseIndex,
        ImmutableInt2IntMap(IntArray(0), IntArray(0)),
        ImmutableLong2IntMap(LongArray(0), IntArray(0))
    )

    private fun letterIndex(char: Int, baseIndex: Int): Int {
        val index = letterIndexMap[char].toUShort().toInt()
        return if (index == NO_LETTER_INDEX) {
            NO_LETTER_INDEX
        } else {
            val relativeIndex = index - baseIndex
            return if (relativeIndex < 0) NO_LETTER_INDEX else relativeIndex
        }
    }
    private fun unigramLetterIndex(char: Int) = letterIndex(char, unigramBaseIndex)
    private fun bigramLetterIndex(char: Int) = letterIndex(char, bigramBaseIndex)
    private fun trigramLetterIndex(char: Int) = letterIndex(char, trigramBaseIndex)

    private inline fun <R> useEncodedUnigram(
        char0: Int,
        asByte: (encodedNgram: Byte) -> R,
        asShort: (encodedNgram: Short) -> R
    ): R {
        val letterIndex0 = unigramLetterIndex(char0)
        return if (letterIndex0 <= 255) asByte(letterIndex0.toByte()) else asShort(char0.toShort())
    }

    private inline fun <R> String.useEncodedUnigram(
        asByte: (encodedNgram: Byte) -> R,
        asShort: (encodedNgram: Short) -> R
    ): R = useEncodedUnigram(this[0].code, asByte, asShort)

    private inline fun <R> useEncodedBigram(
        char0: Int,
        char1: Int,
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R
    ): R {
        val letterIndex0 = bigramLetterIndex(char0)
        val letterIndex1 = bigramLetterIndex(char1)
        return if (letterIndex0 <= 255 && letterIndex1 <= 255) {
            val encoded = letterIndex0 or (letterIndex1 shl 8)
            asShort(encoded.toShort())
        } else {
            val encoded = char0 or (char1 shl 16)
            asInt(encoded)
        }
    }

    private inline fun <R> String.useEncodedBigram(
        asShort: (encodedNgram: Short) -> R,
        asInt: (encodedNgram: Int) -> R
    ): R = useEncodedBigram(this[0].code, this[1].code, asShort, asInt)

    private inline fun <R> useEncodedTrigram(
        char0: Int,
        char1: Int,
        char2: Int,
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R
    ): R {
        /*
         * Trigram as Int is encoded by writing absolute char value of first char (index 0)
         * followed by the signed offsets of the other chars compared to the first char.
         *
         * This allows encoding trigrams where some or all chars would not fit
         * within Int.SIZE_BITS / 3, but all of the char values are close together.
         */

        val letterIndex0 = trigramLetterIndex(char0)
        val letterIndex1 = trigramLetterIndex(char1)
        val letterIndex2 = trigramLetterIndex(char2)
        val diff1 = letterIndex1 - letterIndex0
        val diff2 = letterIndex2 - letterIndex0

        // (2^x) - 1
        val maxOffset = (1 shl (TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR - 1)) - 1
        // -2^x
        val minOffset = -maxOffset - 1

        return if (
            letterIndex0 < (1 shl TRIGRAM_AS_INT_BASE_CHAR_BITS)
            && diff1 in minOffset..maxOffset
            && diff2 in minOffset..maxOffset
        ) {
            val encoded = (
                letterIndex0
                // AND with bitmask to remove leading 1s for negative values
                or ((diff1 and TRIGRAM_AS_INT_OFFSET_BIT_MASK) shl TRIGRAM_AS_INT_BASE_CHAR_BITS)
                or ((diff2 and TRIGRAM_AS_INT_OFFSET_BIT_MASK) shl (TRIGRAM_AS_INT_BASE_CHAR_BITS + TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR))
            )
            asInt(encoded)
        } else {
            val encoded = (
                char0.toLong()
                or (char1.toLong() shl 16)
                or (char2.toLong() shl 32)
            )
            asLong(encoded)
        }
    }

    private inline fun <R> String.useEncodedTrigram(
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R
    ): R = useEncodedTrigram(this[0].code, this[1].code, this[2].code, asInt, asLong)

    private fun putUnigramFrequency(unigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$unigram'")
        }
        if (unigram.length != 1) {
            throw IllegalArgumentException("Invalid ngram length ${unigram.length}")
        }

        unigram.useEncodedUnigram(
            { unigramsAsByteBuilder.add(it, encodedFrequency) },
            { unigramsAsShortBuilder.add(it, encodedFrequency) }
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
            { bigramsAsIntBuilder.add(it, encodedFrequency) }
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
            { trigramsAsIntBuilder.add(it, encodedFrequency) },
            { trigramsAsLongBuilder.add(it, encodedFrequency) }
        )
    }

    private fun finishCreation(): UniBiTrigramRelativeFrequencyLookup {
        return UniBiTrigramRelativeFrequencyLookup(
            letterIndexMap,
            unigramBaseIndex,
            unigramsAsByteBuilder.build(),
            unigramsAsShortBuilder.build(),
            bigramBaseIndex,
            bigramsAsShortBuilder.build(),
            bigramsAsIntBuilder.build(),
            trigramBaseIndex,
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
                { unigramsAsShort.get(it) }
            )
            2 -> useEncodedBigram(
                char0, char1,
                { bigramsAsShort.get(it) },
                { bigramsAsInt.get(it) }
            )
            3 -> useEncodedTrigram(
                char0, char1, char2,
                { trigramsAsInt.get(it) },
                { trigramsAsLong.get(it) }
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
            dataOut.writeShort(unigramBaseIndex)
            dataOut.writeShort(bigramBaseIndex)
            dataOut.writeShort(trigramBaseIndex)

            unigramsAsByte.writeBinary(dataOut)
            unigramsAsShort.writeBinary(dataOut)

            bigramsAsShort.writeBinary(dataOut)
            bigramsAsInt.writeBinary(dataOut)

            trigramsAsInt.writeBinary(dataOut)
            trigramsAsLong.writeBinary(dataOut)
        }
    }
}

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal class QuadriFivegramRelativeFrequencyLookup private constructor(
    private val letterIndexMap: ShortArray,
    private val quadrigramBaseIndex: Int,
    private val quadrigramsAsInt: ImmutableInt2IntMap,
    private val quadrigramsAsLong: ImmutableLong2IntMap,
    private val fivegramBaseIndex: Int,
    private val fivegramsAsInt: ImmutableInt2IntMap,
    private val fivegramsAsLong: ImmutableLong2IntMap,
    private val fivegramsAsObject: ImmutableFivegram2IntMap,
    // Temporary builders; TODO: solve this in a cleaner way
    private val quadrigramsAsIntBuilder: ImmutableInt2IntMap.Builder = ImmutableInt2IntMap.Builder(),
    private val quadrigramsAsLongBuilder: ImmutableLong2IntMap.Builder = ImmutableLong2IntMap.Builder(),
    private val fivegramsAsIntBuilder: ImmutableInt2IntMap.Builder = ImmutableInt2IntMap.Builder(),
    private val fivegramsAsLongBuilder: ImmutableLong2IntMap.Builder = ImmutableLong2IntMap.Builder(),
    private val fivegramsAsObjectBuilder: ImmutableFivegram2IntMap.Builder = ImmutableFivegram2IntMap.Builder(),
) {
    companion object {
        val empty = QuadriFivegramRelativeFrequencyLookup(shortArrayOf(), 0, 0)

        /**
         * Number of bits used for encoding the code point value of the first char (the "base char").
         */
        private const val FIVEGRAM_BASE_CHAR_BITS = Char.SIZE_BITS // Support encoding all chars

        /**
         * Number of bits per fivegram char used to encode the signed offset compared to
         * the first char (at index 0).
         */
        private const val FIVEGRAM_OFFSET_BITS_PER_CHAR = (Long.SIZE_BITS - FIVEGRAM_BASE_CHAR_BITS) / 4
        private const val FIVEGRAM_OFFSET_BIT_MASK = (1L shl FIVEGRAM_OFFSET_BITS_PER_CHAR) - 1

        fun fromJson(language: Language, letterIndexMap: ShortArray): QuadriFivegramRelativeFrequencyLookup {
            val (quadrigramBaseIndex, quadrigrams) = fromJson(language, letterIndexMap, "quadrigrams.json")
            val (fivegramBaseIndex, fivegrams) = fromJson(language, letterIndexMap, "fivegrams.json")

            val lookup = QuadriFivegramRelativeFrequencyLookup(letterIndexMap, quadrigramBaseIndex, fivegramBaseIndex)
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

        fun fromBinary(language: Language, letterIndexMap: ShortArray): QuadriFivegramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val quadrigramBaseIndex = it.readUnsignedShort()
                val fivegramBaseIndex = it.readUnsignedShort()

                val quadrigramsAsInt = ImmutableInt2IntMap.fromBinary(it)
                val quadrigramsAsLong = ImmutableLong2IntMap.fromBinary(it)

                val fivegramsAsInt = ImmutableInt2IntMap.fromBinary(it)
                val fivegramsAsLong = ImmutableLong2IntMap.fromBinary(it)
                val fivegramsAsObject = ImmutableFivegram2IntMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return QuadriFivegramRelativeFrequencyLookup(
                    letterIndexMap,
                    quadrigramBaseIndex,
                    quadrigramsAsInt,
                    quadrigramsAsLong,
                    fivegramBaseIndex,
                    fivegramsAsInt,
                    fivegramsAsLong,
                    fivegramsAsObject
                )
            }
        }
    }

    private constructor(letterIndexMap: ShortArray, quadrigramBaseIndex: Int, fivegramBaseIndex: Int) : this(
        letterIndexMap,
        quadrigramBaseIndex,
        ImmutableInt2IntMap(IntArray(0), IntArray(0)),
        ImmutableLong2IntMap(LongArray(0), IntArray(0)),
        fivegramBaseIndex,
        ImmutableInt2IntMap(IntArray(0), IntArray(0)),
        ImmutableLong2IntMap(LongArray(0), IntArray(0)),
        ImmutableFivegram2IntMap(emptyArray(), IntArray(0))
    )

    private fun letterIndex(char: Int, baseIndex: Int): Int {
        val index = letterIndexMap[char].toUShort().toInt()
        return if (index == NO_LETTER_INDEX) {
            NO_LETTER_INDEX
        } else {
            val relativeIndex = index - baseIndex
            return if (relativeIndex < 0) NO_LETTER_INDEX else relativeIndex
        }
    }
    private fun quadrigramLetterIndex(char: Int) = letterIndex(char, quadrigramBaseIndex)
    private fun fivegramLetterIndex(char: Int) = letterIndex(char, fivegramBaseIndex)

    private inline fun <R> String.useEncodedQuadrigram(
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R
    ): R {
        val char0 = this[0].code
        val char1 = this[1].code
        val char2 = this[2].code
        val char3 = this[3].code
        val letterIndex0 = quadrigramLetterIndex(char0)
        val letterIndex1 = quadrigramLetterIndex(char1)
        val letterIndex2 = quadrigramLetterIndex(char2)
        val letterIndex3 = quadrigramLetterIndex(char3)

        return if (
            letterIndex0 <= 255
            && letterIndex1 <= 255
            && letterIndex2 <= 255
            && letterIndex3 <= 255
        ) {
            val encoded = (
                letterIndex0
                or (letterIndex1 shl 8)
                or (letterIndex2 shl 16)
                or (letterIndex3 shl 24)
            )
            asInt(encoded)
        } else {
            val encoded = (
                char0.toLong()
                or (char1.toLong() shl 16)
                or (char2.toLong() shl 32)
                or (char3.toLong() shl 48)
            )
            asLong(encoded)
        }
    }

    private inline fun <R> String.useEncodedFivegram(
        asInt: (encodedNgram: Int) -> R,
        asLong: (encodedNgram: Long) -> R,
        asObject: (encodedNgram: String) -> R
    ): R {
        val letterIndex0 = fivegramLetterIndex(this[0].code)
        val letterIndex1 = fivegramLetterIndex(this[1].code)
        val letterIndex2 = fivegramLetterIndex(this[2].code)
        val letterIndex3 = fivegramLetterIndex(this[3].code)
        val letterIndex4 = fivegramLetterIndex(this[4].code)
        val diff1 = letterIndex1 - letterIndex0
        val diff2 = letterIndex2 - letterIndex0
        val diff3 = letterIndex3 - letterIndex0
        val diff4 = letterIndex4 - letterIndex0

        // (2^x) - 1
        val maxOffset = (1 shl (FIVEGRAM_OFFSET_BITS_PER_CHAR - 1)) - 1
        // -2^x
        val minOffset = -maxOffset - 1

        /*
         * Fivegram as Int is encoded by storing absolute values for each char;
         * first two get 7 bits each, last three get 6 bits (2*7 + 3*6 = 32)
         */
        return if (
            letterIndex0 < (1 shl 7)
            && letterIndex1 < (1 shl 7)
            && letterIndex2 < (1 shl 6)
            && letterIndex3 < (1 shl 6)
            && letterIndex4 < (1 shl 6)
        ) {
            val encoded = (
                letterIndex0
                or (letterIndex1 shl 7)
                or (letterIndex2 shl (7 * 2))
                or (letterIndex3 shl (7 * 2 + 6))
                or (letterIndex4 shl (7 * 2 + 6 * 2))
            )
            asInt(encoded)
        }
        /*
         * Fivegram as Long is encoded by writing absolute char value of first
         * char (index 0) using 16 bits followed by the signed offsets of the other
         * chars compared to the first char.
         *
         * This allows encoding fivegrams where some or all chars would not fit
         * within Long.SIZE_BITS / 5, but all of the char values are close together.
         */
        else if (
            // Make sure char0 has a letter index
            letterIndex0 != NO_LETTER_INDEX
            && (diff1 in minOffset..maxOffset)
            && (diff2 in minOffset..maxOffset)
            && (diff3 in minOffset..maxOffset)
            && (diff4 in minOffset..maxOffset)
        ) {
            val encoded = (
                letterIndex0.toLong()
                // AND with bitmask to remove leading 1s for negative values
                or ((diff1.toLong() and FIVEGRAM_OFFSET_BIT_MASK) shl FIVEGRAM_BASE_CHAR_BITS)
                or ((diff2.toLong() and FIVEGRAM_OFFSET_BIT_MASK) shl (FIVEGRAM_BASE_CHAR_BITS + FIVEGRAM_OFFSET_BITS_PER_CHAR))
                or ((diff3.toLong() and FIVEGRAM_OFFSET_BIT_MASK) shl (FIVEGRAM_BASE_CHAR_BITS + FIVEGRAM_OFFSET_BITS_PER_CHAR * 2))
                or ((diff4.toLong() and FIVEGRAM_OFFSET_BIT_MASK) shl (FIVEGRAM_BASE_CHAR_BITS + FIVEGRAM_OFFSET_BITS_PER_CHAR * 3))
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
            { quadrigramsAsIntBuilder.add(it, encodedFrequency) },
            { quadrigramsAsLongBuilder.add(it, encodedFrequency) }
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
            { fivegramsAsObjectBuilder.add(it, encodedFrequency) }
        )
    }

    private fun finishCreation(): QuadriFivegramRelativeFrequencyLookup {
        return QuadriFivegramRelativeFrequencyLookup(
            letterIndexMap,
            quadrigramBaseIndex,
            quadrigramsAsIntBuilder.build(),
            quadrigramsAsLongBuilder.build(),
            fivegramBaseIndex,
            fivegramsAsIntBuilder.build(),
            fivegramsAsLongBuilder.build(),
            fivegramsAsObjectBuilder.build()
        )
    }

    fun getFrequency(ngram: ObjectNgram): Double {
        val ngramStr = ngram.value

        return decodeFrequency(when (ngramStr.length) {
            4 -> ngramStr.useEncodedQuadrigram(
                { quadrigramsAsInt.get(it) },
                { quadrigramsAsLong.get(it) }
            )
            5 -> ngramStr.useEncodedFivegram(
                { fivegramsAsInt.get(it) },
                { fivegramsAsLong.get(it) },
                { fivegramsAsObject.get(it) }
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
            dataOut.writeShort(quadrigramBaseIndex)
            dataOut.writeShort(fivegramBaseIndex)

            quadrigramsAsInt.writeBinary(dataOut)
            quadrigramsAsLong.writeBinary(dataOut)

            fivegramsAsInt.writeBinary(dataOut)
            fivegramsAsLong.writeBinary(dataOut)
            fivegramsAsObject.writeBinary(dataOut)
        }
    }
}

internal fun loadLetterIndexMap(): ShortArray {
    openBinaryDataInput(LETTER_INDEX_MAP_RESOURCE_NAME).use { dataIn ->
        return ShortArray(Char.MAX_VALUE.code) {
            dataIn.readShort()
        }
    }
}

internal fun writeBinaryModels(resourcesDirectory: Path) {
    val letterIndexMap = ShortArray(Char.MAX_VALUE.code) { NO_LETTER_INDEX.toShort() }
    var index = 0.toShort()
    for (c in '0'..Char.MAX_VALUE) {
        /*
         * During language detection ngrams are converted to lower case and filtered for letters;
         * therefore only include letters here.
         * However, there are letters which are neither lower nor upper case (however some of
         * these also have lower case versions, such as `U+01C5`), and also for some letters
         * there is no lower case representation, therefore simply checking `c.isLowerCase()`
         * is not enough.
         */
        if (c.isLetter() && (c.isLowerCase() || c.lowercaseChar() == c)) {
            letterIndexMap[c.code] = index
            index++
        }
    }

    runBlocking(Dispatchers.IO) {
        openBinaryDataOutput(resourcesDirectory, LETTER_INDEX_MAP_RESOURCE_NAME) { _, _ -> }.use { dataOut ->
            letterIndexMap.forEach { dataOut.writeShort(it.toInt()) }
        }
    }

    runBlocking {
        Language.all().map { language ->
            async(Dispatchers.IO) {
                UniBiTrigramRelativeFrequencyLookup.fromJson(language, letterIndexMap)
                    .writeBinary(resourcesDirectory, language, printingSizeChange(language, "uni-bi-trigram"))
                QuadriFivegramRelativeFrequencyLookup.fromJson(language, letterIndexMap)
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
