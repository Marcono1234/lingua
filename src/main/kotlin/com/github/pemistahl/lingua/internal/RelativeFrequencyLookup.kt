package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.squareup.moshi.JsonReader
import it.unimi.dsi.fastutil.bytes.Byte2DoubleOpenHashMap
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import it.unimi.dsi.fastutil.bytes.ByteConsumer
import it.unimi.dsi.fastutil.bytes.ByteList
import it.unimi.dsi.fastutil.chars.Char2DoubleOpenHashMap
import it.unimi.dsi.fastutil.chars.CharArrayList
import it.unimi.dsi.fastutil.chars.CharConsumer
import it.unimi.dsi.fastutil.chars.CharList
import it.unimi.dsi.fastutil.doubles.Double2ObjectFunction
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap
import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2DoubleOpenHashMap
import it.unimi.dsi.fastutil.shorts.ShortArrayList
import it.unimi.dsi.fastutil.shorts.ShortConsumer
import it.unimi.dsi.fastutil.shorts.ShortList
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

internal typealias ModelEncodingType = Int

/**
 * Helper interface which allows creating lookups from model files.
 * The lookup types implement this interface for simplicity, however none
 * of the functions of this interface should be called from outside of this
 * file.
 */
internal interface FrequencyLookupBuilder {
    companion object {
        private val jsonModelNameOptions = JsonReader.Options.of("language", "ngrams")

        private fun fromJson(builder: FrequencyLookupBuilder, language: Language, jsonStream: InputStream) {
            val jsonReader = JsonReader.of(jsonStream.source().buffer())
            jsonReader.beginObject()

            var isLanguageMissing = true
            var areNgramsMissing = true

            while (jsonReader.hasNext()) {
                when (jsonReader.selectName(jsonModelNameOptions)) {
                    -1 -> throw IllegalArgumentException("Unknown name '${jsonReader.nextName()}' at ${jsonReader.path}")
                    0 -> if (isLanguageMissing) {
                        isLanguageMissing = false
                        if (Language.valueOf(jsonReader.nextString()) != language) {
                            throw IllegalArgumentException("JSON file is for wrong language")
                        }
                    } else throw IllegalArgumentException("Duplicate language at ${jsonReader.path}")
                    1 -> if (areNgramsMissing) {
                        areNgramsMissing = false
                        jsonReader.beginObject()
                        while (jsonReader.hasNext()) {
                            val (numerator, denominator) = jsonReader.nextName().split('/')
                                .map(String::toInt)
                            val frequency = numerator.toDouble() / denominator
                            jsonReader.nextString().split(' ')
                                .forEach { builder.putFrequency(it, frequency) }
                        }
                        jsonReader.endObject()
                    } else throw IllegalArgumentException("Duplicate ngrams at ${jsonReader.path}")
                }
            }
            jsonReader.endObject()

            if (isLanguageMissing) throw IllegalArgumentException("Model data is missing language")
            if (areNgramsMissing) throw IllegalArgumentException("Model data is missing ngrams")
            builder.finishCreation()
        }

        fun fromJson(builder: FrequencyLookupBuilder, language: Language, jsonNames: List<String>) {
            jsonNames.forEach { jsonName ->
                val filePath = "/language-models/${language.isoCode639_1}/$jsonName"
                Language::class.java.getResourceAsStream(filePath)!!.use {
                    fromJson(builder, language, it)
                }
            }
        }

        fun getBinaryModelResourceName(language: Language, fileName: String): String {
            return "/language-models/${language.isoCode639_1}/$fileName"
        }

        fun openBinaryDataInput(resourcePath: String): DataInputStream {
            return DataInputStream(FrequencyLookupBuilder::class.java.getResourceAsStream(resourcePath)!!.buffered())
        }

        fun openBinaryDataOutput(resourcesDirectory: Path, resourcePath: String): DataOutputStream {
            val file = resourcesDirectory.resolve(resourcePath.removePrefix("/"))
            Files.createDirectories(file.parent)

            return DataOutputStream(Files.newOutputStream(file).buffered())
        }
    }

    fun putFrequency(ngram: String, frequency: Double)
    fun finishCreation()
}

private fun Byte2DoubleOpenHashMap.reverse(): Double2ObjectOpenHashMap<ByteList> {
    val reversed = Double2ObjectOpenHashMap<ByteList>(10, 0.90f)
    byte2DoubleEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.doubleValue, Double2ObjectFunction { _ -> ByteArrayList() }).add(it.byteKey)
    }
    return reversed
}

private fun Char2DoubleOpenHashMap.reverse(): Double2ObjectOpenHashMap<CharList> {
    val reversed = Double2ObjectOpenHashMap<CharList>(10, 0.90f)
    char2DoubleEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.doubleValue, Double2ObjectFunction { _ -> CharArrayList() }).add(it.charKey)
    }
    return reversed
}

