package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.squareup.moshi.JsonReader
import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import it.unimi.dsi.fastutil.bytes.ByteConsumer
import it.unimi.dsi.fastutil.bytes.ByteList
import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap
import it.unimi.dsi.fastutil.chars.CharArrayList
import it.unimi.dsi.fastutil.chars.CharConsumer
import it.unimi.dsi.fastutil.chars.CharList
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap
import it.unimi.dsi.fastutil.shorts.ShortArrayList
import it.unimi.dsi.fastutil.shorts.ShortConsumer
import it.unimi.dsi.fastutil.shorts.ShortList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.source
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.IntConsumer
import java.util.function.LongConsumer
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

private fun openBinaryDataOutput(resourcesDirectory: Path, resourcePath: String): DataOutputStream {
    val file = resourcesDirectory.resolve(resourcePath.removePrefix("/"))
    Files.createDirectories(file.parent)

    return DataOutputStream(Files.newOutputStream(file).buffered())
}

private data class MinIndexAndNgrams(
    val minLetterIndex: Int,
    /** Mapping from ngram to [encoded frequency][encodeFrequency] */
    val ngramsMap: Object2IntOpenHashMap<String>
)

private val jsonModelNameOptions = JsonReader.Options.of("language", "ngrams")

private fun fromJson(language: Language, letterIndexMap: ShortArray, jsonStream: InputStream): MinIndexAndNgrams {
    val jsonReader = JsonReader.of(jsonStream.source().buffer())
    jsonReader.beginObject()

    var isLanguageMissing = true
    var ngramsMap: Object2IntOpenHashMap<String>? = null
    var minLetterIndex = NO_LETTER_INDEX

    while (jsonReader.hasNext()) {
        when (jsonReader.selectName(jsonModelNameOptions)) {
            -1 -> throw IllegalArgumentException("Unknown name '${jsonReader.nextName()}' at ${jsonReader.path}")
            0 -> if (isLanguageMissing) {
                isLanguageMissing = false
                if (Language.valueOf(jsonReader.nextString()) != language) {
                    throw IllegalArgumentException("JSON file is for wrong language")
                }
            } else throw IllegalArgumentException("Duplicate language at ${jsonReader.path}")
            1 -> if (ngramsMap == null) {
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

private fun Byte2IntOpenHashMap.reverse(): Int2ObjectOpenHashMap<ByteList> {
    val reversed = Int2ObjectOpenHashMap<ByteList>(10, 0.90f)
    byte2IntEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.intValue, Int2ObjectFunction { ByteArrayList() }).add(it.byteKey)
    }
    return reversed
}

private fun Char2IntOpenHashMap.reverse(): Int2ObjectOpenHashMap<CharList> {
    val reversed = Int2ObjectOpenHashMap<CharList>(10, 0.90f)
    char2IntEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.intValue, Int2ObjectFunction { CharArrayList() }).add(it.charKey)
    }
    return reversed
}

private fun Short2IntOpenHashMap.reverse(): Int2ObjectOpenHashMap<ShortList> {
    val reversed = Int2ObjectOpenHashMap<ShortList>(10, 0.90f)
    short2IntEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.intValue, Int2ObjectFunction { ShortArrayList() }).add(it.shortKey)
    }
    return reversed
}

private fun Int2IntOpenHashMap.reverse(): Int2ObjectOpenHashMap<IntList> {
    val reversed = Int2ObjectOpenHashMap<IntList>(10, 0.90f)
    int2IntEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.intValue, Int2ObjectFunction { IntArrayList() }).add(it.intKey)
    }
    return reversed
}

private fun Long2IntOpenHashMap.reverse(): Int2ObjectOpenHashMap<LongList> {
    val reversed = Int2ObjectOpenHashMap<LongList>(10, 0.90f)
    long2IntEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.intValue, Int2ObjectFunction { LongArrayList() }).add(it.longKey)
    }
    return reversed
}

private fun <T> Object2IntOpenHashMap<T>.reverse(): Int2ObjectOpenHashMap<out List<T>> {
    val reversed = Int2ObjectOpenHashMap<MutableList<T>>(10, 0.90f)
    object2IntEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.intValue, Int2ObjectFunction { mutableListOf() }).add(it.key)
    }
    return reversed
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

