package com.github.pemistahl.lingua.internal.model

import com.github.pemistahl.lingua.internal.model.extension.readFloatArray
import com.github.pemistahl.lingua.internal.model.extension.readInt
import com.github.pemistahl.lingua.internal.model.extension.readLongArray
import com.github.pemistahl.lingua.internal.model.extension.readShortArray
import com.github.pemistahl.lingua.internal.model.extension.writeFloatArray
import com.github.pemistahl.lingua.internal.model.extension.writeLongArray
import com.github.pemistahl.lingua.internal.model.extension.writeShortArray
import it.unimi.dsi.fastutil.longs.Long2FloatAVLTreeMap
import it.unimi.dsi.fastutil.longs.Long2FloatSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class ImmutableLong2FloatMap private constructor(
    private val keys: LongArray,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if _i_ < [indValuesIndices]`.length`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else if `indValuesIndices.isEmpty()`: Look up value from `values[i]`
     * - else: Look up value from `values[i - indValuesIndices.length + maxIndirectionIndices]`
     */
    private val indValuesIndices: ShortArray,
    private val values: FloatArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableLong2FloatMap {
            val keys = inputStream.readLongArray(inputStream.readInt())
            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readFloatArray(inputStream.readInt())

            return ImmutableLong2FloatMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        private val map: Long2FloatSortedMap = Long2FloatAVLTreeMap()

        fun add(key: Long, value: Float) {
            val old = map.put(key, value)
            check(old == 0f)
        }

        fun build(): ImmutableLong2FloatMap {
            val keys = map.keys.toLongArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableLong2FloatMap(keys, indValuesIndices, values)
            }
        }
    }

    fun get(key: Long): Float {
        val index = keys.binarySearch(key)
        return if (index < 0) 0f else {
            if (index < indValuesIndices.size) values[indValuesIndices[index].toInt().and(0xFFFF) /* UShort */]
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
        dataOutput.writeFloatArray(values)
    }
}
