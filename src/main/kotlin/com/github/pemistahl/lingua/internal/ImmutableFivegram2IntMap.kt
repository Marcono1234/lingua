package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.readFivegramArray
import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImmutableFivegram2IntMap(
    private val keys: Array<String>,
    private val values: IntArray
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableFivegram2IntMap {
            val length = inputStream.readInt()

            val keys = inputStream.readFivegramArray(length)
            val values = inputStream.readIntArray(length)
            return ImmutableFivegram2IntMap(keys, values)
        }
    }

    class Builder(private val map: Object2IntSortedMap<String> = Object2IntAVLTreeMap()) {
        fun add(key: String, value: Int) {
            check(key.length == 5)
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableFivegram2IntMap {
            val size = map.size
            val keys = Array(size) {""}
            val values = IntArray(size)

            map.object2IntEntrySet().forEachIndexed { index, entry ->
                keys[index] = entry.key
                values[index] = entry.intValue
            }

            return ImmutableFivegram2IntMap(keys, values)
        }
    }

    fun get(key: String): Int {
        val index = keys.binarySearch(key)
        return if (index >= 0) values[index] else 0
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        dataOutput.writeInt(keys.size)
        keys.forEach {
            dataOutput.writeChar(it[0].code)
            dataOutput.writeChar(it[1].code)
            dataOutput.writeChar(it[2].code)
            dataOutput.writeChar(it[3].code)
            dataOutput.writeChar(it[4].code)
        }
        values.forEach(dataOutput::writeInt)
    }
}
