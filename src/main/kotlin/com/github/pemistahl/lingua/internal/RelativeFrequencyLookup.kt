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
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
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
    private val quadrigramsAsInt: Int2DoubleOpenHashMap,
    private val quadrigramsAsLong: Long2DoubleOpenHashMap,
    private val fivegramsAsLong: Long2DoubleOpenHashMap,
    private val fivegramsAsObject: Object2DoubleOpenHashMap<String>
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

        fun fromJson(language: Language): QuadriFivegramRelativeFrequencyLookup {
            val lookup = QuadriFivegramRelativeFrequencyLookup()
            FrequencyLookupBuilder.fromJson(lookup, language, listOf("quadrigrams.json", "fivegrams.json"))
            return lookup
        }

        fun getBinaryModelResourceName(language: Language): String {
            return FrequencyLookupBuilder.getBinaryModelResourceName(language, "quadri-fivegrams.bin")
        }

        fun fromBinary(language: Language): QuadriFivegramRelativeFrequencyLookup {
            FrequencyLookupBuilder.openBinaryDataInput(getBinaryModelResourceName(language)).use {
                var quadrigramsAsIntCount = it.readInt()
                val quadrigramsAsInt = Int2DoubleOpenHashMap(quadrigramsAsIntCount, loadFactor)
                while (quadrigramsAsIntCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        quadrigramsAsInt.put(it.readInt(), frequency)
                    }
                    quadrigramsAsIntCount -= count
                }

                var quadrigramsAsLongCount = it.readInt()
                val quadrigramsAsLong = Long2DoubleOpenHashMap(quadrigramsAsLongCount, loadFactor)
                while (quadrigramsAsLongCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        quadrigramsAsLong.put(it.readLong(), frequency)
                    }
                    quadrigramsAsLongCount -= count
                }

                var fivegramsAsLongCount = it.readInt()
                val fivegramsAsLong = Long2DoubleOpenHashMap(fivegramsAsLongCount, loadFactor)
                while (fivegramsAsLongCount > 0) {
                    val (count, frequency) = decodeNgramCountAndFrequency(it)
                    for (i in 1..count) {
                        fivegramsAsLong.put(it.readLong(), frequency)
                    }
                    fivegramsAsLongCount -= count
                }

                var fivegramsAsObjectCount = it.readInt()
                val fivegramsAsObject = Object2DoubleOpenHashMap<String>(fivegramsAsObjectCount, loadFactor)
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
                    quadrigramsAsInt,
                    quadrigramsAsLong,
                    fivegramsAsLong,
                    fivegramsAsObject
                )
            }
        }
    }

    private constructor() : this(
        Int2DoubleOpenHashMap(initialCapacity, loadFactor),
        Long2DoubleOpenHashMap(initialCapacity, loadFactor),
        Long2DoubleOpenHashMap(initialCapacity, loadFactor),
        Object2DoubleOpenHashMap<String>(initialCapacity, loadFactor)
    )

    override fun putFrequency(ngram: String, frequency: Double) {
        if (frequency.isInfinite() || frequency <= 0.0) {
            throw IllegalArgumentException("Invalid frequency $frequency for ngram '$ngram'")
        }
        val old = when (ngram.length) {
            4 -> when {
                ngram.quadrigramFitsInt() -> quadrigramsAsInt.put(ngram.quadrigramToInt(), frequency)
                else -> quadrigramsAsLong.put(ngram.quadrigramToLong(), frequency)
            }
            5 -> when {
                ngram.fivegramFitsLong() -> fivegramsAsLong.put(ngram.fivegramToLong(), frequency)
                // Fall back to storing Ngram object
                else -> fivegramsAsObject.put(ngram, frequency)
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
        }
        if (old != 0.0) {
            throw AssertionError("Colliding encoding for '$ngram'")
        }
    }

    override fun finishCreation() {
        quadrigramsAsInt.trim()
        quadrigramsAsLong.trim()

        fivegramsAsLong.trim()
        fivegramsAsObject.trim()
    }

    fun getFrequency(ngram: ObjectNgram): Double {
        val ngramStr = ngram.value

        return when (ngramStr.length) {
            4 -> when {
                ngramStr.quadrigramFitsInt() -> quadrigramsAsInt.get(ngramStr.quadrigramToInt())
                else -> quadrigramsAsLong.get(ngramStr.quadrigramToLong())
            }
            5 -> when {
                ngramStr.fivegramFitsLong() -> fivegramsAsLong.get(ngramStr.fivegramToLong())
                else -> fivegramsAsObject.getDouble(ngramStr)
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
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
            dataOut.writeInt(quadrigramsAsInt.size)
            quadrigramsAsInt.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(IntConsumer { dataOut.writeInt(it) })
            }

            dataOut.writeInt(quadrigramsAsLong.size)
            quadrigramsAsLong.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(LongConsumer { dataOut.writeLong(it) })
            }

            dataOut.writeInt(fivegramsAsLong.size)
            fivegramsAsLong.reverse().double2ObjectEntrySet().fastForEach { entry ->
                encodeNgramCountAndFrequency(entry, dataOut)
                entry.value.forEach(LongConsumer { dataOut.writeLong(it) })
            }

            dataOut.writeInt(fivegramsAsObject.size)
            fivegramsAsObject.reverse().double2ObjectEntrySet().fastForEach { entry ->
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
    }
}
