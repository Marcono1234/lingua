package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import com.github.pemistahl.lingua.internal.util.extension.readShortArray
import it.unimi.dsi.fastutil.shorts.Short2IntAVLTreeMap
import it.unimi.dsi.fastutil.shorts.Short2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImmutableShort2IntMap(
    private val keys: ShortArray,
    private val values: IntArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableShort2IntMap {
            val length = inputStream.readInt()

            val keys = inputStream.readShortArray(length)
            val values = inputStream.readIntArray(length)
            return ImmutableShort2IntMap(keys, values)
        }
    }

    class Builder(private val map: Short2IntSortedMap = Short2IntAVLTreeMap()) {
        fun add(key: Short, value: Int) {
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableShort2IntMap {
            val size = map.size
            val keys = ShortArray(size)
            val values = IntArray(size)

            map.short2IntEntrySet().forEachIndexed { index, entry ->
                keys[index] = entry.shortKey
                values[index] = entry.intValue
            }

            return ImmutableShort2IntMap(keys, values)
        }
    }

    fun get(key: Short): Int {
        val index = keys.binarySearch(key)
        return if (index >= 0) values[index] else 0
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        // Must write as int instead of short because otherwise max length of UShort.MAX_VALUE + 1 would overflow
        dataOutput.writeInt(keys.size)
        keys.forEach {dataOutput.writeShort(it.toInt())}
        values.forEach(dataOutput::writeInt)
    }
}
