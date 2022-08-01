package com.github.pemistahl.lingua.internal.model

import com.github.pemistahl.lingua.internal.model.extension.readByteArray
import com.github.pemistahl.lingua.internal.model.extension.readIntArray
import com.github.pemistahl.lingua.internal.model.extension.readShort
import com.github.pemistahl.lingua.internal.model.extension.writeIntArray
import it.unimi.dsi.fastutil.bytes.Byte2IntAVLTreeMap
import it.unimi.dsi.fastutil.bytes.Byte2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class ImmutableByte2IntMap private constructor(
    private val keys: ByteArray,
    private val values: IntArray
) {
    companion object {
        @JvmStatic
        fun fromBinary(inputStream: InputStream): ImmutableByte2IntMap {
            val length = inputStream.readShort()

            val keys = inputStream.readByteArray(length)
            val values = inputStream.readIntArray(length)
            return ImmutableByte2IntMap(keys, values)
        }
    }

    class Builder {
        private val map: Byte2IntSortedMap = Byte2IntAVLTreeMap()

        fun add(key: Byte, value: Int) {
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableByte2IntMap {
            return ImmutableByte2IntMap(map.keys.toByteArray(), map.values.toIntArray())
        }
    }

    fun get(key: Byte): Int {
        val index = keys.binarySearch(key)
        return if (index >= 0) values[index] else 0
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        // Must write as short instead of byte because otherwise max length of 256 would overflow
        dataOutput.writeShort(keys.size)
        dataOutput.write(keys)
        dataOutput.writeIntArray(values)
    }
}