private fun Short2DoubleOpenHashMap.reverse(): Double2ObjectOpenHashMap<ShortList> {
    val reversed = Double2ObjectOpenHashMap<ShortList>(10, 0.90f)
    short2DoubleEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.doubleValue, Double2ObjectFunction { _ -> ShortArrayList() }).add(it.shortKey)
    }
    return reversed
}

private fun Int2DoubleOpenHashMap.reverse(): Double2ObjectOpenHashMap<IntList> {
    val reversed = Double2ObjectOpenHashMap<IntList>(10, 0.90f)
    int2DoubleEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.doubleValue, Double2ObjectFunction { _ -> IntArrayList() }).add(it.intKey)
    }
    return reversed
}

private fun Long2DoubleOpenHashMap.reverse(): Double2ObjectOpenHashMap<LongList> {
    val reversed = Double2ObjectOpenHashMap<LongList>(10, 0.90f)
    long2DoubleEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.doubleValue, Double2ObjectFunction { _ -> LongArrayList() }).add(it.longKey)
    }
    return reversed
}

private fun <T> Object2DoubleOpenHashMap<T>.reverse(): Double2ObjectOpenHashMap<out List<T>> {
    val reversed = Double2ObjectOpenHashMap<MutableList<T>>(10, 0.90f)
    object2DoubleEntrySet().fastIterator().forEach {
        reversed.computeIfAbsent(it.doubleValue, Double2ObjectFunction { _ -> mutableListOf() }).add(it.key)
    }
    return reversed
}

private fun encodeNgramCountAndFrequency(entry: Double2ObjectMap.Entry<out Collection<*>>, dataOut: DataOutput) {
    /*
     * If count == 1: Only write frequency
     * Else: Write count (negated to differentiate it from frequency), followed by frequency
     */

    val ngramCount = entry.value.size
    if (ngramCount > 1) {
        // Invert count to make it negative allowing to differentiate it from frequency
        dataOut.writeInt(ngramCount.inv())
    }
    assert(entry.doubleKey >= 0)
    dataOut.writeDouble(entry.doubleKey)
}

