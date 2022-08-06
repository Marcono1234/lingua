package com.github.pemistahl.lingua.internal.model

import com.github.pemistahl.lingua.internal.model.extension.readByteArray
import com.github.pemistahl.lingua.internal.model.extension.readFloatArray
import com.github.pemistahl.lingua.internal.model.extension.readShort
import com.github.pemistahl.lingua.internal.model.extension.writeFloatArray
import it.unimi.dsi.fastutil.bytes.Byte2FloatAVLTreeMap
import it.unimi.dsi.fastutil.bytes.Byte2FloatSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class ImmutableByte2FloatMap private constructor(
    private val keys: ByteArray,
    private val values: FloatArray
) {
    companion object {
        @JvmStatic
        fun fromBinary(inputStream: InputStream): ImmutableByte2FloatMap {
            val length = inputStream.readShort()

            val keys = inputStream.readByteArray(length)
            val values = inputStream.readFloatArray(length)
            return ImmutableByte2FloatMap(keys, values)
        }
    }

    class Builder {
        private val map: Byte2FloatSortedMap = Byte2FloatAVLTreeMap()

        fun add(key: Byte, value: Float) {
            val old = map.put(key, value)
            check(old == 0f)
        }

        fun build(): ImmutableByte2FloatMap {
            return ImmutableByte2FloatMap(map.keys.toByteArray(), map.values.toFloatArray())
        }
    }

    fun get(key: Byte): Float {
        val index = keys.binarySearch(key)
        return if (index >= 0) values[index] else 0f
    }

    fun writeBinary(outputStream: OutputStream) {
        val dataOutput = DataOutputStream(outputStream)

        // Must write as short instead of byte because otherwise max length of 256 would overflow
        dataOutput.writeShort(keys.size)
        dataOutput.write(keys)
        dataOutput.writeFloatArray(values)
    }
}