private typealias FrequencyWriter = () -> Unit

/**
 * Encodes the ngrams count and frequency. Returns a frequency writer which
 * has to be invoked before writing each ngram; if the frequency has not been
 * encoded by this method the frequency writer will write it.
 */
private fun encodeNgramCountAndFrequency(
    entry: Int2ObjectMap.Entry<out Collection<*>>,
    dataOut: DataOutput
): FrequencyWriter {
    /*
     * If count <= 3: Only write frequency
     * Else: Write 32-bit 0 (to differentiate it from frequency), followed by count, followed by frequency
     *
     * This encoding saves multiple kilobytes per model file.
     */

    val ngramCount = entry.value.size
    val encodedFrequency = entry.intKey
    assert(encodedFrequency != 0)
    if (ngramCount > 3) {
        // Write 32-bit 0 to indicate that ngram count follows
        dataOut.writeInt(0)
        dataOut.writeInt(ngramCount)
        dataOut.writeInt(encodedFrequency)
        // Frequency writer has nothing to do, frequency is already encoded
        return {}
    } else {
        // For each ngram write frequency
        return { dataOut.writeInt(encodedFrequency) }
    }
}

// Use value class to avoid unnecessary object allocations while reading models
@JvmInline
private value class NgramCountAndFrequency(val encoded: Long) {
    constructor(count: Int, encodedFrequency: Int) : this(
        (count.toLong() shl 32) or encodedFrequency.toUInt().toLong()
    )

    fun getCount() = (encoded ushr 32).toInt()
    fun getEncodedFrequency() = encoded.toInt()

    operator fun component1() = getCount()
    operator fun component2() = getEncodedFrequency()
}
private fun decodeNgramCountAndFrequency(dataIn: DataInput): NgramCountAndFrequency {
    val first32Bits = dataIn.readInt()
    val count: Int
    val encodedFrequency: Int

    if (first32Bits == 0) {
        count = dataIn.readInt()
        encodedFrequency = dataIn.readInt()
    } else {
        count = 1
        encodedFrequency = first32Bits
    }
    return NgramCountAndFrequency(count, encodedFrequency)
}

