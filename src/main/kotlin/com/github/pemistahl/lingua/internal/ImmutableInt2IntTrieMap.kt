package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.*
import it.unimi.dsi.fastutil.bytes.Byte2ObjectAVLTreeMap
import it.unimi.dsi.fastutil.bytes.Byte2ObjectFunction
import it.unimi.dsi.fastutil.bytes.Byte2ObjectSortedMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.shorts.Short2IntAVLTreeMap
import it.unimi.dsi.fastutil.shorts.Short2IntSortedMap
import java.io.*
import java.util.*
import kotlin.math.max
import kotlin.random.Random

/**
 * Map where the first bytes of the int key are encoded in a trie-like way. This kind of map is only implemented for
 * int key map because there it is easier to encode the first two bytes (16 bits total) separately, which leaves behind
 * a short (16 bit). Whereas for a long key map, it would leave behind 48 bits, for which no primitive exists, and
 * encoding more than 2 bytes separately might decrease the likelihood that common prefixes exist.
 * Additionally, the keys of Int2Int maps are currently taking up the most memory compared to the other map types.
 *
 * This implementation assumes that for a language all ngrams encoded as primitive int may share a common prefix, e.g.
 * because the first two bytes correspond to the first two chars of the ngram. This might not apply to all primitive
 * ngram encodings, so this map might not be applicable everywhere.
 *
 * The implementation works like this: The key is split in _first key_ (`byte`), _second key_ (`byte`) and
 * _key remainder_ (`short`). For a key _k_ of type `int` its first byte is looked up in the _first key_ layer. If
 * found, its second byte is looked up in the corresponding _second key_ layer (obtained base on the _first key_ index).
 * If found, this _second key_ index is used to obtain the _search data_. That search data encodes a key remainder
 * search index offset, and search size. This data is then used to select the range in the _key remainder_ layer where
 * to search for the _key remainder_. If found, the index is the global index which can be used to look up the value.
 *
 * If _first key_, or the combination of _first key_ and _second key_ occurs frequently in the keys, this can save up
 * a lot of memory because this common prefix only has to be encoded once.
 */
