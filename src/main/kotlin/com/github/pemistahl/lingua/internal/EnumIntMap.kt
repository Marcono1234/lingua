package com.github.pemistahl.lingua.internal

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.*
import java.util.function.IntConsumer
import kotlin.NoSuchElementException

private const val NO_INDEX = -1

/**
 * Custom `Map` implementation with `Enum` as key type and `Int` as value type.
 *
 * This class exists because:
 * - Enums use the identity hash code, so the iteration order for [Object2IntOpenHashMap]
 *   would be arbitrary (though [Object2IntLinkedOpenHashMap] could be an alternative)
 * - It provides a function for handling multiple entries with maximum value
 * - It does not implement [Map] so it is not possible to use boxing functions by accident
 */
internal class EnumIntMap<E : Enum<E>>(
    private val enumClass: Class<E>,
    private val keyIndexer: KeyIndexer<E>
) {
    companion object {
        inline fun <reified E : Enum<E>> newMap(keyIndexer: KeyIndexer<E>): EnumIntMap<E> {
            return EnumIntMap(E::class.java, keyIndexer)
        }
    }

    private val values = IntArray(keyIndexer.indicesCount())

    fun increment(enumConstant: E) {
        values[keyIndexer.keyToIndex(enumConstant)]++
    }

    fun hasOnlyZeroValues() = values.all { it == 0 }

    fun countNonZeroValues() = values.count { it != 0 }

    fun hasNonZeroValue(enumConstant: E) = values[keyIndexer.keyToIndex(enumConstant)] != 0

    fun firstNonZero(): E? {
        values.forEachIndexed { index, value ->
            if (value != 0) return keyIndexer.indexToKey(index)
        }
        return null
    }

    /** Returns the value or 0 if the constant has no value. */
    fun getOrZero(enumConstant: E) = values[keyIndexer.keyToIndex(enumConstant)]

    fun set(enumConstant: E, value: Int) {
        values[keyIndexer.keyToIndex(enumConstant)] = value
    }

    inline fun ifNonZero(enumConstant: E, consumer: (Int) -> Unit) {
        val value = values[keyIndexer.keyToIndex(enumConstant)]
        if (value != 0) {
            consumer(value)
        }
    }

    data class Entry<E: Enum<E>>(val key: E, val value: Int)

    fun descendingIterator() = object: Iterator<Entry<E>> {
        var lastMax = Int.MAX_VALUE
        var nextIndex = Int.MAX_VALUE // Move to end to skip `lastMax` check for first `hasNext()` call

        var next: E? = null
        var nextValue = 0

        override fun hasNext(): Boolean {
            if (next != null) return true
            if (nextIndex == NO_INDEX) return false

            var maxIndex = NO_INDEX
            var maxValue = 0

            // First try finding constant with same value behind last result
            for (index in nextIndex until values.size) {
                val value = values[index]
                if (value == lastMax && value > maxValue) {
                    maxIndex = index
                    maxValue = value
                }
            }

            if (maxIndex != NO_INDEX) {
                next = keyIndexer.indexToKey(maxIndex)
                nextValue = maxValue
                // Next iteration search one constant further for max
                nextIndex = maxIndex + 1
                return true
            }

            // No other constant found with `value == lastMax`, now check all constants
            values.forEachIndexed { index, value ->
                if (value < lastMax && value > maxValue) {
                    maxIndex = index
                    maxValue = value
                }
            }

            if (maxIndex != NO_INDEX) {
                next = keyIndexer.indexToKey(maxIndex)
                nextValue = maxValue
                // Next iteration search one constant further for max
                nextIndex = maxIndex + 1
                lastMax = maxValue
                return true
            } else {
                // Reached end
                nextIndex = NO_INDEX
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

        values.forEachIndexed { index, value ->
            if (value == maxValue) {
                set.add(keyIndexer.indexToKey(index))
            } else if (value > maxValue) {
                // Found new maximum
                set.clear()
                set.add(keyIndexer.indexToKey(index))
                maxValue = value
            }
        }

        return set
    }

    fun keysWithValueLargerEqualThan(value: Double): EnumSet<E> {
        val set = EnumSet.noneOf(enumClass)

        // 0 acts as no-value, so don't consider it
        if (value <= 0) return set

        values.forEachIndexed { index, entryValue ->
            if (entryValue >= value) {
               set.add(keyIndexer.indexToKey(index))
            }
        }
        return set
    }

    override fun toString(): String {
        val joiner = StringJoiner(", ", "{", "}")
        values.forEachIndexed { index, value ->
            if (value != 0) {
                val enumConstant = keyIndexer.indexToKey(index)
                joiner.add("$enumConstant=$value")
            }
        }
        return joiner.toString()
    }
}
