package com.github.pemistahl.lingua.internal

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import java.lang.StringBuilder
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
internal class EnumDoubleMap<E : Enum<E>>(private val enumClass: Class<E>) {
    companion object {
        inline fun <reified E : Enum<E>> newMap(): EnumDoubleMap<E> {
            return EnumDoubleMap(E::class.java)
        }
    }

    private val values = DoubleArray(enumClass.enumConstants.size)

    private fun enumConstantForOrdinal(ordinal: Int): E {
        return enumClass.enumConstants[ordinal]
    }

    fun put(enumConstant: E, value: Double) {
        values[enumConstant.ordinal] = value
    }

    /** Returns the value or 0 if the constant has no value. */
    fun getOrZero(enumConstant: E) = values[enumConstant.ordinal]

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
        values.forEachIndexed { ordinal, value ->
            if (value != 0.0) {
                set.add(enumConstantForOrdinal(ordinal))
            }
        }
        return set
    }

    inline fun mapNonZeroValues(mapper: DoubleUnaryOperator): EnumDoubleMap<E> {
        val copy = EnumDoubleMap(enumClass)
        values.forEachIndexed { index, value ->
            if (value != 0.0) {
                copy.values[index] = mapper.applyAsDouble(value)
            }
        }
        return copy
    }

    fun sortedByNonZeroDescendingValue(): SortedMap<E, Double> {
        val map = TreeMap<E, Double> { a, b ->
            // Highest first
            val diff = values[b.ordinal].compareTo(values[a.ordinal])
            when {
                diff != 0 -> diff
                // Else sort Language constants by declaration order
                else -> a.compareTo(b)
            }
        }
        values.forEachIndexed { ordinal, value ->
            if (value != 0.0) {
                map[enumConstantForOrdinal(ordinal)] = value
            }
        }
        return map
    }

    override fun toString(): String {
        val joiner = StringJoiner(", ", "{", "}")
        values.forEachIndexed { ordinal, value ->
            if (value != 0.0) {
                val enumConstant = enumConstantForOrdinal(ordinal)
                joiner.add("$enumConstant=$value")
            }
        }
        return joiner.toString()
    }
}
