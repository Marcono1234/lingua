package com.github.pemistahl.lingua.internal

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.*
import java.util.function.IntConsumer
import kotlin.NoSuchElementException

private const val NO_ORDINAL = -1

/**
 * Custom `Map` implementation with `Enum` as key type and `Int` as value type.
 *
 * This class exists because:
 * - Enums use the identity hash code, so the iteration order for [Object2IntOpenHashMap]
 *   would be arbitrary (though [Object2IntLinkedOpenHashMap] could be an alternative)
 * - It provides a function for handling multiple entries with maximum value
 * - It does not implement [Map] so it is not possible to use boxing functions by accident
 */
internal class EnumIntMap<E : Enum<E>>(private val enumClass: Class<E>) {
    companion object {
        inline fun <reified E : Enum<E>> newMap(): EnumIntMap<E> {
            return EnumIntMap(E::class.java)
        }
    }

    private val values = IntArray(enumClass.enumConstants.size)

    private fun enumConstantForOrdinal(ordinal: Int): E {
        return enumClass.enumConstants[ordinal]
    }

    fun increment(enum: E) {
        values[enum.ordinal]++
    }

    fun hasOnlyZeroValues() = values.all { it == 0 }

    fun countNonZeroValues() = values.count { it != 0 }

    fun hasNonZeroValue(enumConstant: E) = values[enumConstant.ordinal] != 0

    fun firstNonZero(): E? {
        values.forEachIndexed { ordinal, value ->
            if (value != 0) return enumConstantForOrdinal(ordinal)
        }
        return null
    }

    /** Returns the value or 0 if the constant has no value. */
    fun getOrZero(enumConstant: E) = values[enumConstant.ordinal]

    fun set(enumConstant: E, value: Int) {
        values[enumConstant.ordinal] = value
    }

    inline fun ifNonZero(enumConstant: E, consumer: IntConsumer) {
        val value = values[enumConstant.ordinal]
        if (value != 0) {
            consumer.accept(value)
        }
    }

    data class Entry<E: Enum<E>>(val key: E, val value: Int)

    fun descendingIterator() = object: Iterator<Entry<E>> {
        var lastMax = Int.MAX_VALUE
        var nextOrdinal = Int.MAX_VALUE // Move to end to skip `lastMax` check for first `hasNext()` call

        var next: E? = null
        var nextValue = 0

        override fun hasNext(): Boolean {
            if (next != null) return true
            if (nextOrdinal == NO_ORDINAL) return false

            var maxOrdinal = NO_ORDINAL
            var maxValue = 0

            // First try finding constant with same value behind last result
            for (ordinal in nextOrdinal until values.size) {
                val value = values[ordinal]
                if (value == lastMax && value > maxValue) {
                    maxOrdinal = ordinal
                    maxValue = value
                }
            }

            if (maxOrdinal != NO_ORDINAL) {
                next = enumConstantForOrdinal(maxOrdinal)
                nextValue = maxValue
                // Next iteration search one constant further for max
                nextOrdinal = maxOrdinal + 1
                return true
            }

            // No other constant found with `value == lastMax`, now check all constants
            values.forEachIndexed { ordinal, value ->
                if (value < lastMax && value > maxValue) {
                    maxOrdinal = ordinal
                    maxValue = value
                }
            }

            if (maxOrdinal != NO_ORDINAL) {
                next = enumConstantForOrdinal(maxOrdinal)
                nextValue = maxValue
                // Next iteration search one constant further for max
                nextOrdinal = maxOrdinal + 1
                lastMax = maxValue
                return true
            } else {
                // Reached end
                nextOrdinal = NO_ORDINAL
                return false
            }
        }

        override fun next(): Entry<E> {
            if (hasNext()) {
                val result = next
                next = null
                return Entry(result!!, nextValue)
            } else {
                throw NoSuchElementException()
            }
        }
    }

    /**
     * Returns the enum constants with the maximum > 0 value, or an empty set if all
     * enum constants have the value 0.
     */
    fun maxNonZero(): EnumSet<E> {
        val set = EnumSet.noneOf(enumClass)
        var maxValue = 1 // Ignore 0

        values.forEachIndexed { ordinal, value ->
            if (value == maxValue) {
                set.add(enumConstantForOrdinal(ordinal))
            } else if (value > maxValue) {
                // Found new maximum
                set.clear()
                set.add(enumConstantForOrdinal(ordinal))
                maxValue = value
            }
        }

        return set
    }

    fun keysWithValueLargerEqualThan(value: Double): EnumSet<E> {
        val set = EnumSet.noneOf(enumClass)

        // 0 acts as no-value, so don't consider it
        if (value <= 0) return set

        values.forEachIndexed { ordinal, entryValue ->
            if (entryValue >= value) {
               set.add(enumConstantForOrdinal(ordinal))
            }
        }
        return set
    }

    override fun toString(): String {
        val joiner = StringJoiner(", ", "{", "}")
        values.forEachIndexed { ordinal, value ->
            if (value != 0) {
                val enumConstant = enumConstantForOrdinal(ordinal)
                joiner.add("$enumConstant=$value")
            }
        }
        return joiner.toString()
    }
}
