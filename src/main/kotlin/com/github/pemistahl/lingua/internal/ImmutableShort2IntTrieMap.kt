package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.*
import it.unimi.dsi.fastutil.bytes.*
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.*
import kotlin.random.Random

class ImmutableShort2IntTrieMap private constructor(
    private val firstByteLayer: ByteArray,
    private val secondByteLayers: Array<ByteArray>,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if [indValuesIndices]`.length > 0`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [indValues]
     * - else: Look up value from `values[i]`
     *
     * (This is a deviation from the other maps because for a short based map all keys fit inside [indValuesIndices],
     * so the question is only whether indirection reduces memory usage)
     */
    private val indValuesIndices: Array<ShortArray>,
    private val indValues: IntArray,
    private val directValues: Array<IntArray>
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableShort2IntTrieMap {
            val firstByteLayer = inputStream.readByteArray(inputStream.readShort())
            val secondByteLayers = Array(firstByteLayer.size) { ByteArray(0) }

            val indValuesCount = inputStream.readShort()
            val indValuesIndices: Array<ShortArray>
            val indValues: IntArray
            val directValues: Array<IntArray>

            if (indValuesCount > 0) {
                indValuesIndices = Array(firstByteLayer.size) { ShortArray(0) }
                indValues = inputStream.readIntArray(indValuesCount)
                directValues = emptyArray()
            } else {
                indValuesIndices = emptyArray()
                indValues = IntArray(0)
                directValues = Array(firstByteLayer.size) { IntArray(0) }
            }

            repeat(firstByteLayer.size) { firstLayerIndex ->
                var secondByteLayerSize = inputStream.readByte()
                // If 0 then value overflowed; cannot have value 0 because then firstByteLayer entry would not exist
                if (secondByteLayerSize == 0) secondByteLayerSize = 256

                secondByteLayers[firstLayerIndex] = inputStream.readByteArray(secondByteLayerSize)

                if (indValuesCount > 0) {
                    indValuesIndices[firstLayerIndex] = inputStream.readShortArray(secondByteLayerSize)
                } else {
                    directValues[firstLayerIndex] = inputStream.readIntArray(secondByteLayerSize)
                }
            }

            return ImmutableShort2IntTrieMap(firstByteLayer, secondByteLayers, indValuesIndices, indValues, directValues)
        }
    }

    class Builder {
        private val map: Byte2ObjectSortedMap<Byte2IntSortedMap> = Byte2ObjectAVLTreeMap()

        fun add(key: Short, value: Int) {
            val old = map.computeIfAbsent(key.toByte(), Byte2ObjectFunction { Byte2IntAVLTreeMap() })
                .put(key.toInt().shr(8).toByte(), value)
            check(old == 0)
        }

        fun build(): ImmutableShort2IntTrieMap {
            val firstByteLayer = map.keys.toByteArray()
            val secondByteLayers = Array(firstByteLayer.size) { ByteArray(0) }
            map.values.forEachIndexed { firstLayerIndex, secondByteLayerMap ->
                secondByteLayers[firstLayerIndex] = secondByteLayerMap.keys.toByteArray()
            }

            val allValues = map.values.stream()
                .flatMapToInt { map -> map.values.intStream() }
                .toArray()

            return createValueArrays(IntArrayList.wrap(allValues)) { indValuesIndices, values ->
                if (indValuesIndices.isEmpty()) {
                    var globalIndex = 0
                    val directValues = Array(firstByteLayer.size) { firstLayerIndex ->
                        IntArray(secondByteLayers[firstLayerIndex].size) {
                            allValues[globalIndex++]
                        }
                    }

                    return@createValueArrays ImmutableShort2IntTrieMap(firstByteLayer, secondByteLayers, emptyArray(), IntArray(0), directValues)
                } else {
                    var globalIndex = 0
                    val splitIndValueIndices = Array(firstByteLayer.size) { firstLayerIndex ->
                        ShortArray(secondByteLayers[firstLayerIndex].size) {
                            indValuesIndices[globalIndex++]
                        }
                    }

                    return@createValueArrays ImmutableShort2IntTrieMap(firstByteLayer, secondByteLayers, splitIndValueIndices, values, emptyArray())
                }
            }
        }
    }

    fun get(key: Short): Int {
        val firstLayerIndex = firstByteLayer.binarySearch(key.toByte())
        if (firstLayerIndex < 0) return 0

        val secondLayerIndex = secondByteLayers[firstLayerIndex].binarySearch(key.toInt().shr(8).toByte())
        if (secondLayerIndex < 0) return 0

        return if (indValuesIndices.isEmpty()) directValues[firstLayerIndex][secondLayerIndex]
            else indValues[indValuesIndices[firstLayerIndex][secondLayerIndex].toUShort().toInt()]
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        // Must write as short instead of byte because otherwise max length of 256 would overflow
        dataOutput.writeShort(firstByteLayer.size)
        dataOutput.write(firstByteLayer)

        val indValuesCount = indValues.size
        // Can't overflow because for maximum value 65536 no indirect lookup would be used
        // because direct lookup would be more efficient
        dataOutput.writeShort(indValuesCount)
        if (indValuesCount > 0) {
            dataOutput.writeIntArray(indValues)
        }

        secondByteLayers.forEachIndexed { firstLayerIndex, secondByteLayer ->
            dataOutput.writeByte(secondByteLayer.size)
            dataOutput.write(secondByteLayer)

            if (indValuesCount > 0) {
                dataOutput.writeShortArray(indValuesIndices[firstLayerIndex])
            } else {
                dataOutput.writeIntArray(directValues[firstLayerIndex])
            }
        }
    }
}

// TODO DEBUG
fun main() {
    var random = Random(0)
    val builder = ImmutableShort2IntTrieMap.Builder()

    repeat(100) {
        builder.add(random.nextInt(65536).toShort(), 4)
    }

    var map = builder.build()

    val out = ByteArrayOutputStream()
    map.writeBinary(out)
    println(out.size())

    val inStream = ByteArrayInputStream(out.toByteArray())
    map = ImmutableShort2IntTrieMap.fromBinary(inStream)
    check(inStream.read() == -1)


    random = Random(0)
    repeat(100) {
        val key = random.nextInt(65536).toShort()
        val actualValue = map.get(key)
        check(actualValue == 4)
    }
}
