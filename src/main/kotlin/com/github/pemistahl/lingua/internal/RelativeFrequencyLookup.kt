package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.squareup.moshi.JsonReader
import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap
import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap
import okio.buffer
import okio.source
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

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

        private fun encodeFraction(numerator: Int, denominator: Int): Int {
            val encoded = numerator.toLong() * Int.MAX_VALUE.toLong() / denominator.toLong()
            return if (encoded > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else encoded.toInt()
        }

        fun decodeFraction(encoded: Int): Double {
            return encoded.toDouble() / Int.MAX_VALUE
        }

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
                            val encodedFrequency = encodeFraction(numerator, denominator)
                            jsonReader.nextString().split(' ')
                                .forEach { builder.putFrequency(it, encodedFrequency) }
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

    fun putFrequency(ngram: String, encodedFrequency: Int)
    fun finishCreation()
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
    private val unigramsAsByte: Byte2IntOpenHashMap,
    private val unigramsAsChar: Char2IntOpenHashMap,
    private val bigramsAsShort: Short2IntOpenHashMap,
    private val bigramsAsInt: Int2IntOpenHashMap,
    private val trigramsAsInt: Int2IntOpenHashMap,
    private val trigramsAsLong: Long2IntOpenHashMap
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

        internal const val TRIGRAM_AS_INT_BITS_PER_CHAR = Int.SIZE_BITS / 3
        /**
         * Maximum code point value (inclusive) a char of a trigram may have to
         * allow encoding the trigram as int.
         */
        private const val TRIGRAM_AS_INT_MAX_CHAR = (1 shl TRIGRAM_AS_INT_BITS_PER_CHAR) - 1

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

        private fun String.trigramFitsInt(): Boolean {
            return this[0].code <= TRIGRAM_AS_INT_MAX_CHAR
                && this[1].code <= TRIGRAM_AS_INT_MAX_CHAR
                && this[2].code <= TRIGRAM_AS_INT_MAX_CHAR
        }

        private fun String.trigramToInt(): Int {
            return (
                this[0].code
                or (this[1].code shl TRIGRAM_AS_INT_BITS_PER_CHAR)
                or (this[2].code shl (TRIGRAM_AS_INT_BITS_PER_CHAR * 2))
            )
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
                val unigramsAsByteSize = it.readInt()
                val unigramsAsByte = Byte2IntOpenHashMap(unigramsAsByteSize, loadFactor)
                for (i in 1..unigramsAsByteSize) {
                    unigramsAsByte.put(it.readByte(), it.readInt())
                }

                val unigramsAsCharSize = it.readInt()
                val unigramsAsChar = Char2IntOpenHashMap(unigramsAsCharSize, loadFactor)
                for (i in 1..unigramsAsCharSize) {
                    unigramsAsChar.put(it.readChar(), it.readInt())
                }

                val bigramsAsShortSize = it.readInt()
                val bigramsAsShort = Short2IntOpenHashMap(bigramsAsShortSize, loadFactor)
                for (i in 1..bigramsAsShortSize) {
                    bigramsAsShort.put(it.readShort(), it.readInt())
                }

                val bigramsAsIntSize = it.readInt()
                val bigramsAsInt = Int2IntOpenHashMap(bigramsAsIntSize, loadFactor)
                for (i in 1..bigramsAsIntSize) {
                    bigramsAsInt.put(it.readInt(), it.readInt())
                }

                val trigramsAsIntSize = it.readInt()
                val trigramsAsInt = Int2IntOpenHashMap(trigramsAsIntSize, loadFactor)
                for (i in 1..trigramsAsIntSize) {
                    trigramsAsInt.put(it.readInt(), it.readInt())
                }

                val trigramsAsLongSize = it.readInt()
                val trigramsAsLong = Long2IntOpenHashMap(trigramsAsLongSize, loadFactor)
                for (i in 1..trigramsAsLongSize) {
                    trigramsAsLong.put(it.readLong(), it.readInt())
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
        Byte2IntOpenHashMap(initialCapacity, loadFactor),
        Char2IntOpenHashMap(initialCapacity, loadFactor),
        Short2IntOpenHashMap(initialCapacity, loadFactor),
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        Long2IntOpenHashMap(initialCapacity, loadFactor)
    )

    override fun putFrequency(ngram: String, encodedFrequency: Int) {
        when (ngram.length) {
            1 -> {
                val char0 = ngram[0].code
                when {
                    char0 <= 255 -> unigramsAsByte[char0.toByte()] = encodedFrequency
                    else -> unigramsAsChar[char0.toChar()] = encodedFrequency
                }
            }
            2 -> when {
                ngram.bigramFitsShort() -> bigramsAsShort[ngram.bigramToShort()] = encodedFrequency
                else -> bigramsAsInt[ngram.bigramToInt()] = encodedFrequency
            }
            3 -> when {
                ngram.trigramFitsInt() -> trigramsAsInt[ngram.trigramToInt()] = encodedFrequency
                else -> trigramsAsLong[ngram.trigramToLong()] = encodedFrequency
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
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
        return FrequencyLookupBuilder.decodeFraction(when (ngram.getEncodingType()) {
            UNIGRAM_AS_BYTE -> unigramsAsByte[ngram.unigramToByte()]
            UNIGRAM_AS_CHAR -> unigramsAsChar[ngram.unigramToChar()]
            BIGRAM_AS_SHORT -> bigramsAsShort[ngram.bigramToShort()]
            BIGRAM_AS_INT -> bigramsAsInt[ngram.bigramToInt()]
            TRIGRAM_AS_INT -> trigramsAsInt[ngram.trigramToInt()]
            TRIGRAM_AS_LONG -> trigramsAsLong[ngram.trigramToLong()]
            else -> throw AssertionError("Unknown encoding type")
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

        FrequencyLookupBuilder.openBinaryDataOutput(resourcesDirectory, resourceName).use { dataOut ->
            dataOut.writeInt(unigramsAsByte.size)
            unigramsAsByte.byte2IntEntrySet().fastForEach {
                dataOut.writeByte(it.byteKey.toInt())
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(unigramsAsChar.size)
            unigramsAsChar.char2IntEntrySet().fastForEach {
                dataOut.writeChar(it.charKey.code)
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(bigramsAsShort.size)
            bigramsAsShort.short2IntEntrySet().fastForEach {
                dataOut.writeShort(it.shortKey.toInt())
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(bigramsAsInt.size)
            bigramsAsInt.int2IntEntrySet().fastForEach {
                dataOut.writeInt(it.intKey)
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(trigramsAsInt.size)
            trigramsAsInt.int2IntEntrySet().fastForEach {
                dataOut.writeInt(it.intKey)
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(trigramsAsLong.size)
            trigramsAsLong.long2IntEntrySet().fastForEach {
                dataOut.writeLong(it.longKey)
                dataOut.writeInt(it.intValue)
            }
        }
    }
}

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal class QuadriFivegramRelativeFrequencyLookup private constructor(
    private val quadrigramsAsInt: Int2IntOpenHashMap,
    private val quadrigramsAsLong: Long2IntOpenHashMap,
    private val fivegramsAsLong: Long2IntOpenHashMap,
    private val fivegramsAsObject: Object2IntOpenHashMap<String>
) : FrequencyLookupBuilder {
    companion object {
        val empty = QuadriFivegramRelativeFrequencyLookup()

        private const val loadFactor = 0.9f
        private const val initialCapacity = 16

        /**
         * Number of bits per fivegram char used to encode the signed offset compared to
         * the first char (at index 0).
         */
        private const val FIVEGRAM_OFFSET_BITS_PER_CHAR = (Long.SIZE_BITS - Char.SIZE_BITS) / 4

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

            return (
                char0
                or ((this[1].code.toLong() - char0) shl FIVEGRAM_OFFSET_BITS_PER_CHAR)
                or ((this[2].code.toLong() - char0) shl (FIVEGRAM_OFFSET_BITS_PER_CHAR * 2))
                or ((this[3].code.toLong() - char0) shl (FIVEGRAM_OFFSET_BITS_PER_CHAR * 3))
                or ((this[4].code.toLong() - char0) shl (FIVEGRAM_OFFSET_BITS_PER_CHAR * 4))
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
                val quadrigramsAsIntSize = it.readInt()
                val quadrigramsAsInt = Int2IntOpenHashMap(quadrigramsAsIntSize, loadFactor)
                for (i in 1..quadrigramsAsIntSize) {
                    quadrigramsAsInt.put(it.readInt(), it.readInt())
                }

                val quadrigramsAsLongSize = it.readInt()
                val quadrigramsAsLong = Long2IntOpenHashMap(quadrigramsAsLongSize, loadFactor)
                for (i in 1..quadrigramsAsLongSize) {
                    quadrigramsAsLong.put(it.readLong(), it.readInt())
                }

                val fivegramsAsLongSize = it.readInt()
                val fivegramsAsLong = Long2IntOpenHashMap(fivegramsAsLongSize, loadFactor)
                for (i in 1..fivegramsAsLongSize) {
                    fivegramsAsLong.put(it.readLong(), it.readInt())
                }

                val fivegramsAsObjectSize = it.readInt()
                val fivegramsAsObject = Object2IntOpenHashMap<String>(fivegramsAsObjectSize, loadFactor)
                for (i in 1..fivegramsAsObjectSize) {
                    val charArray = CharArray(5)
                    charArray[0] = it.readChar()
                    charArray[1] = it.readChar()
                    charArray[2] = it.readChar()
                    charArray[3] = it.readChar()
                    charArray[4] = it.readChar()
                    val fivegram = String(charArray)

                    fivegramsAsObject.put(fivegram, it.readInt())
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
        Int2IntOpenHashMap(initialCapacity, loadFactor),
        Long2IntOpenHashMap(initialCapacity, loadFactor),
        Long2IntOpenHashMap(initialCapacity, loadFactor),
        Object2IntOpenHashMap<String>(initialCapacity, loadFactor)
    )

    override fun putFrequency(ngram: String, encodedFrequency: Int) {
        when (ngram.length) {
            4 -> when {
                ngram.quadrigramFitsInt() -> quadrigramsAsInt[ngram.quadrigramToInt()] = encodedFrequency
                else -> quadrigramsAsLong[ngram.quadrigramToLong()] = encodedFrequency
            }
            5 -> when {
                ngram.fivegramFitsLong() -> fivegramsAsLong[ngram.fivegramToLong()] = encodedFrequency
                // Fall back to storing Ngram object
                else -> fivegramsAsObject[ngram] = encodedFrequency
            }
            else -> throw IllegalArgumentException("Invalid Ngram length")
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

        return FrequencyLookupBuilder.decodeFraction(when (ngramStr.length) {
            4 -> when {
                ngramStr.quadrigramFitsInt() -> quadrigramsAsInt[ngramStr.quadrigramToInt()]
                else -> quadrigramsAsLong[ngramStr.quadrigramToLong()]
            }
            5 -> when {
                ngramStr.fivegramFitsLong() -> fivegramsAsLong[ngramStr.fivegramToLong()]
                else -> fivegramsAsObject.getInt(ngramStr)
            }
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

        FrequencyLookupBuilder.openBinaryDataOutput(resourcesDirectory, resourceName).use { dataOut ->
            dataOut.writeInt(quadrigramsAsInt.size)
            quadrigramsAsInt.int2IntEntrySet().fastForEach {
                dataOut.writeInt(it.intKey)
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(quadrigramsAsLong.size)
            quadrigramsAsLong.long2IntEntrySet().fastForEach {
                dataOut.writeLong(it.longKey)
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(fivegramsAsLong.size)
            fivegramsAsLong.long2IntEntrySet().fastForEach {
                dataOut.writeLong(it.longKey)
                dataOut.writeInt(it.intValue)
            }

            dataOut.writeInt(fivegramsAsObject.size)
            fivegramsAsObject.object2IntEntrySet().fastForEach {
                val ngram = it.key
                // Write String manually since length is known (= 5) so don't have to store it
                dataOut.writeChar(ngram[0].code)
                dataOut.writeChar(ngram[1].code)
                dataOut.writeChar(ngram[2].code)
                dataOut.writeChar(ngram[3].code)
                dataOut.writeChar(ngram[4].code)

                dataOut.writeInt(it.intValue)
            }
        }
    }
}
