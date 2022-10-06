package com.github.pemistahl.lingua.internal.model.lookup

import com.github.pemistahl.lingua.internal.model.CharOffsetsData
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableFivegram2FloatMap
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableInt2FloatTrieMap
import com.github.pemistahl.lingua.internal.model.floatmap.ImmutableLong2FloatMap
import it.unimi.dsi.fastutil.chars.Char2ShortMaps
import it.unimi.dsi.fastutil.ints.Int2FloatFunction
import it.unimi.dsi.fastutil.ints.Int2FloatMaps
import it.unimi.dsi.fastutil.longs.Long2FloatFunction
import it.unimi.dsi.fastutil.longs.Long2FloatMaps
import it.unimi.dsi.fastutil.objects.Object2FloatFunction
import it.unimi.dsi.fastutil.objects.Object2FloatLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2FloatMaps
import java.nio.file.Path

/**
 * Frequency lookup for quadri- and fivegrams.
 */
internal open class QuadriFivegramLookup(
    private val charOffsetsData: CharOffsetsData,
    private val quadrigramsAsInt: Int2FloatFunction,
    private val quadrigramsAsLong: Long2FloatFunction,
    private val fivegramsAsInt: Int2FloatFunction,
    private val fivegramsAsLong: Long2FloatFunction,
    private val fivegramsAsString: Object2FloatFunction<String>,
) {
    companion object {
        val empty = QuadriFivegramLookup(
            CharOffsetsData(Char2ShortMaps.EMPTY_MAP),
            Int2FloatMaps.EMPTY_MAP,
            Long2FloatMaps.EMPTY_MAP,
            Int2FloatMaps.EMPTY_MAP,
            Long2FloatMaps.EMPTY_MAP,
            Object2FloatMaps.emptyMap(),
        )
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
                { fivegramsAsString.getFloat(it) },
                { 0f }
            )
            else -> throw IllegalArgumentException("Invalid Ngram length")
        }
        return frequency.toDouble()
    }
}

/**
 * Binary search based lookup using custom Map implementations.
 */
internal class QuadriFivegramBinarySearchLookup private constructor(
    private val charOffsetsData: CharOffsetsData,
    private val quadrigramsAsInt: ImmutableInt2FloatTrieMap,
    private val quadrigramsAsLong: ImmutableLong2FloatMap,
    private val fivegramsAsInt: ImmutableInt2FloatTrieMap,
    private val fivegramsAsLong: ImmutableLong2FloatMap,
    private val fivegramsAsString: ImmutableFivegram2FloatMap,
) : QuadriFivegramLookup(
    charOffsetsData,
    quadrigramsAsInt,
    quadrigramsAsLong,
    fivegramsAsInt,
    fivegramsAsLong,
    fivegramsAsString,
) {
    companion object {
        @Suppress("unused") // used by buildSrc for model generation
        fun fromJson(
            quadrigrams: Object2FloatLinkedOpenHashMap<String>,
            fivegrams: Object2FloatLinkedOpenHashMap<String>
        ): QuadriFivegramBinarySearchLookup {
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

        private fun getBinaryModelResourceName(languageCode: String): String {
            return getBinaryModelResourceName(languageCode, "quadri-fivegrams.bin")
        }

        fun fromBinary(languageCode: String): QuadriFivegramBinarySearchLookup {
            openBinaryDataInput(getBinaryModelResourceName(languageCode)).use {
                val charOffsetsData = CharOffsetsData.fromBinary(it)

                val quadrigramsAsInt = ImmutableInt2FloatTrieMap.fromBinary(it)
                val quadrigramsAsLong = ImmutableLong2FloatMap.fromBinary(it)

                val fivegramsAsInt = ImmutableInt2FloatTrieMap.fromBinary(it)
                val fivegramsAsLong = ImmutableLong2FloatMap.fromBinary(it)
                val fivegramsAsObject = ImmutableFivegram2FloatMap.fromBinary(it)

                // Should have reached end of data
                check(it.read() == -1)

                return QuadriFivegramBinarySearchLookup(
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

    class Builder(
        private val charOffsetsData: CharOffsetsData,
    ) {
        private val quadrigramsAsIntBuilder = ImmutableInt2FloatTrieMap.Builder()
        private val quadrigramsAsLongBuilder = ImmutableLong2FloatMap.Builder()
        private val fivegramsAsIntBuilder = ImmutableInt2FloatTrieMap.Builder()
        private val fivegramsAsLongBuilder = ImmutableLong2FloatMap.Builder()
        private val fivegramsAsStringBuilder = ImmutableFivegram2FloatMap.Builder()

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
                { fivegramsAsStringBuilder.add(it, frequency) },
                { throw AssertionError("Char offsets don't include chars of: $fivegram") }
            )
        }

        fun finishCreation(): QuadriFivegramBinarySearchLookup {
            return QuadriFivegramBinarySearchLookup(
                charOffsetsData,
                quadrigramsAsIntBuilder.build(),
                quadrigramsAsLongBuilder.build(),
                fivegramsAsIntBuilder.build(),
                fivegramsAsLongBuilder.build(),
                fivegramsAsStringBuilder.build()
            )
        }
    }

    fun asHashMapLookup() = QuadriFivegramLookup(
        charOffsetsData,
        quadrigramsAsInt.asHashMap(),
        quadrigramsAsLong.asHashMap(),
        fivegramsAsInt.asHashMap(),
        fivegramsAsLong.asHashMap(),
        fivegramsAsString.asHashMap(),
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

            quadrigramsAsInt.writeBinary(it)
            quadrigramsAsLong.writeBinary(it)

            fivegramsAsInt.writeBinary(it)
            fivegramsAsLong.writeBinary(it)
            fivegramsAsString.writeBinary(it)
        }

        return filePath
    }
}
