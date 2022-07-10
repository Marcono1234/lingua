package com.github.pemistahl.lingua.internal.model

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

private fun openResourceInputStream(resourcePath: String): InputStream {
    return UniBiTrigramRelativeFrequencyLookup::class.java.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Resource '$resourcePath' does not exist")
}

private fun openBinaryDataInput(resourcePath: String): DataInputStream {
    return DataInputStream(openResourceInputStream(resourcePath).buffered())
}

private data class FileDataOutput(val filePath: Path, val dataOut: DataOutputStream)
private fun openBinaryDataOutput(
    resourcesDirectory: Path,
    resourcePath: String,
    changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
): FileDataOutput {
    val file = resourcesDirectory.resolve(resourcePath.removePrefix("/"))
    Files.createDirectories(file.parent)

    val oldSizeBytes = if (Files.isRegularFile(file)) Files.size(file) else null
    val dataOut = object : DataOutputStream(Files.newOutputStream(file).buffered()) {
        override fun close() {
            super.close()
            val newSizeBytes = Files.size(file)
            changeSummaryCallback(oldSizeBytes, newSizeBytes)
        }
    }

    return FileDataOutput(file, dataOut)
}

private fun getBinaryModelResourceName(languageCode: String, fileName: String): String {
    return "/language-models/$languageCode/$fileName"
}

/**
 * Multiply frequency, which is in range (0.0, 1.0), with UInt.MAX_VALUE + 1 to
 * map the decimal places to a 32-bit integer.
 */
private const val ENCODING_MULTIPLIER = 1L shl 32