private const val loadFactor = 0.9f
private const val initialCapacity = 16

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
    private val unigramsAsByte: Byte2IntOpenHashMap,
    private val unigramsAsChar: Char2IntOpenHashMap,
    private val bigramBaseIndex: Int,
    private val bigramsAsShort: Short2IntOpenHashMap,
    private val bigramsAsInt: Int2IntOpenHashMap,
    private val trigramBaseIndex: Int,
    private val trigramsAsInt: Int2IntOpenHashMap,
    private val trigramsAsLong: Long2IntOpenHashMap
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
            lookup.finishCreation()
            return lookup
        }

        private fun getBinaryModelResourceName(language: Language): String {
            return getBinaryModelResourceName(language, "uni-bi-trigrams.bin")
        }

        fun fromBinary(language: Language, letterIndexMap: ShortArray): UniBiTrigramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val unigramBaseIndex = it.readUnsignedShort()
                val bigramBaseIndex = it.readUnsignedShort()
                val trigramBaseIndex = it.readUnsignedShort()

                var unigramsAsByteCount = it.readInt()
                val unigramsAsByte = Byte2IntOpenHashMap(unigramsAsByteCount, loadFactor)
                while (unigramsAsByteCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        unigramsAsByte.put(it.readByte(), frequency)
                    }
                    unigramsAsByteCount -= count
                }

                var unigramsAsCharCount = it.readInt()
                val unigramsAsChar = Char2IntOpenHashMap(unigramsAsCharCount, loadFactor)
                while (unigramsAsCharCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        unigramsAsChar.put(it.readChar(), frequency)
                    }
                    unigramsAsCharCount -= count
                }

                var bigramsAsShortCount = it.readInt()
                val bigramsAsShort = Short2IntOpenHashMap(bigramsAsShortCount, loadFactor)
                while (bigramsAsShortCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        bigramsAsShort.put(it.readShort(), frequency)
                    }
                    bigramsAsShortCount -= count
                }

                var bigramsAsIntCount = it.readInt()
                val bigramsAsInt = Int2IntOpenHashMap(bigramsAsIntCount, loadFactor)
                while (bigramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        bigramsAsInt.put(it.readInt(), frequency)
                    }
                    bigramsAsIntCount -= count
                }

                var trigramsAsIntCount = it.readInt()
                val trigramsAsInt = Int2IntOpenHashMap(trigramsAsIntCount, loadFactor)
                while (trigramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        trigramsAsInt.put(it.readInt(), frequency)
                    }
                    trigramsAsIntCount -= count
                }

                var trigramsAsLongCount = it.readInt()
                val trigramsAsLong = Long2IntOpenHashMap(trigramsAsLongCount, loadFactor)
                while (trigramsAsLongCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        trigramsAsLong.put(it.readLong(), frequency)
                    }
                    trigramsAsLongCount -= count
                }

                // Should have reached end of data
                assert(it.read() == -1)

                return UniBiTrigramRelativeFrequencyLookup(
                    letterIndexMap,
                    unigramBaseIndex,
                    unigramsAsByte,
                    unigramsAsChar,
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
        Byte2IntOpenHashMap(initialCapacity, loadFactor),
        Char2IntOpenHashMap(initialCapacity, loadFactor),
        bigramBaseIndex,
        Short2IntOpenHashMap(initialCapacity, loadFactor),
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        trigramBaseIndex,
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        Long2IntOpenHashMap(initialCapacity, loadFactor)
    )

    private fun letterIndex(char: Int, baseIndex: Int): Int {
        val index = letterIndexMap[char].toUShort().toInt()
        return if (index == NO_LETTER_INDEX) index else index - baseIndex
    }
    private fun unigramLetterIndex(char: Int) = letterIndex(char, unigramBaseIndex)
    private fun bigramLetterIndex(char: Int) = letterIndex(char, bigramBaseIndex)
    private fun trigramLetterIndex(char: Int) = letterIndex(char, trigramBaseIndex)

    private inline fun <R> useEncodedUnigram(
        char0: Int,
        asByte: (encodedNgram: Byte) -> R,
        asChar: (encodedNgram: Char) -> R
    ): R {
        val letterIndex0 = unigramLetterIndex(char0)
        return if (letterIndex0 <= 255) asByte(letterIndex0.toByte()) else asChar(char0.toChar())
    }

    private inline fun <R> String.useEncodedUnigram(
        asByte: (encodedNgram: Byte) -> R,
        asChar: (encodedNgram: Char) -> R
    ): R = useEncodedUnigram(this[0].code, asByte, asChar)

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

        val old = unigram.useEncodedUnigram(
            { unigramsAsByte.put(it, encodedFrequency) },
            { unigramsAsChar.put(it, encodedFrequency) }
        )
        if (old != 0) {
            throw AssertionError("Colliding encoding for '$unigram'")
        }
    }

    private fun putBigramFrequency(bigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$bigram'")
        }
        if (bigram.length != 2) {
            throw IllegalArgumentException("Invalid ngram length ${bigram.length}")
        }

        val old = bigram.useEncodedBigram(
            { bigramsAsShort.put(it, encodedFrequency) },
            { bigramsAsInt.put(it, encodedFrequency) }
        )
        if (old != 0) {
            throw AssertionError("Colliding encoding for '$bigram'")
        }
    }

    private fun putTrigramFrequency(trigram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$trigram'")
        }
        if (trigram.length != 3) {
            throw IllegalArgumentException("Invalid ngram length ${trigram.length}")
        }

        val old = trigram.useEncodedTrigram(
            { trigramsAsInt.put(it, encodedFrequency) },
            { trigramsAsLong.put(it, encodedFrequency) }
        )
        if (old != 0) {
            throw AssertionError("Colliding encoding for '$trigram'")
        }
    }

    private fun finishCreation() {
        unigramsAsByte.trim()
        unigramsAsChar.trim()

        bigramsAsShort.trim()
        bigramsAsInt.trim()

        trigramsAsInt.trim()
        trigramsAsLong.trim()
    }

    fun getFrequency(ngram: PrimitiveNgram): Double {
        val (length, char0, char1, char2) = ngram
        return decodeFrequency(when (length) {
            1 -> useEncodedUnigram(
                char0,
                { unigramsAsByte.get(it) },
                { unigramsAsChar.get(it) }
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
    fun writeBinary(resourcesDirectory: Path, language: Language) {
        val resourceName = getBinaryModelResourceName(language)

        openBinaryDataOutput(resourcesDirectory, resourceName).use { dataOut ->
            dataOut.writeShort(unigramBaseIndex)
            dataOut.writeShort(bigramBaseIndex)
            dataOut.writeShort(trigramBaseIndex)

            dataOut.writeInt(unigramsAsByte.size)
            unigramsAsByte.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(ByteConsumer {
                    frequencyWriter()
                    dataOut.writeByte(it.toInt())
                })
            }

            dataOut.writeInt(unigramsAsChar.size)
            unigramsAsChar.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(CharConsumer {
                    frequencyWriter()
                    dataOut.writeChar(it.code)
                })
            }

            dataOut.writeInt(bigramsAsShort.size)
            bigramsAsShort.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(ShortConsumer {
                    frequencyWriter()
                    dataOut.writeShort(it.toInt())
                })
            }

            dataOut.writeInt(bigramsAsInt.size)
            bigramsAsInt.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer {
                    frequencyWriter()
                    dataOut.writeInt(it)
                })
            }

            dataOut.writeInt(trigramsAsInt.size)
            trigramsAsInt.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer {
                    frequencyWriter()
                    dataOut.writeInt(it)
                })
            }

            dataOut.writeInt(trigramsAsLong.size)
            trigramsAsLong.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(LongConsumer {
                    frequencyWriter()
                    dataOut.writeLong(it)
                })
            }
        }
    }
}

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal class QuadriFivegramRelativeFrequencyLookup private constructor(
    private val letterIndexMap: ShortArray,
    private val quadrigramBaseIndex: Int,
    private val quadrigramsAsInt: Int2IntOpenHashMap,
    private val quadrigramsAsLong: Long2IntOpenHashMap,
    private val fivegramBaseIndex: Int,
    private val fivegramsAsInt: Int2IntOpenHashMap,
    private val fivegramsAsLong: Long2IntOpenHashMap,
    private val fivegramsAsObject: Object2IntOpenHashMap<String>
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
            lookup.finishCreation()
            return lookup
        }

        private fun getBinaryModelResourceName(language: Language): String {
            return getBinaryModelResourceName(language, "quadri-fivegrams.bin")
        }

        fun fromBinary(language: Language, letterIndexMap: ShortArray): QuadriFivegramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val quadrigramBaseIndex = it.readUnsignedShort()
                val fivegramBaseIndex = it.readUnsignedShort()

                var quadrigramsAsIntCount = it.readInt()
                val quadrigramsAsInt = Int2IntOpenHashMap(quadrigramsAsIntCount, loadFactor)
                while (quadrigramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        quadrigramsAsInt.put(it.readInt(), frequency)
                    }
                    quadrigramsAsIntCount -= count
                }

                var quadrigramsAsLongCount = it.readInt()
                val quadrigramsAsLong = Long2IntOpenHashMap(quadrigramsAsLongCount, loadFactor)
                while (quadrigramsAsLongCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        quadrigramsAsLong.put(it.readLong(), frequency)
                    }
                    quadrigramsAsLongCount -= count
                }

                var fivegramsAsIntCount = it.readInt()
                val fivegramsAsInt = Int2IntOpenHashMap(fivegramsAsIntCount, loadFactor)
                while (fivegramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        fivegramsAsInt.put(it.readInt(), frequency)
                    }
                    fivegramsAsIntCount -= count
                }

                var fivegramsAsLongCount = it.readInt()
                val fivegramsAsLong = Long2IntOpenHashMap(fivegramsAsLongCount, loadFactor)
                while (fivegramsAsLongCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        fivegramsAsLong.put(it.readLong(), frequency)
                    }
                    fivegramsAsLongCount -= count
                }

                var fivegramsAsObjectCount = it.readInt()
                val fivegramsAsObject = Object2IntOpenHashMap<String>(fivegramsAsObjectCount, loadFactor)
                while (fivegramsAsObjectCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        val charArray = CharArray(5)
                        charArray[0] = it.readChar()
                        charArray[1] = it.readChar()
                        charArray[2] = it.readChar()
                        charArray[3] = it.readChar()
                        charArray[4] = it.readChar()
                        val fivegram = String(charArray)

                        fivegramsAsObject.put(fivegram, frequency)
                    }
                    fivegramsAsObjectCount -= count
                }

                // Should have reached end of data
                assert(it.read() == -1)

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
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        Long2IntOpenHashMap(initialCapacity, loadFactor),
        fivegramBaseIndex,
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        Long2IntOpenHashMap(initialCapacity, loadFactor),
        Object2IntOpenHashMap<String>(initialCapacity, loadFactor)
    )

    private fun letterIndex(char: Int, baseIndex: Int): Int {
        val index = letterIndexMap[char].toUShort().toInt()
        return if (index == NO_LETTER_INDEX) index else index - baseIndex
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

        val old = quadrigram.useEncodedQuadrigram(
            { quadrigramsAsInt.put(it, encodedFrequency) },
            { quadrigramsAsLong.put(it, encodedFrequency) }
        )
        if (old != 0) {
            throw AssertionError("Colliding encoding for '$quadrigram'")
        }
    }

    private fun putFivegramFrequency(fivegram: String, encodedFrequency: Int) {
        if (encodedFrequency == 0) {
            throw AssertionError("Invalid encoded frequency $encodedFrequency for ngram '$fivegram'")
        }
        if (fivegram.length != 5) {
            throw IllegalArgumentException("Invalid ngram length ${fivegram.length}")
        }

        val old = fivegram.useEncodedFivegram(
            { fivegramsAsInt.put(it, encodedFrequency) },
            { fivegramsAsLong.put(it, encodedFrequency) },
            { fivegramsAsObject.put(it, encodedFrequency) }
        )
        if (old != 0) {
            throw AssertionError("Colliding encoding for '$fivegram'")
        }
    }

    private fun finishCreation() {
        quadrigramsAsInt.trim()
        quadrigramsAsLong.trim()

        fivegramsAsInt.trim()
        fivegramsAsLong.trim()
        fivegramsAsObject.trim()
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
                { fivegramsAsObject.getInt(it) }
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
    fun writeBinary(resourcesDirectory: Path, language: Language) {
        val resourceName = getBinaryModelResourceName(language)

        openBinaryDataOutput(resourcesDirectory, resourceName).use { dataOut ->
            dataOut.writeShort(quadrigramBaseIndex)
            dataOut.writeShort(fivegramBaseIndex)

            dataOut.writeInt(quadrigramsAsInt.size)
            quadrigramsAsInt.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer {
                    frequencyWriter()
                    dataOut.writeInt(it)
                })
            }

            dataOut.writeInt(quadrigramsAsLong.size)
            quadrigramsAsLong.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(LongConsumer {
                    frequencyWriter()
                    dataOut.writeLong(it)
                })
            }

            dataOut.writeInt(fivegramsAsInt.size)
            fivegramsAsInt.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer {
                    frequencyWriter()
                    dataOut.writeInt(it)
                })
            }

            dataOut.writeInt(fivegramsAsLong.size)
            fivegramsAsLong.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(LongConsumer {
                    frequencyWriter()
                    dataOut.writeLong(it)
                })
            }

            dataOut.writeInt(fivegramsAsObject.size)
            fivegramsAsObject.reverse().int2ObjectEntrySet().fastForEach { entry ->
                val frequencyWriter = encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach {
                    frequencyWriter()

                    // Write String manually since length is known (= 5) so don't have to encode it
                    dataOut.writeChar(it[0].code)
                    dataOut.writeChar(it[1].code)
                    dataOut.writeChar(it[2].code)
                    dataOut.writeChar(it[3].code)
                    dataOut.writeChar(it[4].code)
                }
            }
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
        openBinaryDataOutput(resourcesDirectory, LETTER_INDEX_MAP_RESOURCE_NAME).use { dataOut ->
            letterIndexMap.forEach { dataOut.writeShort(it.toInt()) }
        }
    }

    runBlocking {
        Language.all().map { language ->
            async(Dispatchers.IO) {
                UniBiTrigramRelativeFrequencyLookup.fromJson(language, letterIndexMap).writeBinary(resourcesDirectory, language)
                QuadriFivegramRelativeFrequencyLookup.fromJson(language, letterIndexMap).writeBinary(resourcesDirectory, language)
            }
        }.awaitAll()
    }
}
