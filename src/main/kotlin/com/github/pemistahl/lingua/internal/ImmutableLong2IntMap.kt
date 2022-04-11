package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import com.github.pemistahl.lingua.internal.util.extension.readLongArray
import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImmutableLong2IntMap(
    private val keys: LongArray,
    private val values: IntArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableLong2IntMap {
            val length = inputStream.readInt()

            val keys = inputStream.readLongArray(length)
            val values = inputStream.readIntArray(length)
            return ImmutableLong2IntMap(keys, values)
        }
    }

    class Builder(private val map: Long2IntSortedMap = Long2IntAVLTreeMap()) {
        fun add(key: Long, value: Int) {
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableLong2IntMap {
            val size = map.size
            val keys = LongArray(size)
            val values = IntArray(size)

            map.long2IntEntrySet().forEachIndexed { index, entry ->
                keys[index] = entry.longKey
                values[index] = entry.intValue
            }

            return ImmutableLong2IntMap(keys, values)
        }
    }

    fun get(key: Long): Int {
        val index = keys.binarySearch(key)
        return if (index >= 0) values[index] else 0
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        dataOutput.writeInt(keys.size)
        keys.forEach(dataOutput::writeLong)
        values.forEach(dataOutput::writeInt)
    }
}
