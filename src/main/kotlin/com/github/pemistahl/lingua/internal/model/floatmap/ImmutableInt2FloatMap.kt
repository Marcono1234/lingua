package com.github.pemistahl.lingua.internal.model.floatmap

import com.github.pemistahl.lingua.internal.model.extension.readFloatArray
import com.github.pemistahl.lingua.internal.model.extension.readInt
import com.github.pemistahl.lingua.internal.model.extension.readIntArray
import com.github.pemistahl.lingua.internal.model.extension.readShortArray
import com.github.pemistahl.lingua.internal.model.extension.writeFloatArray
import com.github.pemistahl.lingua.internal.model.extension.writeIntArray
import com.github.pemistahl.lingua.internal.model.extension.writeShortArray
import it.unimi.dsi.fastutil.ints.Int2FloatFunction
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2FloatRBTreeMap
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class ImmutableInt2FloatMap private constructor(
    private val keys: IntArray,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if _i_ < [indValuesIndices]`.length`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else if `indValuesIndices.isEmpty()`: Look up value from `values[i]`
     * - else: Look up value from `values[i - indValuesIndices.length + maxIndirectionIndices]`
     */
    private val indValuesIndices: ShortArray,
    private val values: FloatArray
) : Int2FloatFunction {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableInt2FloatMap {
            val keys = inputStream.readIntArray(inputStream.readInt())
            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readFloatArray(inputStream.readInt())

            return ImmutableInt2FloatMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        // Uses a red-black tree map because that should have faster insertion times than AVL map
        private val map: Int2FloatSortedMap = Int2FloatRBTreeMap()

        fun add(key: Int, value: Float) {
            val old = map.put(key, value)
            check(old == 0f)
        }

        fun build(): ImmutableInt2FloatMap {
            val keys = map.keys.toIntArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableInt2FloatMap(keys, indValuesIndices, values)
            }
        }
    }

    private fun getValue(index: Int): Float {
        return if (index < indValuesIndices.size) values[indValuesIndices[index].toInt().and(0xFFFF) /* UShort */]
        else if (indValuesIndices.isEmpty()) values[index]
        else values[index - indValuesIndices.size + maxIndirectionIndices]
    }

    override fun get(key: Int): Float {
        val index = keys.binarySearch(key)
        return if (index < 0) 0f else getValue(index)
    }

    override fun size(): Int = keys.size

    fun asHashMap(): Int2FloatOpenHashMap {
        val map = Int2FloatOpenHashMap(size())
        keys.forEachIndexed { index, key -> map.put(key, getValue(index)) }
        return map
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        dataOutput.writeInt(keys.size)
        dataOutput.writeIntArray(keys)

        dataOutput.writeInt(indValuesIndices.size)
        dataOutput.writeShortArray(indValuesIndices)

        dataOutput.writeInt(values.size)
        dataOutput.writeFloatArray(values)
    }
}
