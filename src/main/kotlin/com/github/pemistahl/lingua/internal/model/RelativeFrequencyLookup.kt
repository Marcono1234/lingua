package com.github.pemistahl.lingua.internal.model

import it.unimi.dsi.fastutil.chars.Char2ShortMaps
import it.unimi.dsi.fastutil.objects.Object2FloatLinkedOpenHashMap
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
    private val unigramsAsByte: ImmutableByte2FloatMap,
    private val unigramsAsShort: ImmutableShort2FloatMap,
    private val bigramsAsShort: ImmutableShort2FloatMap,
    private val bigramsAsInt: ImmutableInt2FloatTrieMap,
    private val trigramsAsInt: ImmutableInt2FloatTrieMap,
    private val trigramsAsLong: ImmutableLong2FloatMap,
) {
    companion object {
        @Suppress("unused") // used by buildSrc for model generation
        @JvmStatic
        fun fromJson(
            unigrams: Object2FloatLinkedOpenHashMap<String>,
            bigrams: Object2FloatLinkedOpenHashMap<String>,
            trigrams: Object2FloatLinkedOpenHashMap<String>
        ): UniBiTrigramRelativeFrequencyLookup {
            val ngrams = unigrams.keys.asSequence().plus(bigrams.keys).plus(trigrams.keys)
            val charOffsetsData = CharOffsetsData.createCharOffsetsData(ngrams)

            val builder = Builder(charOffsetsData)
            unigrams.object2FloatEntrySet().fastForEach {
                builder.putUnigramFrequency(it.key, it.floatValue)
            }
            bigrams.object2FloatEntrySet().fastForEach {
                builder.putBigramFrequency(it.key, it.floatValue)
            }
            trigrams.object2FloatEntrySet().fastForEach {
                builder.putTrigramFrequency(it.key, it.floatValue)
            }
            return builder.finishCreation()
        }

        @JvmStatic
        private fun getBinaryModelResourceName(languageCode: String): String {
            return getBinaryModelResourceName(languageCode, "uni-bi-trigrams.bin")
        }

        @JvmStatic
        fun fromBinary(languageCode: String): UniBiTrigramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(languageCode)).use {
                val charOffsetsData = CharOffsetsData.fromBinary(it)

                val unigramsAsByte = ImmutableByte2FloatMap.fromBinary(it)
                val unigramsAsShort = ImmutableShort2FloatMap.fromBinary(it)

                val bigramsAsShort = ImmutableShort2FloatMap.fromBinary(it)
                val bigramsAsInt = ImmutableInt2FloatTrieMap.fromBinary(it)

                val trigramsAsInt = ImmutableInt2FloatTrieMap.fromBinary(it)
                val trigramsAsLong = ImmutableLong2FloatMap.fromBinary(it)

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
        private val unigramsAsByteBuilder = ImmutableByte2FloatMap.Builder()
        private val unigramsAsShortBuilder = ImmutableShort2FloatMap.Builder()
        private val bigramsAsShortBuilder = ImmutableShort2FloatMap.Builder()
        private val bigramsAsIntBuilder = ImmutableInt2FloatTrieMap.Builder()
        private val trigramsAsIntBuilder = ImmutableInt2FloatTrieMap.Builder()
        private val trigramsAsLongBuilder = ImmutableLong2FloatMap.Builder()

        fun putUnigramFrequency(unigram: String, frequency: Float) {
            if (unigram.length != 1) {
                throw IllegalArgumentException("Invalid ngram length ${unigram.length}")
            }

            charOffsetsData.useEncodedUnigram(
                unigram,
                { unigramsAsByteBuilder.add(it, frequency) },
                { unigramsAsShortBuilder.add(it, frequency) },
                { throw AssertionError("Char offsets don't include chars of: $unigram") }
            )
        }

        fun putBigramFrequency(bigram: String, frequency: Float) {
            if (bigram.length != 2) {
                throw IllegalArgumentException("Invalid ngram length ${bigram.length}")
            }

            charOffsetsData.useEncodedBigram(
                bigram,
                { bigramsAsShortBuilder.add(it, frequency) },
                { bigramsAsIntBuilder.add(it, frequency) },
                { throw AssertionError("Char offsets don't include chars of: $bigram") }
            )
        }

        fun putTrigramFrequency(trigram: String, frequency: Float) {
            if (trigram.length != 3) {
                throw IllegalArgumentException("Invalid ngram length ${trigram.length}")
            }

            charOffsetsData.useEncodedTrigram(
                trigram,
                { trigramsAsIntBuilder.add(it, frequency) },
                { trigramsAsLongBuilder.add(it, frequency) },
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
        // Note: Explicitly specify type Float here to avoid accidentally having implicit type Number
        // (and therefore boxing) when one of the results is not a Float
        val frequency: Float = when (length) {
            1 -> charOffsetsData.useEncodedUnigram(
                char0,
                { unigramsAsByte.get(it) },
                { unigramsAsShort.get(it) },
                { 0f }
            )
            2 -> charOffsetsData.useEncodedBigram(
                char0, char1,
                { bigramsAsShort.get(it) },
                { bigramsAsInt.get(it) },
                { 0f }
            )
            3 -> charOffsetsData.useEncodedTrigram(
                char0, char1, char2,
                { trigramsAsInt.get(it) },
                { trigramsAsLong.get(it) },
                { 0f }
            )
            else -> throw AssertionError("Invalid ngram length $length")
        }
        return frequency.toDouble()
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
    private val quadrigramsAsInt: ImmutableInt2FloatTrieMap,
    private val quadrigramsAsLong: ImmutableLong2FloatMap,
    private val fivegramsAsInt: ImmutableInt2FloatTrieMap,
    private val fivegramsAsLong: ImmutableLong2FloatMap,
    private val fivegramsAsObject: ImmutableFivegram2FloatMap,
) {
    companion object {
        val empty = QuadriFivegramRelativeFrequencyLookup(
            CharOffsetsData(Char2ShortMaps.EMPTY_MAP),
            ImmutableInt2FloatTrieMap.Builder().build(),
            ImmutableLong2FloatMap.Builder().build(),
            ImmutableInt2FloatTrieMap.Builder().build(),
            ImmutableLong2FloatMap.Builder().build(),
            ImmutableFivegram2FloatMap.Builder().build(),
        )

        @Suppress("unused") // used by buildSrc for model generation
        @JvmStatic
        fun fromJson(
            quadrigrams: Object2FloatLinkedOpenHashMap<String>,
            fivegrams: Object2FloatLinkedOpenHashMap<String>
        ): QuadriFivegramRelativeFrequencyLookup {
            val ngrams = quadrigrams.keys.asSequence().plus(fivegrams.keys)
            val charOffsetsData = CharOffsetsData.createCharOffsetsData(ngrams)
            val builder = Builder(charOffsetsData)

            quadrigrams.object2FloatEntrySet().fastForEach {
                builder.putQuadrigramFrequency(it.key, it.floatValue)
            }
            fivegrams.object2FloatEntrySet().fastForEach {
                builder.putFivegramFrequency(it.key, it.floatValue)
            }
            return builder.finishCreation()
        }

        @JvmStatic
        private fun getBinaryModelResourceName(languageCode: String): String {
            return getBinaryModelResourceName(languageCode, "quadri-fivegrams.bin")
        }

        @JvmStatic
        fun fromBinary(languageCode: String): QuadriFivegramRelativeFrequencyLookup {
            openBinaryDataInput(getBinaryModelResourceName(languageCode)).use {
                val charOffsetsData = CharOffsetsData.fromBinary(it)

                val quadrigramsAsInt = ImmutableInt2FloatTrieMap.fromBinary(it)
                val quadrigramsAsLong = ImmutableLong2FloatMap.fromBinary(it)

                val fivegramsAsInt = ImmutableInt2FloatTrieMap.fromBinary(it)
                val fivegramsAsLong = ImmutableLong2FloatMap.fromBinary(it)
                val fivegramsAsObject = ImmutableFivegram2FloatMap.fromBinary(it)

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
        private val quadrigramsAsIntBuilder = ImmutableInt2FloatTrieMap.Builder()
        private val quadrigramsAsLongBuilder = ImmutableLong2FloatMap.Builder()
        private val fivegramsAsIntBuilder = ImmutableInt2FloatTrieMap.Builder()
        private val fivegramsAsLongBuilder = ImmutableLong2FloatMap.Builder()
        private val fivegramsAsObjectBuilder = ImmutableFivegram2FloatMap.Builder()

        fun putQuadrigramFrequency(quadrigram: String, frequency: Float) {
            if (quadrigram.length != 4) {
                throw IllegalArgumentException("Invalid ngram length ${quadrigram.length}")
            }

            charOffsetsData.useEncodedQuadrigram(
                quadrigram,
                { quadrigramsAsIntBuilder.add(it, frequency) },
                { quadrigramsAsLongBuilder.add(it, frequency) },
                { throw AssertionError("Char offsets don't include chars of: $quadrigram") }
            )
        }

        fun putFivegramFrequency(fivegram: String, frequency: Float) {
            if (fivegram.length != 5) {
                throw IllegalArgumentException("Invalid ngram length ${fivegram.length}")
            }

            charOffsetsData.useEncodedFivegram(
                fivegram,
                { fivegramsAsIntBuilder.add(it, frequency) },
                { fivegramsAsLongBuilder.add(it, frequency) },
                { fivegramsAsObjectBuilder.add(it, frequency) },
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

    // Note: Effectively this is a destructured ReusableObjectNgram, but to keep number of classes for buildSrc
    // binary model task low, avoid dependency on other class (in other package)
    inline fun getFrequency(
        length: Int,
        char0: Char,
        char1: Char,
        char2: Char,
        char3: Char,
        char4: Char,
        fivegramAsString: () -> String,
    ): Double {
        // Note: Explicitly specify type Float here to avoid accidentally having implicit type Number
        // (and therefore boxing) when one of the results is not a Float
        val frequency: Float = when (length) {
            4 -> charOffsetsData.useEncodedQuadrigram(
                char0, char1, char2, char3,
                { quadrigramsAsInt.get(it) },
                { quadrigramsAsLong.get(it) },
                { 0f }
            )
            5 -> charOffsetsData.useEncodedFivegram(
                char0, char1, char2, char3, char4,
                fivegramAsString,
                { fivegramsAsInt.get(it) },
                { fivegramsAsLong.get(it) },
                { fivegramsAsObject.get(it) },
                { 0f }
            )
            else -> throw IllegalArgumentException("Invalid Ngram length")
        }
        return frequency.toDouble()
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