// TODO: Check if custom encoding is really worth it (compare accuracy reports)
internal fun encodeFrequency(numerator: Int, denominator: Int): Int {
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
    private val charOffsetsData: CharOffsetsData,
    private val unigramsAsByte: ImmutableByte2IntMap,
    private val unigramsAsShort: ImmutableShort2IntMap,
    private val bigramsAsShort: ImmutableShort2IntMap,
    private val bigramsAsInt: ImmutableInt2IntTrieMap,
    private val trigramsAsInt: ImmutableInt2IntTrieMap,
    private val trigramsAsLong: ImmutableLong2IntMap,
) {
    companion object {
        @Suppress("unused") // used by buildSrc for model generation
        fun fromJson(
            unigrams: Object2IntOpenHashMap<String>,
            bigrams: Object2IntOpenHashMap<String>,
            trigrams: Object2IntOpenHashMap<String>
        ): UniBiTrigramRelativeFrequencyLookup {
            val charOffsetsData = CharOffsetsData.createCharOffsetsData(unigrams, bigrams, trigrams)

            val builder = Builder(charOffsetsData)
            unigrams.object2IntEntrySet().fastForEach {
                builder.putUnigramFrequency(it.key, it.intValue)
            }
            bigrams.object2IntEntrySet().fastForEach {
                builder.putBigramFrequency(it.key, it.intValue)
            }
            trigrams.object2IntEntrySet().fastForEach {
                builder.putTrigramFrequency(it.key, it.intValue)
            }
            return builder.finishCreation()
        }

        private fun getBinaryModelResourceName(languageCode: String): String {
            return getBinaryModelResourceName(languageCode, "uni-bi-trigrams.bin")
        }

        fun fromBinary(languageCode: String): UniBiTrigramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(languageCode)).use {
                val charOffsetsData = CharOffsetsData.fromBinary(it)

                val unigramsAsByte = ImmutableByte2IntMap.fromBinary(it)
                val unigramsAsShort = ImmutableShort2IntMap.fromBinary(it)

                val bigramsAsShort = ImmutableShort2IntMap.fromBinary(it)
                val bigramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)

                val trigramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)
                val trigramsAsLong = ImmutableLong2IntMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return UniBiTrigramRelativeFrequencyLookup(
                    charOffsetsData,
                    unigramsAsByte,
                    unigramsAsShort,
                    bigramsAsShort,
                    bigramsAsInt,
                    trigramsAsInt,
                    trigramsAsLong
                )
            }
        }
    }

    private class Builder(
        private val charOffsetsData: CharOffsetsData,
    ) {
        private val unigramsAsByteBuilder = ImmutableByte2IntMap.Builder()
        private val unigramsAsShortBuilder = ImmutableShort2IntMap.Builder()
        private val bigramsAsShortBuilder = ImmutableShort2IntMap.Builder()
        private val bigramsAsIntBuilder = ImmutableInt2IntTrieMap.Builder()
        private val trigramsAsIntBuilder = ImmutableInt2IntTrieMap.Builder()
        private val trigramsAsLongBuilder = ImmutableLong2IntMap.Builder()

        fun putUnigramFrequency(unigram: String, encodedFrequency: Int) {
            if (encodedFrequency == 0) {
                throw AssertionError("Invalid encoded frequency 0 for ngram '$unigram'")
            }
            if (unigram.length != 1) {
                throw IllegalArgumentException("Invalid ngram length ${unigram.length}")
            }

            charOffsetsData.useEncodedUnigram(
                unigram,
                { unigramsAsByteBuilder.add(it, encodedFrequency) },
                { unigramsAsShortBuilder.add(it, encodedFrequency) },
                { throw AssertionError("Char offsets don't include chars of: $unigram") }
            )
        }

        fun putBigramFrequency(bigram: String, encodedFrequency: Int) {
            if (encodedFrequency == 0) {
                throw AssertionError("Invalid encoded frequency 0 for ngram '$bigram'")
            }
            if (bigram.length != 2) {
                throw IllegalArgumentException("Invalid ngram length ${bigram.length}")
            }

            charOffsetsData.useEncodedBigram(
                bigram,
                { bigramsAsShortBuilder.add(it, encodedFrequency) },
                { bigramsAsIntBuilder.add(it, encodedFrequency) },
                { throw AssertionError("Char offsets don't include chars of: $bigram") }
            )
        }

        fun putTrigramFrequency(trigram: String, encodedFrequency: Int) {
            if (encodedFrequency == 0) {
                throw AssertionError("Invalid encoded frequency 0 for ngram '$trigram'")
            }
            if (trigram.length != 3) {
                throw IllegalArgumentException("Invalid ngram length ${trigram.length}")
            }

            charOffsetsData.useEncodedTrigram(
                trigram,
                { trigramsAsIntBuilder.add(it, encodedFrequency) },
                { trigramsAsLongBuilder.add(it, encodedFrequency) },
                { throw AssertionError("Char offsets don't include chars of: $trigram") }
            )
        }

        fun finishCreation(): UniBiTrigramRelativeFrequencyLookup {
            return UniBiTrigramRelativeFrequencyLookup(
                charOffsetsData,
                unigramsAsByteBuilder.build(),
                unigramsAsShortBuilder.build(),
                bigramsAsShortBuilder.build(),
                bigramsAsIntBuilder.build(),
                trigramsAsIntBuilder.build(),
                trigramsAsLongBuilder.build()
            )
        }
    }

    // Note: Effectively this is a destructured PrimitiveNgram, but to keep number of classes for buildSrc
    // binary model task low, avoid dependency on other class (in other package)
    fun getFrequency(length: Int, char0: Char, char1: Char, char2: Char): Double {
        return decodeFrequency(
            when (length) {
                1 -> charOffsetsData.useEncodedUnigram(
                    char0,
                    { unigramsAsByte.get(it) },
                    { unigramsAsShort.get(it) },
                    { 0 }
                )
                2 -> charOffsetsData.useEncodedBigram(
                    char0, char1,
                    { bigramsAsShort.get(it) },
                    { bigramsAsInt.get(it) },
                    { 0 }
                )
                3 -> charOffsetsData.useEncodedTrigram(
                    char0, char1, char2,
                    { trigramsAsInt.get(it) },
                    { trigramsAsLong.get(it) },
                    { 0 }
                )
                else -> throw AssertionError("Invalid ngram length $length")
            }
        )
    }

    /**
     * Writes the binary representation of the model data to a sub directory for
     * the specified language of the resources directory.
     *
     * @see fromBinary
     */
    @Suppress("unused") // used by buildSrc for model generation
    fun writeBinary(
        resourcesDirectory: Path,
        languageCode: String,
        changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
    ): Path {
        val resourceName = getBinaryModelResourceName(languageCode)

        val (filePath, dataOut) = openBinaryDataOutput(resourcesDirectory, resourceName, changeSummaryCallback)
        dataOut.use {
            charOffsetsData.writeBinary(it)

            unigramsAsByte.writeBinary(it)
            unigramsAsShort.writeBinary(it)

            bigramsAsShort.writeBinary(it)
            bigramsAsInt.writeBinary(it)

            trigramsAsInt.writeBinary(it)
            trigramsAsLong.writeBinary(it)
        }

        return filePath
    }
}

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal class QuadriFivegramRelativeFrequencyLookup private constructor(
    private val charOffsetsData: CharOffsetsData,
    private val quadrigramsAsInt: ImmutableInt2IntTrieMap,
    private val quadrigramsAsLong: ImmutableLong2IntMap,
    private val fivegramsAsInt: ImmutableInt2IntTrieMap,
    private val fivegramsAsLong: ImmutableLong2IntMap,
    private val fivegramsAsObject: ImmutableFivegram2IntMap,
) {
    companion object {
        val empty = QuadriFivegramRelativeFrequencyLookup(
            CharOffsetsData(CharArray(0), ShortArray(0)),
            ImmutableInt2IntTrieMap.Builder().build(),
            ImmutableLong2IntMap.Builder().build(),
            ImmutableInt2IntTrieMap.Builder().build(),
            ImmutableLong2IntMap.Builder().build(),
            ImmutableFivegram2IntMap.Builder().build(),
        )

        @Suppress("unused") // used by buildSrc for model generation
        fun fromJson(
            quadrigrams: Object2IntOpenHashMap<String>,
            fivegrams: Object2IntOpenHashMap<String>
        ): QuadriFivegramRelativeFrequencyLookup {
            val charOffsetsData = CharOffsetsData.createCharOffsetsData(quadrigrams, fivegrams)
            val builder = Builder(charOffsetsData)

            quadrigrams.object2IntEntrySet().fastForEach {
                builder.putQuadrigramFrequency(it.key, it.intValue)
            }
            fivegrams.object2IntEntrySet().fastForEach {
                builder.putFivegramFrequency(it.key, it.intValue)
            }
            return builder.finishCreation()
        }

        private fun getBinaryModelResourceName(languageCode: String): String {
            return getBinaryModelResourceName(languageCode, "quadri-fivegrams.bin")
        }

        fun fromBinary(languageCode: String): QuadriFivegramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(languageCode)).use {
                val charOffsetsData = CharOffsetsData.fromBinary(it)

                val quadrigramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)
                val quadrigramsAsLong = ImmutableLong2IntMap.fromBinary(it)

                val fivegramsAsInt = ImmutableInt2IntTrieMap.fromBinary(it)
                val fivegramsAsLong = ImmutableLong2IntMap.fromBinary(it)
                val fivegramsAsObject = ImmutableFivegram2IntMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return QuadriFivegramRelativeFrequencyLookup(
                    charOffsetsData,
                    quadrigramsAsInt,
                    quadrigramsAsLong,
                    fivegramsAsInt,
                    fivegramsAsLong,
                    fivegramsAsObject
                )
            }
        }
    }

    private class Builder(
        private val charOffsetsData: CharOffsetsData,
    ) {
        private val quadrigramsAsIntBuilder = ImmutableInt2IntTrieMap.Builder()
        private val quadrigramsAsLongBuilder = ImmutableLong2IntMap.Builder()
        private val fivegramsAsIntBuilder = ImmutableInt2IntTrieMap.Builder()
        private val fivegramsAsLongBuilder = ImmutableLong2IntMap.Builder()
        private val fivegramsAsObjectBuilder = ImmutableFivegram2IntMap.Builder()

        fun putQuadrigramFrequency(quadrigram: String, encodedFrequency: Int) {
            if (encodedFrequency == 0) {
                throw AssertionError("Invalid encoded frequency 0 for ngram '$quadrigram'")
            }
            if (quadrigram.length != 4) {
                throw IllegalArgumentException("Invalid ngram length ${quadrigram.length}")
            }

            charOffsetsData.useEncodedQuadrigram(
                quadrigram,
                { quadrigramsAsIntBuilder.add(it, encodedFrequency) },
                { quadrigramsAsLongBuilder.add(it, encodedFrequency) },
                { throw AssertionError("Char offsets don't include chars of: $quadrigram") }
            )
        }

        fun putFivegramFrequency(fivegram: String, encodedFrequency: Int) {
            if (encodedFrequency == 0) {
                throw AssertionError("Invalid encoded frequency 0 for ngram '$fivegram'")
            }
            if (fivegram.length != 5) {
                throw IllegalArgumentException("Invalid ngram length ${fivegram.length}")
            }

            charOffsetsData.useEncodedFivegram(
                fivegram,
                { fivegramsAsIntBuilder.add(it, encodedFrequency) },
                { fivegramsAsLongBuilder.add(it, encodedFrequency) },
                { fivegramsAsObjectBuilder.add(it, encodedFrequency) },
                { throw AssertionError("Char offsets don't include chars of: $fivegram") }
            )
        }

        fun finishCreation(): QuadriFivegramRelativeFrequencyLookup {
            return QuadriFivegramRelativeFrequencyLookup(
                charOffsetsData,
                quadrigramsAsIntBuilder.build(),
                quadrigramsAsLongBuilder.build(),
                fivegramsAsIntBuilder.build(),
                fivegramsAsLongBuilder.build(),
                fivegramsAsObjectBuilder.build()
            )
        }
    }

    fun getFrequency(ngram: String): Double {
        return decodeFrequency(
            when (ngram.length) {
                4 -> charOffsetsData.useEncodedQuadrigram(
                    ngram,
                    { quadrigramsAsInt.get(it) },
                    { quadrigramsAsLong.get(it) },
                    { 0 }
                )
                5 -> charOffsetsData.useEncodedFivegram(
                    ngram,
                    { fivegramsAsInt.get(it) },
                    { fivegramsAsLong.get(it) },
                    { fivegramsAsObject.get(it) },
                    { 0 }
                )
                else -> throw IllegalArgumentException("Invalid Ngram length")
            }
        )
    }

    /**
     * Writes the binary representation of the model data to a sub directory for
     * the specified language of the resources directory.
     *
     * @see fromBinary
     */
    @Suppress("unused") // used by buildSrc for model generation
    fun writeBinary(
        resourcesDirectory: Path,
        languageCode: String,
        changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
    ): Path {
        val resourceName = getBinaryModelResourceName(languageCode)

        val (filePath, dataOut) = openBinaryDataOutput(resourcesDirectory, resourceName, changeSummaryCallback)
        dataOut.use {
            charOffsetsData.writeBinary(it)

            quadrigramsAsInt.writeBinary(it)
            quadrigramsAsLong.writeBinary(it)

            fivegramsAsInt.writeBinary(it)
            fivegramsAsLong.writeBinary(it)
            fivegramsAsObject.writeBinary(it)
        }

        return filePath
    }
}