private data class NgramCountAndFrequency(val count: Int, val frequency: Double)
private fun decodeNgramCountAndFrequency(dataIn: DataInput): NgramCountAndFrequency {
    val first32Bits = dataIn.readInt()
    val count: Int
    val frequency: Double

    if (first32Bits < 0) {
        count = first32Bits.inv()
        frequency = dataIn.readDouble()
    } else {
        count = 1
        // Reconstruct `double` from already read 32 bits and subsequent 32 bits
        val doubleBits = (
            (first32Bits.toLong() shl 32)
            or (dataIn.readInt().toUInt().toLong())
        )
        frequency = Double.fromBits(doubleBits)
    }
    return NgramCountAndFrequency(count, frequency)
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
    private val unigramsAsByte: Byte2DoubleOpenHashMap,
    private val unigramsAsChar: Char2DoubleOpenHashMap,
    private val bigramsAsShort: Short2DoubleOpenHashMap,
    private val bigramsAsInt: Int2DoubleOpenHashMap,
    private val trigramsAsInt: Int2DoubleOpenHashMap,
    private val trigramsAsLong: Long2DoubleOpenHashMap
): FrequencyLookupBuilder {
    companion object {
        private const val loadFactor = 0.9f
        private const val initialCapacity = 16

        internal const val UNIGRAM_AS_BYTE: ModelEncodingType = 0
        internal const val UNIGRAM_AS_CHAR: ModelEncodingType = 1
        internal const val BIGRAM_AS_SHORT: ModelEncodingType = 2
        internal const val BIGRAM_AS_INT: ModelEncodingType = 3
        internal const val TRIGRAM_AS_INT: ModelEncodingType = 4
        internal const val TRIGRAM_AS_LONG: ModelEncodingType = 5

        private fun String.bigramFitsShort(): Boolean {
            return this[0].code <= 255 && this[1].code <= 255
        }

        private fun String.bigramToShort(): Short {
            return (
                this[0].code
                or (this[1].code shl 8)
            ).toShort()
        }

        private fun String.bigramToInt(): Int {
            return (
                this[0].code
                or (this[1].code shl 16)
            )
        }

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

        internal fun trigramFitsInt(char0: Int, char1: Int, char2: Int): Boolean {
            /*
             * Trigram is encoded by writing absolute char value of first char (index 0)
             * followed by the signed offsets of the other chars compared to the first char.
             *
             * This allows encoding trigrams where some or all chars would not fit
             * within Int.SIZE_BITS / 3, but all of the char values are close together.
             */
            if (char0 >= (1 shl TRIGRAM_AS_INT_BASE_CHAR_BITS)) return false

            // (2^x) - 1
            val maxOffset = (1 shl (TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR - 1)) - 1
            // -2^x
            val minOffset = -maxOffset - 1

            val diff1 = char1 - char0
            val diff2 = char2 - char0

            return (diff1 in minOffset..maxOffset)
                && (diff2 in minOffset..maxOffset)
        }

        private fun String.trigramFitsInt(): Boolean {
            return trigramFitsInt(this[0].code, this[1].code, this[2].code)
        }

        internal fun trigramToInt(char0: Int, char1: Int, char2: Int): Int {
            // AND with bitmask to remove leading 1s for negative values
            val diff1 = (char1 - char0) and TRIGRAM_AS_INT_OFFSET_BIT_MASK
            val diff2 = (char2 - char0) and TRIGRAM_AS_INT_OFFSET_BIT_MASK
            return (
                char0
                or (diff1 shl TRIGRAM_AS_INT_BASE_CHAR_BITS)
                or (diff2 shl (TRIGRAM_AS_INT_BASE_CHAR_BITS + TRIGRAM_AS_INT_OFFSET_BITS_PER_CHAR))
            )
        }

        private fun String.trigramToInt(): Int {
            return trigramToInt(this[0].code, this[1].code, this[2].code)
        }

        private fun String.trigramToLong(): Long {
            return (
                this[0].code.toLong()
                or (this[1].code.toLong() shl 16)
                or (this[2].code.toLong() shl 32)
            )
        }

        fun fromJson(language: Language): UniBiTrigramRelativeFrequencyLookup {
            val lookup = UniBiTrigramRelativeFrequencyLookup()
            FrequencyLookupBuilder.fromJson(lookup, language, listOf("unigrams.json", "bigrams.json", "trigrams.json"))
            return lookup
        }

        fun getBinaryModelResourceName(language: Language): String {
            return FrequencyLookupBuilder.getBinaryModelResourceName(language, "uni-bi-trigrams.bin")
        }

        fun fromBinary(language: Language): UniBiTrigramRelativeFrequencyLookup {
            FrequencyLookupBuilder.openBinaryDataInput(getBinaryModelResourceName(language)).use {
                var unigramsAsByteCount = it.readInt()
                val unigramsAsByte = Byte2DoubleOpenHashMap(unigramsAsByteCount, loadFactor)
                while (unigramsAsByteCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        unigramsAsByte.put(it.readByte(), frequency)
                    }
                    unigramsAsByteCount -= count
                }

                var unigramsAsCharCount = it.readInt()
                val unigramsAsChar = Char2DoubleOpenHashMap(unigramsAsCharCount, loadFactor)
                while (unigramsAsCharCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        unigramsAsChar.put(it.readChar(), frequency)
                    }
                    unigramsAsCharCount -= count
                }

                var bigramsAsShortCount = it.readInt()
                val bigramsAsShort = Short2DoubleOpenHashMap(bigramsAsShortCount, loadFactor)
                while (bigramsAsShortCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        bigramsAsShort.put(it.readShort(), frequency)
                    }
                    bigramsAsShortCount -= count
                }

                var bigramsAsIntCount = it.readInt()
                val bigramsAsInt = Int2DoubleOpenHashMap(bigramsAsIntCount, loadFactor)
                while (bigramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        bigramsAsInt.put(it.readInt(), frequency)
                    }
                    bigramsAsIntCount -= count
                }

                var trigramsAsIntCount = it.readInt()
                val trigramsAsInt = Int2DoubleOpenHashMap(trigramsAsIntCount, loadFactor)
                while (trigramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        trigramsAsInt.put(it.readInt(), frequency)
                    }
                    trigramsAsIntCount -= count
                }

                var trigramsAsLongCount = it.readInt()
                val trigramsAsLong = Long2DoubleOpenHashMap(trigramsAsLongCount, loadFactor)
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
                    unigramsAsByte,
                    unigramsAsChar,
                    bigramsAsShort,
                    bigramsAsInt,
                    trigramsAsInt,
                    trigramsAsLong
                )
            }
        }
    }

    private constructor() : this(
        Byte2DoubleOpenHashMap(initialCapacity, loadFactor),
        Char2DoubleOpenHashMap(initialCapacity, loadFactor),
        Short2DoubleOpenHashMap(initialCapacity, loadFactor),
        Int2DoubleOpenHashMap(initialCapacity, loadFactor),
        Int2DoubleOpenHashMap(initialCapacity, loadFactor),
        Long2DoubleOpenHashMap(initialCapacity, loadFactor)
    )

    override fun putFrequency(ngram: String, frequency: Double) {
        if (frequency.isInfinite() || frequency <= 0.0) {
            throw IllegalArgumentException("Invalid frequency $frequency for ngram '$ngram'")
        }
        val old = when (ngram.length) {
            1 -> {
                val char0 = ngram[0].code
                when {
                    char0 <= 255 -> unigramsAsByte.put(char0.toByte(), frequency)
                    else -> unigramsAsChar.put(char0.toChar(), frequency)
                }
            }
            2 -> when {
                ngram.bigramFitsShort() -> bigramsAsShort.put(ngram.bigramToShort(), frequency)
                else -> bigramsAsInt.put(ngram.bigramToInt(), frequency)
            }
            3 -> when {
                ngram.trigramFitsInt() -> trigramsAsInt.put(ngram.trigramToInt(), frequency)
                else -> trigramsAsLong.put(ngram.trigramToLong(), frequency)
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
        }
        if (old != 0.0) {
            throw AssertionError("Colliding encoding for '$ngram'")
        }
    }

    override fun finishCreation() {
        unigramsAsByte.trim()
        unigramsAsChar.trim()

        bigramsAsShort.trim()
        bigramsAsInt.trim()

        trigramsAsInt.trim()
        trigramsAsLong.trim()
    }

    fun getFrequency(ngram: PrimitiveNgram): Double {
        return when (ngram.getEncodingType()) {
            UNIGRAM_AS_BYTE -> unigramsAsByte.get(ngram.unigramToByte())
            UNIGRAM_AS_CHAR -> unigramsAsChar.get(ngram.unigramToChar())
            BIGRAM_AS_SHORT -> bigramsAsShort.get(ngram.bigramToShort())
            BIGRAM_AS_INT -> bigramsAsInt.get(ngram.bigramToInt())
            TRIGRAM_AS_INT -> trigramsAsInt.get(ngram.trigramToInt())
            TRIGRAM_AS_LONG -> trigramsAsLong.get(ngram.trigramToLong())
            else -> throw AssertionError("Unknown encoding type")
        }
    }

    /**
     * Writes the binary representation of the model data to a sub directory for
     * the specified language of the resources directory.
     *
     * @see fromBinary
     */
    fun writeBinary(resourcesDirectory: Path, language: Language) {
        val resourceName = getBinaryModelResourceName(language)

        FrequencyLookupBuilder.openBinaryDataOutput(resourcesDirectory, resourceName).use { dataOut ->
            dataOut.writeInt(unigramsAsByte.size)
            unigramsAsByte.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(ByteConsumer { dataOut.writeByte(it.toInt()) })
            }

            dataOut.writeInt(unigramsAsChar.size)
            unigramsAsChar.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(CharConsumer { dataOut.writeChar(it.code) })
            }

            dataOut.writeInt(bigramsAsShort.size)
            bigramsAsShort.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(ShortConsumer { dataOut.writeShort(it.toInt()) })
            }

            dataOut.writeInt(bigramsAsInt.size)
            bigramsAsInt.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer { dataOut.writeInt(it) })
            }

            dataOut.writeInt(trigramsAsInt.size)
            trigramsAsInt.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer { dataOut.writeInt(it) })
            }

            dataOut.writeInt(trigramsAsLong.size)
            trigramsAsLong.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(LongConsumer { dataOut.writeLong(it) })
            }
        }
    }
}

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal class QuadriFivegramRelativeFrequencyLookup private constructor(
    /*
     * Implementation note: This lookup uses 'direct' and 'indirect' maps. The 'direct' maps
     * directly map to the frequency value, the 'indirect' ones map to an index in an array
     * at which the frequency is stored. This reduces memory usage at runtime because often
     * multiple ngrams share the same 64-bit double frequency. Therefore instead of repeating
     * that value, only store a 16-bit index and store these common frequencies in a separate
     * array.
     *
     * The performance overhead is probably acceptable because LanguageDetector does not
     * quadrigrams and fivegrams for longer texts.
     */
    private val quadrigramsAsIntDirect: Int2DoubleOpenHashMap,
    private val quadrigramsAsIntIndirect: Int2ShortOpenHashMap,
    private val quadrigramsAsLongDirect: Long2DoubleOpenHashMap,
    private val quadrigramsAsLongIndirect: Long2ShortOpenHashMap,
    private val quadrigramsIndirectFrequencies: DoubleArray,
    private val fivegramsAsLongDirect: Long2DoubleOpenHashMap,
    private val fivegramsAsLongIndirect: Long2ShortOpenHashMap,
    private val fivegramsAsObjectDirect: Object2DoubleOpenHashMap<String>,
    private val fivegramsAsObjectIndirect: Object2ShortOpenHashMap<String>,
    private val fivegramsIndirectFrequencies: DoubleArray,
) : FrequencyLookupBuilder {
    companion object {
        val empty = QuadriFivegramRelativeFrequencyLookup()

        private const val loadFactor = 0.9f
        private const val initialCapacity = 16

        private fun String.quadrigramFitsInt(): Boolean {
            return this[0].code <= 255
                && this[1].code <= 255
                && this[2].code <= 255
                && this[3].code <= 255
        }

        private fun String.quadrigramToInt(): Int {
            return (
                this[0].code
                or (this[1].code shl 8)
                or (this[2].code shl 16)
                or (this[3].code shl 24)
            )
        }

        private fun String.quadrigramToLong(): Long {
            return (
                this[0].code.toLong()
                or (this[1].code.toLong() shl 16)
                or (this[2].code.toLong() shl 32)
                or (this[3].code.toLong() shl 48)
            )
        }

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

        private fun String.fivegramFitsLong(): Boolean {
            /*
             * Fivegram is encoded by writing absolute char value of first char (index 0)
             * using 16 bits followed by the signed offsets of the other chars compared
             * to the first char.
             *
             * This allows encoding fivegrams where some or all chars would not fit
             * within Long.SIZE_BITS / 5, but all of the char values are close together.
             */

            val char0 = this[0].code
            // (2^x) - 1
            val maxOffset = (1 shl (FIVEGRAM_OFFSET_BITS_PER_CHAR - 1)) - 1
            // -2^x
            val minOffset = -maxOffset - 1

            val diff1 = this[1].code - char0
            val diff2 = this[2].code - char0
            val diff3 = this[3].code - char0
            val diff4 = this[4].code - char0

            return (diff1 in minOffset..maxOffset)
                && (diff2 in minOffset..maxOffset)
                && (diff3 in minOffset..maxOffset)
                && (diff4 in minOffset..maxOffset)
        }

        private fun String.fivegramToLong(): Long {
            val char0 = this[0].code.toLong()
            // AND with bitmask to remove leading 1s for negative values
            val diff1 = (this[1].code.toLong() - char0) and FIVEGRAM_OFFSET_BIT_MASK
            val diff2 = (this[2].code.toLong() - char0) and FIVEGRAM_OFFSET_BIT_MASK
            val diff3 = (this[3].code.toLong() - char0) and FIVEGRAM_OFFSET_BIT_MASK
            val diff4 = (this[4].code.toLong() - char0) and FIVEGRAM_OFFSET_BIT_MASK

            return (
                char0
                or (diff1 shl FIVEGRAM_BASE_CHAR_BITS)
                or (diff2 shl (FIVEGRAM_BASE_CHAR_BITS + FIVEGRAM_OFFSET_BITS_PER_CHAR))
                or (diff3 shl (FIVEGRAM_BASE_CHAR_BITS + FIVEGRAM_OFFSET_BITS_PER_CHAR * 2))
                or (diff4 shl (FIVEGRAM_BASE_CHAR_BITS + FIVEGRAM_OFFSET_BITS_PER_CHAR * 3))
            )
        }

        /**
         * Minimum number of ngrams which need to share the same frequency for them to be
         * stored indirectly.
         */
        private const val INDIRECT_STORAGE_THRESHOLD = 2

        /**
         * Maximum number of frequencies which can be stored indirectly.
         */
        private const val INDIRECT_MAX_FREQUENCIES_COUNT = 0xFFFF // UShort.MAX_VALUE

        /**
         * Index value for indirect frequency lookup indicating that frequency is not stored indirectly
         * (i.e. has no index for indirect lookup).
         */
        private const val INDIRECT_NO_INDEX = 0

        fun fromJson(language: Language): QuadriFivegramRelativeFrequencyLookup {
            val lookup = QuadriFivegramRelativeFrequencyLookup()
            FrequencyLookupBuilder.fromJson(lookup, language, listOf("quadrigrams.json", "fivegrams.json"))
            return lookup
        }

        fun getBinaryModelResourceName(language: Language): String {
            return FrequencyLookupBuilder.getBinaryModelResourceName(language, "quadri-fivegrams.bin")
        }

        private data class QuadrigramsData(
            val quadrigramsAsIntDirect: Int2DoubleOpenHashMap,
            val quadrigramsAsIntIndirect: Int2ShortOpenHashMap,
            val quadrigramsAsLongDirect: Long2DoubleOpenHashMap,
            val quadrigramsAsLongIndirect: Long2ShortOpenHashMap,
            val quadrigramsIndirectFrequencies: DoubleArray
        )
        private fun quadrigramsFromBinary(dataIn: DataInput): QuadrigramsData {
            val indirectFrequenciesCount = dataIn.readUnsignedShort()
            val indirectFrequencies = DoubleArray(indirectFrequenciesCount)
            var indirectFrequencyIndex = 0

            /* Quadrigrams as Int */
            val directQuadrigramsAsIntCount = dataIn.readInt()
            val indirectQuadrigramsAsIntCount = dataIn.readInt()
            val directQuadrigramsAsInt = Int2DoubleOpenHashMap(directQuadrigramsAsIntCount, loadFactor)
            val indirectQuadrigramAsInt = Int2ShortOpenHashMap(indirectQuadrigramsAsIntCount, loadFactor)

            var quadrigramsAsIntCount = directQuadrigramsAsIntCount + indirectQuadrigramsAsIntCount
            while (quadrigramsAsIntCount > 0) {
                val (count, frequency) = decodeNgramCountAndFrequency(dataIn)
                if (count >= INDIRECT_STORAGE_THRESHOLD) {
                    indirectFrequencies[indirectFrequencyIndex] = frequency
                    for (i in 1..count) {
                        indirectQuadrigramAsInt.put(dataIn.readInt(), indirectFrequencyIndex.toShort())
                    }
                    indirectFrequencyIndex++
                } else {
                    for (i in 1..count) {
                        directQuadrigramsAsInt.put(dataIn.readInt(), frequency)
                    }
                }
                quadrigramsAsIntCount -= count
            }

            /* Quadrigrams as Long */
            val directQuadrigramsAsLongCount = dataIn.readInt()
            val indirectQuadrigramsAsLongCount = dataIn.readInt()
            val directQuadrigramsAsLong = Long2DoubleOpenHashMap(directQuadrigramsAsLongCount, loadFactor)
            val indirectQuadrigramAsLong = Long2ShortOpenHashMap(indirectQuadrigramsAsLongCount, loadFactor)

            var quadrigramsAsLongCount = directQuadrigramsAsLongCount + indirectQuadrigramsAsLongCount
            while (quadrigramsAsLongCount > 0) {
                val (count, frequency) = decodeNgramCountAndFrequency(dataIn)
                if (count >= INDIRECT_STORAGE_THRESHOLD) {
                    indirectFrequencies[indirectFrequencyIndex] = frequency
                    for (i in 1..count) {
                        indirectQuadrigramAsLong.put(dataIn.readLong(), indirectFrequencyIndex.toShort())
                    }
                    indirectFrequencyIndex++
                } else {
                    for (i in 1..count) {
                        directQuadrigramsAsLong.put(dataIn.readLong(), frequency)
                    }
                }
                quadrigramsAsLongCount -= count
            }

            return QuadrigramsData(
                directQuadrigramsAsInt,
                indirectQuadrigramAsInt,
                directQuadrigramsAsLong,
                indirectQuadrigramAsLong,
                indirectFrequencies
            )
        }

        private data class FivegramsData(
            val fivegramsAsLongDirect: Long2DoubleOpenHashMap,
            val fivegramsAsLongIndirect: Long2ShortOpenHashMap,
            val fivegramsAsObjectDirect: Object2DoubleOpenHashMap<String>,
            val fivegramsAsObjectIndirect: Object2ShortOpenHashMap<String>,
            val fivegramsIndirectFrequencies: DoubleArray
        )
        private fun fivegramsFromBinary(dataIn: DataInput): FivegramsData {
            val indirectFrequenciesCount = dataIn.readUnsignedShort()
            val indirectFrequencies = DoubleArray(indirectFrequenciesCount)
            var indirectFrequencyIndex = 0

            /* Fivegrams as Long */
            val directFivegramsAsLongCount = dataIn.readInt()
            val indirectFivegramsAsLongCount = dataIn.readInt()
            val directFivegramsAsLong = Long2DoubleOpenHashMap(directFivegramsAsLongCount, loadFactor)
            val indirectFivegramsAsLong = Long2ShortOpenHashMap(indirectFivegramsAsLongCount, loadFactor)

            var fivegramsAsLongCount = directFivegramsAsLongCount + indirectFivegramsAsLongCount
            while (fivegramsAsLongCount > 0) {
                val (count, frequency) = decodeNgramCountAndFrequency(dataIn)
                if (count >= INDIRECT_STORAGE_THRESHOLD) {
                    indirectFrequencies[indirectFrequencyIndex] = frequency
                    for (i in 1..count) {
                        indirectFivegramsAsLong.put(dataIn.readLong(), indirectFrequencyIndex.toShort())
                    }
                    indirectFrequencyIndex++
                } else {
                    for (i in 1..count) {
                        directFivegramsAsLong.put(dataIn.readLong(), frequency)
                    }
                }
                fivegramsAsLongCount -= count
            }

            /* Fivegrams as Object */
            val directFivegramsAsObjectCount = dataIn.readInt()
            val indirectFivegramsAsObjectCount = dataIn.readInt()
            val directFivegramsAsObject = Object2DoubleOpenHashMap<String>(directFivegramsAsObjectCount, loadFactor)
            val indirectFivegramsAsObject = Object2ShortOpenHashMap<String>(indirectFivegramsAsObjectCount, loadFactor)

            var fivegramsAsObjectCount = directFivegramsAsObjectCount + indirectFivegramsAsObjectCount
            while (fivegramsAsObjectCount > 0) {
                val (count, frequency) = decodeNgramCountAndFrequency(dataIn)
                if (count >= INDIRECT_STORAGE_THRESHOLD) {
                    indirectFrequencies[indirectFrequencyIndex] = frequency
                    for (i in 1..count) {
                        val charArray = CharArray(5)
                        charArray[0] = dataIn.readChar()
                        charArray[1] = dataIn.readChar()
                        charArray[2] = dataIn.readChar()
                        charArray[3] = dataIn.readChar()
                        charArray[4] = dataIn.readChar()
                        val fivegram = String(charArray)
                        indirectFivegramsAsObject.put(fivegram, indirectFrequencyIndex.toShort())
                    }
                    indirectFrequencyIndex++
                } else {
                    for (i in 1..count) {
                        val charArray = CharArray(5)
                        charArray[0] = dataIn.readChar()
                        charArray[1] = dataIn.readChar()
                        charArray[2] = dataIn.readChar()
                        charArray[3] = dataIn.readChar()
                        charArray[4] = dataIn.readChar()
                        val fivegram = String(charArray)
                        directFivegramsAsObject.put(fivegram, frequency)
                    }
                }
                fivegramsAsObjectCount -= count
            }

            return FivegramsData(
                directFivegramsAsLong,
                indirectFivegramsAsLong,
                directFivegramsAsObject,
                indirectFivegramsAsObject,
                indirectFrequencies
            )
        }

        fun fromBinary(language: Language): QuadriFivegramRelativeFrequencyLookup {
            FrequencyLookupBuilder.openBinaryDataInput(getBinaryModelResourceName(language)).use {
                val quadrigramsData = quadrigramsFromBinary(it)
                val fivegramsData = fivegramsFromBinary(it)

                // Should have reached end of data
                assert(it.read() == -1)

                return QuadriFivegramRelativeFrequencyLookup(
                    quadrigramsData.quadrigramsAsIntDirect,
                    quadrigramsData.quadrigramsAsIntIndirect,
                    quadrigramsData.quadrigramsAsLongDirect,
                    quadrigramsData.quadrigramsAsLongIndirect,
                    quadrigramsData.quadrigramsIndirectFrequencies,
                    fivegramsData.fivegramsAsLongDirect,
                    fivegramsData.fivegramsAsLongIndirect,
                    fivegramsData.fivegramsAsObjectDirect,
                    fivegramsData.fivegramsAsObjectIndirect,
                    fivegramsData.fivegramsIndirectFrequencies
                )
            }
        }
    }

    private constructor() : this(
        Int2DoubleOpenHashMap(initialCapacity, loadFactor),
        Int2ShortOpenHashMap(0), // Won't be used when constructing lookup from JSON
        Long2DoubleOpenHashMap(initialCapacity, loadFactor),
        Long2ShortOpenHashMap(0), // Won't be used when constructing lookup from JSON
        DoubleArray(0), // Won't be used when constructing lookup from JSON
        Long2DoubleOpenHashMap(initialCapacity, loadFactor),
        Long2ShortOpenHashMap(0), // Won't be used when constructing lookup from JSON
        Object2DoubleOpenHashMap<String>(initialCapacity, loadFactor),
        Object2ShortOpenHashMap(0), // Won't be used when constructing lookup from JSON
        DoubleArray(0) // Won't be used when constructing lookup from JSON
    )

    override fun putFrequency(ngram: String, frequency: Double) {
        if (frequency.isInfinite() || frequency <= 0.0) {
            throw IllegalArgumentException("Invalid frequency $frequency for ngram '$ngram'")
        }
        val old = when (ngram.length) {
            4 -> when {
                ngram.quadrigramFitsInt() -> quadrigramsAsIntDirect.put(ngram.quadrigramToInt(), frequency)
                else -> quadrigramsAsLongDirect.put(ngram.quadrigramToLong(), frequency)
            }
            5 -> when {
                ngram.fivegramFitsLong() -> fivegramsAsLongDirect.put(ngram.fivegramToLong(), frequency)
                // Fall back to storing Ngram object
                else -> fivegramsAsObjectDirect.put(ngram, frequency)
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
        }
        if (old != 0.0) {
            throw AssertionError("Colliding encoding for '$ngram'")
        }
    }

    override fun finishCreation() {
        quadrigramsAsIntDirect.trim()
        quadrigramsAsLongDirect.trim()

        fivegramsAsLongDirect.trim()
        fivegramsAsObjectDirect.trim()
    }

    fun getFrequency(ngram: ObjectNgram): Double {
        val ngramStr = ngram.value

        return when (ngramStr.length) {
            4 -> when {
                ngramStr.quadrigramFitsInt() -> {
                    val encoded = ngramStr.quadrigramToInt()
                    val index = quadrigramsAsIntIndirect.get(encoded).toUShort().toInt()
                    return when (index) {
                        INDIRECT_NO_INDEX -> quadrigramsAsIntDirect.get(encoded)
                        else -> quadrigramsIndirectFrequencies[index - 1]
                    }
                }
                else -> {
                    val encoded = ngramStr.quadrigramToLong()
                    val index = quadrigramsAsLongIndirect.get(encoded).toUShort().toInt()
                    return when (index) {
                        INDIRECT_NO_INDEX -> quadrigramsAsLongDirect.get(encoded)
                        else -> quadrigramsIndirectFrequencies[index - 1]
                    }
                }
            }
            5 -> when {
                ngramStr.fivegramFitsLong() -> {
                    val encoded = ngramStr.fivegramToLong()
                    val index = fivegramsAsLongIndirect.get(encoded).toUShort().toInt()
                    return when (index) {
                        INDIRECT_NO_INDEX -> fivegramsAsLongDirect.get(encoded)
                        else -> fivegramsIndirectFrequencies[index - 1]
                    }
                }
                else -> {
                    val index = fivegramsAsObjectIndirect.getShort(ngramStr).toUShort().toInt()
                    return when (index) {
                        INDIRECT_NO_INDEX -> fivegramsAsObjectDirect.getDouble(ngramStr)
                        else -> fivegramsIndirectFrequencies[index - 1]
                    }
                }
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
        }
    }

    private fun writeQuadrigrams(dataOut: DataOutput) {
        val reversedQuadrigramsAsInt = quadrigramsAsIntDirect.reverse().double2ObjectEntrySet()
        val reversedQuadrigramsAsLong = quadrigramsAsLongDirect.reverse().double2ObjectEntrySet()

        val indirectQuadrigramFrequenciesCount = (
            reversedQuadrigramsAsInt.count { it.value.size >= INDIRECT_STORAGE_THRESHOLD }
            + reversedQuadrigramsAsLong.count { it.value.size >= INDIRECT_STORAGE_THRESHOLD }
        )
        assert(indirectQuadrigramFrequenciesCount <= INDIRECT_MAX_FREQUENCIES_COUNT)
        dataOut.writeShort(indirectQuadrigramFrequenciesCount)

        /* Quadrigrams as Int */
        val directQuadrigramsAsIntCount = reversedQuadrigramsAsInt.sumOf {
            val count = it.value.size
            if (count < INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        val indirectQuadrigramsAsIntCount = reversedQuadrigramsAsInt.sumOf {
            val count = it.value.size
            if (count >= INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        dataOut.writeInt(directQuadrigramsAsIntCount)
        dataOut.writeInt(indirectQuadrigramsAsIntCount)
        reversedQuadrigramsAsInt.fastForEach { entry ->
            encodeNgramCountAndFrequency(entry, dataOut)
            entry.value.forEach(IntConsumer { dataOut.writeInt(it) })
        }

        /* Quadrigrams as Long */
        val directQuadrigramsAsLongCount = reversedQuadrigramsAsLong.sumOf {
            val count = it.value.size
            if (count < INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        val indirectQuadrigramsAsLongCount = reversedQuadrigramsAsLong.sumOf {
            val count = it.value.size
            if (count >= INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        dataOut.writeInt(directQuadrigramsAsLongCount)
        dataOut.writeInt(indirectQuadrigramsAsLongCount)
        reversedQuadrigramsAsLong.fastForEach { entry ->
            encodeNgramCountAndFrequency(entry, dataOut)
            entry.value.forEach(LongConsumer { dataOut.writeLong(it) })
        }
    }

    private fun writeFivegrams(dataOut: DataOutput) {
        val reversedFivegramsAsLong = fivegramsAsLongDirect.reverse().double2ObjectEntrySet()
        val reversedFivegramsAsObject = fivegramsAsObjectDirect.reverse().double2ObjectEntrySet()

        val indirectFivegramFrequenciesCount = (
            reversedFivegramsAsLong.count { it.value.size >= INDIRECT_STORAGE_THRESHOLD }
            + reversedFivegramsAsObject.count { it.value.size >= INDIRECT_STORAGE_THRESHOLD }
        )
        assert(indirectFivegramFrequenciesCount <= INDIRECT_MAX_FREQUENCIES_COUNT)
        dataOut.writeShort(indirectFivegramFrequenciesCount)

        /* Fivegrams as Long */
        val directFivegramsAsLongCount = reversedFivegramsAsLong.sumOf {
            val count = it.value.size
            if (count < INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        val indirectFivegramsAsLongCount = reversedFivegramsAsLong.sumOf {
            val count = it.value.size
            if (count >= INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        dataOut.writeInt(directFivegramsAsLongCount)
        dataOut.writeInt(indirectFivegramsAsLongCount)
        reversedFivegramsAsLong.fastForEach { entry ->
            encodeNgramCountAndFrequency(entry, dataOut)
            entry.value.forEach(LongConsumer { dataOut.writeLong(it) })
        }

        /* Fivegrams as Object */
        val directFivegramsAsObjectCount = reversedFivegramsAsObject.sumOf {
            val count = it.value.size
            if (count < INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        val indirectFivegramsAsObjectCount = reversedFivegramsAsObject.sumOf {
            val count = it.value.size
            if (count >= INDIRECT_STORAGE_THRESHOLD) count else 0
        }
        dataOut.writeInt(directFivegramsAsObjectCount)
        dataOut.writeInt(indirectFivegramsAsObjectCount)
        reversedFivegramsAsObject.fastForEach { entry ->
            encodeNgramCountAndFrequency(entry, dataOut)
            entry.value.forEach {
                // Write String manually since length is known (= 5) so don't have to encode it
                dataOut.writeChar(it[0].code)
                dataOut.writeChar(it[1].code)
                dataOut.writeChar(it[2].code)
                dataOut.writeChar(it[3].code)
                dataOut.writeChar(it[4].code)
            }
        }
    }

    /**
     * Writes the binary representation of the model data to a sub directory for
     * the specified language of the resources directory.
     *
     * @see fromBinary
     */
    fun writeBinary(resourcesDirectory: Path, language: Language) {
        val resourceName = getBinaryModelResourceName(language)

        FrequencyLookupBuilder.openBinaryDataOutput(resourcesDirectory, resourceName).use { dataOut ->
            writeQuadrigrams(dataOut)
            writeFivegrams(dataOut)
        }
    }
}
