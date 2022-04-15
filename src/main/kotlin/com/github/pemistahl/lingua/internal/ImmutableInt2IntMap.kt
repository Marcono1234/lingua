package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.*
import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import com.github.pemistahl.lingua.internal.util.extension.readShortArray
import com.github.pemistahl.lingua.internal.util.extension.writeShortArray
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImmutableInt2IntMap private constructor(
    private val keys: IntArray,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if _i_ < [indValuesIndices]`.length`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else if `indValuesIndices.isEmpty()`: Look up value from `values[i]`
     * - else: Look up value from `values[i - indValuesIndices.length + maxIndirectionIndices]`
     */
    private val indValuesIndices: ShortArray,
    private val values: IntArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableInt2IntMap {
            val keys = inputStream.readIntArray(inputStream.readInt())
            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readIntArray(inputStream.readInt())

            return ImmutableInt2IntMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        private val map: Int2IntSortedMap = Int2IntAVLTreeMap()

        fun add(key: Int, value: Int) {
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableInt2IntMap {
            val keys = map.keys.toIntArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableInt2IntMap(keys, indValuesIndices, values)
            }
        }
    }

    fun get(key: Int): Int {
        val index = keys.binarySearch(key)
        return if (index < 0) 0 else {
            if (index < indValuesIndices.size) values[indValuesIndices[index].toUShort().toInt()]
            else if (indValuesIndices.isEmpty()) values[index]
            else values[index - indValuesIndices.size + maxIndirectionIndices]
        }
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        dataOutput.writeInt(keys.size)
        dataOutput.writeIntArray(keys)

        dataOutput.writeInt(indValuesIndices.size)
        dataOutput.writeShortArray(indValuesIndices)

        dataOutput.writeInt(values.size)
        dataOutput.writeIntArray(values)
    }
}
