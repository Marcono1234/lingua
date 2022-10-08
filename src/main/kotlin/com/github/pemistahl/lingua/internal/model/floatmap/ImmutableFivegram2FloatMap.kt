package com.github.pemistahl.lingua.internal.model.floatmap

import com.github.pemistahl.lingua.internal.model.extension.readFivegramArray
import com.github.pemistahl.lingua.internal.model.extension.readFloatArray
import com.github.pemistahl.lingua.internal.model.extension.readInt
import com.github.pemistahl.lingua.internal.model.extension.readShortArray
import com.github.pemistahl.lingua.internal.model.extension.writeFloatArray
import com.github.pemistahl.lingua.internal.model.extension.writeShortArray
import it.unimi.dsi.fastutil.objects.Object2FloatFunction
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2FloatRBTreeMap
import it.unimi.dsi.fastutil.objects.Object2FloatSortedMap
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

internal class ImmutableFivegram2FloatMap private constructor(
    private val keys: Array<String>,
    /**
     * For an index _i_ obtained based on [keys]:
     * - if _i_ < [indValuesIndices]`.length`: Look up index from [indValuesIndices], then based on the result (treated
     *   as unsigned short) look up value from [values]
     * - else if `indValuesIndices.isEmpty()`: Look up value from `values[i]`
     * - else: Look up value from `values[i - indValuesIndices.length + maxIndirectionIndices]`
     */
    private val indValuesIndices: ShortArray,
    private val values: FloatArray,
) : Object2FloatFunction<String> {
    companion object {
        fun fromBinary(inputStream: InputStream): ImmutableFivegram2FloatMap {
            val keys = inputStream.readFivegramArray(inputStream.readInt())
            val indValuesIndices = inputStream.readShortArray(inputStream.readInt())
            val values = inputStream.readFloatArray(inputStream.readInt())

            return ImmutableFivegram2FloatMap(keys, indValuesIndices, values)
        }
    }

    class Builder {
        // Uses a red-black tree map because that should have faster insertion times than AVL map
        private val map: Object2FloatSortedMap<String> = Object2FloatRBTreeMap()

        fun add(key: String, value: Float) {
            check(key.length == 5)
            val old = map.put(key, value)
            check(old == 0f)
        }

        fun build(): ImmutableFivegram2FloatMap {
            val keys = map.keys.toTypedArray()

            return createValueArrays(map.values) { indValuesIndices, values ->
                return@createValueArrays ImmutableFivegram2FloatMap(keys, indValuesIndices, values)
            }
        }
    }

    private fun getValue(index: Int): Float {
        return if (index < indValuesIndices.size) values[indValuesIndices[index].toInt().and(0xFFFF) /* UShort */]
        else if (indValuesIndices.isEmpty()) values[index]
        else values[index - indValuesIndices.size + maxIndirectionIndices]
    }

    override fun getFloat(key: Any): Float {
        val index = keys.binarySearch(key)
        return if (index < 0) 0f else getValue(index)
    }

    override fun size(): Int = keys.size

    fun asHashMap(): Object2FloatOpenHashMap<String> {
        val map = Object2FloatOpenHashMap<String>(size())
        keys.forEachIndexed { index, key -> map.put(key, getValue(index)) }
        return map
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
        dataOutput.writeFloatArray(values)
    }
}
