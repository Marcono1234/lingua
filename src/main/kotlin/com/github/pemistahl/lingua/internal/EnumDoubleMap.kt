package com.github.pemistahl.lingua.internal

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import java.util.*
import java.util.function.DoubleUnaryOperator

/**
 * Custom `Map` implementation with `Enum` as key type and `Double` as value type.
 *
 * This class exists because:
 * - Enums use the identity hash code, so the iteration order for [Object2DoubleOpenHashMap]
 *   would be arbitrary (though [Object2DoubleLinkedOpenHashMap] could be an alternative)
 * - It does not implement [Map] so it is not possible to use boxing functions by accident
 */
internal class EnumDoubleMap<E : Enum<E>>(
    private val enumClass: Class<E>,
    private val keyIndexer: KeyIndexer<E>
) {
    companion object {
        inline fun <reified E : Enum<E>> newMap(keyIndexer: KeyIndexer<E>): EnumDoubleMap<E> {
            return EnumDoubleMap(E::class.java, keyIndexer)
        }
    }

    private val values = DoubleArray(keyIndexer.indicesCount())

    fun put(enumConstant: E, value: Double) {
        values[keyIndexer.keyToIndex(enumConstant)] = value
    }

    /** Returns the value or 0 if the constant has no value. */
    fun getOrZero(enumConstant: E) = values[keyIndexer.keyToIndex(enumConstant)]

    fun maxValueOrNull(): Double? {
        var max = Double.NEGATIVE_INFINITY
        var hasValue = false
        values.forEach {
            if (it != 0.0 && it > max) {
                max = it
                hasValue = true
            }
        }

        return if (hasValue) max else null
    }

    /** Returns the keys with non 0 value. */
    fun getNonZeroKeys(): EnumSet<E> {
        val set = EnumSet.noneOf(enumClass)
        values.forEachIndexed { index, value ->
            if (value != 0.0) {
                set.add(keyIndexer.indexToKey(index))
            }
        }
        return set
    }

    inline fun mapNonZeroValues(mapper: (Double) -> Double): EnumDoubleMap<E> {
        val copy = EnumDoubleMap(enumClass, keyIndexer)
        values.forEachIndexed { index, value ->
            if (value != 0.0) {
                copy.values[index] = mapper(value)
            }
        }
        return copy
    }

    fun sortedByNonZeroDescendingValue(): SortedMap<E, Double> {
        val map = TreeMap<E, Double> { a, b ->
            // Highest first
            val diff = values[keyIndexer.keyToIndex(b)].compareTo(values[keyIndexer.keyToIndex(a)])
            when {
                diff != 0 -> diff
                // Else sort Language constants by declaration order
                else -> a.compareTo(b)
            }
        }
        values.forEachIndexed { index, value ->
            if (value != 0.0) {
                map[keyIndexer.indexToKey(index)] = value
            }
        }
        return map
    }

    override fun toString(): String {
        val joiner = StringJoiner(", ", "{", "}")
        values.forEachIndexed { index, value ->
            if (value != 0.0) {
                val enumConstant = keyIndexer.indexToKey(index)
                joiner.add("$enumConstant=$value")
            }
        }
        return joiner.toString()
    }
}
