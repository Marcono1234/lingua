package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.internal.util.extension.*
import com.github.pemistahl.lingua.internal.util.extension.readFivegramArray
import com.github.pemistahl.lingua.internal.util.extension.readInt
import com.github.pemistahl.lingua.internal.util.extension.readIntArray
import com.github.pemistahl.lingua.internal.util.extension.readShortArray
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/*
 Note: Could in theory implement this with two separate key maps, _firstCharsKeys_ contains chars 1 - 4 encoded with
 bitwise OR as long, _lastCharKeys_ contains char 5. This allows fast primitive array binary search in _firstCharsKeys_,
 and probably also saves a bit of memory compared to using an `Array<String>`, which would have some overhead for every
 `String` object.
 However, only very few objects are stored in this map type (most ngrams can be encoded as primitive), therefore
 the additional complexity is most likely not worth it.
 */

class ImmutableFivegram2IntMap private constructor(
    private val keys: Array<String>,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if _i_ < [indValuesIndices]`.length`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else if `indValuesIndices.isEmpty()`: Look up value from `values[i]`
     * - else: Look up value from `values[i - indValuesIndices.length + maxIndirectionIndices]`
     */
    private val indValuesIndices: ShortArray,
    private val values: IntArray,
) {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableFivegram2IntMap {
            val keys = inputStream.readFivegramArray(inputStream.readInt())
            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readIntArray(inputStream.readInt())

            return ImmutableFivegram2IntMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        private val map: Object2IntSortedMap<String> = Object2IntAVLTreeMap()

        fun add(key: String, value: Int) {
            check(key.length == 5)
            val old = map.put(key, value)
            check(old == 0)
        }

        fun build(): ImmutableFivegram2IntMap {
            val keys = map.keys.toTypedArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableFivegram2IntMap(keys, indValuesIndices, values)
            }
        }
    }

    fun get(key: String): Int {
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
        keys.forEach {
            dataOutput.writeChar(it[0].code)
            dataOutput.writeChar(it[1].code)
            dataOutput.writeChar(it[2].code)
            dataOutput.writeChar(it[3].code)
            dataOutput.writeChar(it[4].code)
        }

        dataOutput.writeInt(indValuesIndices.size)
        dataOutput.writeShortArray(indValuesIndices)

        dataOutput.writeInt(values.size)
        dataOutput.writeIntArray(values)
    }
}
