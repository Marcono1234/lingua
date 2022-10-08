package com.github.pemistahl.lingua.internal.model.floatmap

import com.github.pemistahl.lingua.internal.model.extension.readFloatArray
import com.github.pemistahl.lingua.internal.model.extension.readInt
import com.github.pemistahl.lingua.internal.model.extension.readShort
import com.github.pemistahl.lingua.internal.model.extension.readShortArray
import com.github.pemistahl.lingua.internal.model.extension.writeFloatArray
import com.github.pemistahl.lingua.internal.model.extension.writeShortArray
import it.unimi.dsi.fastutil.shorts.Short2FloatFunction
import it.unimi.dsi.fastutil.shorts.Short2FloatOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2FloatRBTreeMap
import it.unimi.dsi.fastutil.shorts.Short2FloatSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class ImmutableShort2FloatMap private constructor(
    private val keys: ShortArray,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if [indValuesIndices]`.length > 0`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else: Look up value from `values[i]`
     *
     * (This is a deviation from the other maps because for a short based map all keys fit inside [indValuesIndices],
     * so the question is only whether indirection reduces memory usage)
     */
    private val indValuesIndices: ShortArray,
    private val values: FloatArray
) : Short2FloatFunction {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableShort2FloatMap {
            val keys = inputStream.readShortArray(inputStream.readInt())
            // Don't need to check for overflow here; if the map contains the maximum of 65536 unique values,
            // then indValuesIndices won't be used (i.e. it will be empty) because indirect lookup would require
            // more memory than direct lookup
            val indValuesIndices = inputStream.readShortArray(inputStream.readShort())

            var valuesLength = inputStream.readShort()
            // Detect overflow when map contains UShort.MAX_VALUE + 1 values
            if (keys.isNotEmpty() && valuesLength == 0) valuesLength = 65536
            val values = inputStream.readFloatArray(valuesLength)

            return ImmutableShort2FloatMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        // Uses a red-black tree map because that should have faster insertion times than AVL map
        private val map: Short2FloatSortedMap = Short2FloatRBTreeMap()

        fun add(key: Short, value: Float) {
            val old = map.put(key, value)
            check(old == 0f)
        }

        fun build(): ImmutableShort2FloatMap {
            val keys = map.keys.toShortArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableShort2FloatMap(keys, indValuesIndices, values)
            }
        }
    }

    private fun getValue(index: Int): Float {
        return if (indValuesIndices.isEmpty()) values[index]
        else values[indValuesIndices[index].toInt().and(0xFFFF) /* UShort */]
    }

    override fun get(key: Short): Float {
        val index = keys.binarySearch(key)
        return if (index < 0) 0f else getValue(index)
    }

    override fun size(): Int = keys.size

    fun asHashMap(): Short2FloatOpenHashMap {
        val map = Short2FloatOpenHashMap(size())
        keys.forEachIndexed { index, key -> map.put(key, getValue(index)) }
        return map
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        // Must write as int instead of short because otherwise max length of UShort.MAX_VALUE + 1 would overflow
        dataOutput.writeInt(keys.size)
        dataOutput.writeShortArray(keys)

        dataOutput.writeShort(indValuesIndices.size)
        dataOutput.writeShortArray(indValuesIndices)

        dataOutput.writeShort(values.size)
        dataOutput.writeFloatArray(values)
    }
}
