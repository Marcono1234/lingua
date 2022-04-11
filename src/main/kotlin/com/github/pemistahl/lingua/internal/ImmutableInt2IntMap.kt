package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImmutableInt2IntMap(
    private val keys: IntArray,
    private val values: IntArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableInt2IntMap {
            val length = inputStream.readInt()

            val keys = inputStream.readIntArray(length)
            val values = inputStream.readIntArray(length)
            return ImmutableInt2IntMap(keys, values)
        }
    }

    class Builder(private val map: Int2IntSortedMap = Int2IntAVLTreeMap()) {
        fun add(key: Int, value: Int) {
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableInt2IntMap {
            val size = map.size
            val keys = IntArray(size)
            val values = IntArray(size)

            map.int2IntEntrySet().forEachIndexed { index, entry ->
                keys[index] = entry.intKey
                values[index] = entry.intValue
            }

            return ImmutableInt2IntMap(keys, values)
        }
    }

    fun get(key: Int): Int {
        val index = keys.binarySearch(key)
        return if (index >= 0) values[index] else 0
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        dataOutput.writeInt(keys.size)
        keys.forEach(dataOutput::writeInt)
        values.forEach(dataOutput::writeInt)
    }
}
