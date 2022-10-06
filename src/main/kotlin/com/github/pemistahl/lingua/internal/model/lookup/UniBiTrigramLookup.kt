package com.github.pemistahl.lingua.internal.model.lookup

import com.github.pemistahl.lingua.internal.model.CharOffsetsData
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableByte2FloatMap
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableInt2FloatTrieMap
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableLong2FloatMap
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableShort2FloatMap
import it.unimi.dsi.fastutil.bytes.Byte2FloatFunction
import it.unimi.dsi.fastutil.ints.Int2FloatFunction
import it.unimi.dsi.fastutil.longs.Long2FloatFunction
import it.unimi.dsi.fastutil.objects.Object2FloatLinkedOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2FloatFunction
import java.nio.file.Path

/**
 * Frequency lookup for uni-, bi- and trigrams.
 */
internal open class UniBiTrigramLookup(
    private val charOffsetsData: CharOffsetsData,
    private val unigramsAsByte: Byte2FloatFunction,
    private val unigramsAsShort: Short2FloatFunction,
    private val bigramsAsShort: Short2FloatFunction,
    private val bigramsAsInt: Int2FloatFunction,
    private val trigramsAsInt: Int2FloatFunction,
    private val trigramsAsLong: Long2FloatFunction,
) {
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
}

/**
 * Binary search based lookup using custom Map implementations.
 */
internal class UniBiTrigramBinarySearchLookup private constructor(
    private val charOffsetsData: CharOffsetsData,
    private val unigramsAsByte: ImmutableByte2FloatMap,
    private val unigramsAsShort: ImmutableShort2FloatMap,
    private val bigramsAsShort: ImmutableShort2FloatMap,
    private val bigramsAsInt: ImmutableInt2FloatTrieMap,
    private val trigramsAsInt: ImmutableInt2FloatTrieMap,
    private val trigramsAsLong: ImmutableLong2FloatMap,
) : UniBiTrigramLookup(
    charOffsetsData,
    unigramsAsByte,
    unigramsAsShort,
    bigramsAsShort,
    bigramsAsInt,
    trigramsAsInt,
    trigramsAsLong,
) {
    companion object {
        @Suppress("unused") // used by buildSrc for model generation
        fun fromJson(
            unigrams: Object2FloatLinkedOpenHashMap<String>,
            bigrams: Object2FloatLinkedOpenHashMap<String>,
            trigrams: Object2FloatLinkedOpenHashMap<String>
        ): UniBiTrigramBinarySearchLookup {
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

        private fun getBinaryModelResourceName(languageCode: String): String {
            return getBinaryModelResourceName(languageCode, "uni-bi-trigrams.bin")
        }

        fun fromBinary(languageCode: String): UniBiTrigramBinarySearchLookup {
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

                return UniBiTrigramBinarySearchLookup(
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

        fun finishCreation(): UniBiTrigramBinarySearchLookup {
            return UniBiTrigramBinarySearchLookup(
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

    fun asHashMapLookup() = UniBiTrigramLookup(
        charOffsetsData,
        unigramsAsByte.asHashMap(),
        unigramsAsShort.asHashMap(),
        bigramsAsShort.asHashMap(),
        bigramsAsInt.asHashMap(),
        trigramsAsInt.asHashMap(),
        trigramsAsLong.asHashMap(),
    )

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