class ImmutableInt2IntTrieMap private constructor(
    private val size: Int,
    private val firstKeyLayer: ByteArray,
    /**
     * Global start indices (in [keyRemainderLayer]) for first keys; aids with global index estimation because
     * especially for large maps estimation only based on first key index and second key index may be off too
     * much, preventing index offset to be encoded in [keyRemainderLayerSearchData]
     */
    private val firstKeyGlobalIndices: IntArray,
    private val secondKeyLayers: Array<ByteArray>,
    private val keyRemainderLayerSearchData: Array<IntArray>,
    private val keyRemainderLayer: ShortArray,
    /**
     * For an index _i_ obtained based on the keys:
     * - if _i_ < [indValuesIndices]`.length`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else if `indValuesIndices.isEmpty()`: Look up value from `values[i]`
     * - else: Look up value from `values[i - indValuesIndices.length + maxIndirectionIndices]`
     */
    private val indValuesIndices: ShortArray,
    private val values: IntArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableInt2IntTrieMap {
            val size = inputStream.readInt()

            var firstKeyLayerSize = inputStream.readByte()
            // Check for overflow from 256 to 0
            if (size > 0 && firstKeyLayerSize == 0) firstKeyLayerSize = 256

            val firstKeyLayer = inputStream.readByteArray(firstKeyLayerSize)
            val firstKeyGlobalIndices = inputStream.readIntArray(firstKeyLayerSize)

            val secondKeyLayers = Array(firstKeyLayerSize) { ByteArray(0) }
            val keyRemainderLayerSearchData = Array(firstKeyLayerSize) { IntArray(0) }

            repeat(firstKeyLayerSize) {
                var secondKeyLayerSize = inputStream.readByte()
                // Check for overflow from 256 to 0; size cannot be 0 because then no entry in
                // firstKeyLayer would exist
                if (secondKeyLayerSize == 0) secondKeyLayerSize = 256

                secondKeyLayers[it] = inputStream.readByteArray(secondKeyLayerSize)
                keyRemainderLayerSearchData[it] = inputStream.readIntArray(secondKeyLayerSize)
            }

            val keyRemainderLayer = inputStream.readShortArray(size)

            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readIntArray(inputStream.readInt())

            return ImmutableInt2IntTrieMap(size, firstKeyLayer, firstKeyGlobalIndices, secondKeyLayers, keyRemainderLayerSearchData, keyRemainderLayer, indValuesIndices, values)
        }

        /**
         * Calculates the estimated index in [ImmutableInt2IntTrieMap.keyRemainderLayer]. The caller should then
         * determine the actual search range using [ImmutableInt2IntTrieMap.keyRemainderLayerSearchData].
         *
         * @param totalSize [ImmutableInt2IntTrieMap.size]
         * @param firstLayerGlobalIndex [firstKeyGlobalIndices]`[firstLayerIndex]`
         */
        private fun calculateEstimatedRemainderIndex(totalSize: Int, firstLayerGlobalIndex: Int, firstLayerSize: Int, secondLayerIndex: Int, secondLayerSize: Int): Int {
            val averageSizePerFirstLayer = totalSize / firstLayerSize.toDouble()
            // Use maximum to account for small second layers with previous second layers which may be way larger
            val averageSizePerSecondLayer = averageSizePerFirstLayer / max(firstLayerSize, secondLayerSize).toDouble()
            return (firstLayerGlobalIndex + secondLayerIndex * averageSizePerSecondLayer + (secondLayerSize / 2.0)).toInt()
        }

        /**
         * Number of bits used to encode the search size in [ImmutableInt2IntTrieMap.keyRemainderLayerSearchData].
         *
         * This value can be adjusted if necessary; there probably exists no perfect value which supports the
         * theoretical combination of maximum offset and maximum search size.
         */
        private const val SEARCH_DATA_SIZE_BITS_COUNT = 14
    }

    class Builder {
        private val map: Byte2ObjectSortedMap<Byte2ObjectSortedMap<Short2IntSortedMap>> = Byte2ObjectAVLTreeMap()

        fun add(key: Int, value: Int) {
            val firstKey = key.toByte()
            val secondKey = key.shr(8).toByte()
            val keyRemainder = key.shr(16).toShort()

            val old = map.computeIfAbsent(firstKey, Byte2ObjectFunction {Byte2ObjectAVLTreeMap()})
                .computeIfAbsent(secondKey, Byte2ObjectFunction {Short2IntAVLTreeMap()})
                .put(keyRemainder, value)
            check(old == 0)
        }

        fun build(): ImmutableInt2IntTrieMap {
            val allValues = map.values.stream()
                .flatMap { map -> map.values.stream() }
                .flatMapToInt { map -> map.values.intStream() }
                .toArray()

            val totalSize = allValues.size
            val firstKeyLayer = ByteArray(map.size)
            val firstKeyGlobalIndices = IntArray(map.size)
            val secondKeyLayers = Array(map.size) { ByteArray(0) }
            val keyRemainderLayerSearchData = Array(map.size) { IntArray(0) }
            val keyRemainderLayer = ShortArray(totalSize)

            var firstKeyIndex = 0
            var globalIndex = 0

            for (firstMapEntry in map.byte2ObjectEntrySet()) {
                val firstKey = firstMapEntry.byteKey
                val secondKeyMap = firstMapEntry.value

                firstKeyLayer[firstKeyIndex] = firstKey
                val secondKeyLayer = ByteArray(secondKeyMap.size)
                secondKeyLayers[firstKeyIndex] = secondKeyLayer
                val searchData = IntArray(secondKeyMap.size)
                keyRemainderLayerSearchData[firstKeyIndex] = searchData

                val firstKeyGlobalIndex = globalIndex
                firstKeyGlobalIndices[firstKeyIndex] = firstKeyGlobalIndex

                var secondKeyIndex = 0
                for (secondMapEntry in secondKeyMap.byte2ObjectEntrySet()) {
                    val secondKey = secondMapEntry.byteKey
                    val keyRemainderMap = secondMapEntry.value
                    secondKeyLayer[secondKeyIndex] = secondKey

                    val estimatedRemainderIndex = calculateEstimatedRemainderIndex(totalSize, firstKeyGlobalIndex, firstKeyLayer.size, secondKeyIndex, secondKeyLayer.size)
                    val indexDiff = globalIndex - estimatedRemainderIndex
                    // Search data encoding is rather brittle and might fail if estimated remainder index is way off,
                    // or if combination of firstKey and secondKey is an extremely common prefix
                    val indexDiffBitsCount = Int.SIZE_BITS - SEARCH_DATA_SIZE_BITS_COUNT
                    // Calculate bits count - 1 here because value is signed
                    check(indexDiff in -(1.shl(indexDiffBitsCount - 1)) until 1.shl(indexDiffBitsCount - 1))
                    check(keyRemainderMap.size in 1..1.shl(SEARCH_DATA_SIZE_BITS_COUNT))
                    val encodedSearchData = indexDiff.shl(SEARCH_DATA_SIZE_BITS_COUNT)
                        // size - 1, because map will never have size 0
                        .or(keyRemainderMap.size - 1)
                    searchData[secondKeyIndex] = encodedSearchData

                    keyRemainderMap.keys.forEach { keyRemainder ->
                        keyRemainderLayer[globalIndex] = keyRemainder
                        globalIndex++
                    }

                    secondKeyIndex++
                }

                firstKeyIndex++
            }

            return createValueArrays(IntArrayList.wrap(allValues)) { indValuesIndices, values ->
                return@createValueArrays ImmutableInt2IntTrieMap(totalSize, firstKeyLayer, firstKeyGlobalIndices, secondKeyLayers, keyRemainderLayerSearchData, keyRemainderLayer, indValuesIndices, values)
            }
        }
    }

    fun get(key: Int): Int {
        val firstKey = key.toByte()
        val firstKeyIndex = Arrays.binarySearch(firstKeyLayer, firstKey)
        if (firstKeyIndex < 0) return 0

        val secondKey = key.shr(8).toByte()
        val secondKeyLayer = secondKeyLayers[firstKeyIndex]
        val secondKeyIndex = Arrays.binarySearch(secondKeyLayer, secondKey)
        if (secondKeyIndex < 0) return 0

        // Determine where to search within keyRemainderLayer
        val estimatedRemainderIndex = calculateEstimatedRemainderIndex(size, firstKeyGlobalIndices[firstKeyIndex], firstKeyLayer.size, secondKeyIndex, secondKeyLayer.size)
        val remainderLayerSearchData = keyRemainderLayerSearchData[firstKeyIndex][secondKeyIndex]

        // shr instead of ushr to preserve sign
        val remainderSearchStartIndex = estimatedRemainderIndex + remainderLayerSearchData.shr(SEARCH_DATA_SIZE_BITS_COUNT)

        val remainderSearchSize = remainderLayerSearchData.and(1.shl(SEARCH_DATA_SIZE_BITS_COUNT) - 1) + 1 // + 1 because size cannot be 0

        val keyRemainder = key.shr(16).toShort()

        val index = Arrays.binarySearch(keyRemainderLayer, remainderSearchStartIndex, remainderSearchStartIndex + remainderSearchSize, keyRemainder)

        return if (index < 0) 0 else {
            if (index < indValuesIndices.size) values[indValuesIndices[index].toUShort().toInt()]
            else if (indValuesIndices.isEmpty()) values[index]
            else values[index - indValuesIndices.size + maxIndirectionIndices]
        }
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        dataOutput.writeInt(size)
        dataOutput.writeByte(firstKeyLayer.size)
        dataOutput.write(firstKeyLayer)
        firstKeyGlobalIndices.forEach(dataOutput::writeInt)

        secondKeyLayers.forEachIndexed { index, secondKeyLayer ->
            dataOutput.writeByte(secondKeyLayer.size)
            dataOutput.write(secondKeyLayer)
            keyRemainderLayerSearchData[index].forEach(dataOutput::writeInt)
        }
        keyRemainderLayer.forEach { dataOutput.writeShort(it.toInt()) }

        dataOutput.writeInt(indValuesIndices.size)
        indValuesIndices.forEach { dataOutput.writeShort(it.toInt()) }

        dataOutput.writeInt(values.size)
        values.forEach(dataOutput::writeInt)
    }
}

// TODO DEBUG
fun main() {
    var random = Random(0)
    val builder = ImmutableInt2IntTrieMap.Builder()

    val times = 79999
    repeat(times) {
        builder.add(random.nextInt(), it)
    }

    var map = builder.build()

    val out = ByteArrayOutputStream()
    map.writeBinary(out)
    println(out.size())

    val inStream = ByteArrayInputStream(out.toByteArray())
    map = ImmutableInt2IntTrieMap.fromBinary(inStream)
    check(inStream.read() == -1)


    random = Random(0)
    repeat(times) {
        val key = random.nextInt()
        val value = map.get(key)
        require(value == it) {
            "[$it]: Failed; $key, $value"
        }
    }
}
