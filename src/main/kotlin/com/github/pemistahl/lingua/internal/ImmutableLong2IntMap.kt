package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.*
import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import com.github.pemistahl.lingua.internal.util.extension.readLongArray
import com.github.pemistahl.lingua.internal.util.extension.readShortArray
import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImmutableLong2IntMap private constructor(
    private val keys: LongArray,
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
        fun fromBinary(inputStream: InputStream): ImmutableLong2IntMap {
            val keys = inputStream.readLongArray(inputStream.readInt())
            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readIntArray(inputStream.readInt())

            return ImmutableLong2IntMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        private val map: Long2IntSortedMap = Long2IntAVLTreeMap()

        fun add(key: Long, value: Int) {
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableLong2IntMap {
            val keys = map.keys.toLongArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableLong2IntMap(keys, indValuesIndices, values)
            }
        }
    }

    fun get(key: Long): Int {
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
        dataOutput.writeLongArray(keys)

        dataOutput.writeInt(indValuesIndices.size)
        dataOutput.writeShortArray(indValuesIndices)

        dataOutput.writeInt(values.size)
        dataOutput.writeIntArray(values)
    }
}
