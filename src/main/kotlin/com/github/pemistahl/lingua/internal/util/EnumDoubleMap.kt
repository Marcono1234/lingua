package com.github.pemistahl.lingua.internal.util

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleRBTreeMap
import it.unimi.dsi.fastutil.objects.Object2DoubleSortedMap
import it.unimi.dsi.fastutil.objects.Object2DoubleSortedMaps
import java.util.EnumSet
import java.util.StringJoiner

private const val NO_INDEX = -1

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

    fun set(enumConstant: E, value: Double) {
        values[keyIndexer.keyToIndex(enumConstant)] = value
    }

    fun increment(enumConstant: E, value: Double) {
        values[keyIndexer.keyToIndex(enumConstant)] += value
    }

    /** Returns the value or 0 if the constant has no value. */
    fun getOrZero(enumConstant: E) = values[keyIndexer.keyToIndex(enumConstant)]

    fun countNonZeroValues() = values.count { it != 0.0 }

    fun hasNonZeroValue(enumConstant: E) = values[keyIndexer.keyToIndex(enumConstant)] != 0.0

    fun hasOnlyZeroValues() = values.all { it == 0.0 }

    fun firstNonZero(): E? {
        values.forEachIndexed { index, value ->
            if (value != 0.0) return keyIndexer.indexToKey(index)
        }
        return null
    }

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

    fun sortedByNonZeroDescendingValue(): Object2DoubleSortedMap<E> {
        // Uses a red-black tree map because that should have faster insertion times than AVL map
        var map: Object2DoubleSortedMap<E> = Object2DoubleRBTreeMap { a, b ->
            // Highest first
            val diff = getOrZero(b).compareTo(getOrZero(a))
            when {
                diff != 0 -> diff
                // Else sort Language constants by declaration order
                else -> a.compareTo(b)
            }
        }
        values.forEachIndexed { index, value ->
            if (value != 0.0) {
                map.put(keyIndexer.indexToKey(index), value)
            }
        }

        /*
         * Wrap in LinkedOpenHashMap; this has the following advantages:
         * - LinkedOpenHashMap implements SortedMap, but only to provide the SortedMap convenience methods for
         *   obtaining entries in insertion order (exactly what is needed here)
         * - Hash map is most likely faster for lookup
         * - Properly handles the case where map is checked for languages which are not part of the keyIndexer;
         *   directly accessing the Object2DoubleRBTreeMap with its custom comparator above would lead to
         *   ArrayIndexOutOfBoundsException
         */
        map = Object2DoubleLinkedOpenHashMap(map)

        // Make unmodifiable because map is not a proper SortedMap
        return Object2DoubleSortedMaps.unmodifiable(map)
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

    data class Entry<E : Enum<E>>(val key: E, val value: Double)

    fun descendingIterator() = object : Iterator<Entry<E>> {
        var lastMax = Double.MAX_VALUE
        var nextIndex = Int.MAX_VALUE // Move to end to skip `lastMax` check for first `hasNext()` call

        var next: E? = null
        var nextValue = 0.0

        override fun hasNext(): Boolean {
            if (next != null) return true
            if (nextIndex == NO_INDEX) return false

            var maxIndex = NO_INDEX
            var maxValue = 0.0

            // First try finding constant with same value behind last result
            for (index in nextIndex until values.size) {
                val value = values[index]
                if (value == lastMax && value > 0.0) {
                    maxIndex = index
                    maxValue = value
                    break
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
                @Suppress("ConvertTwoComparisonsToRangeCheck")
                if (value < lastMax && value > maxValue) {
                    maxIndex = index
                    maxValue = value
                }
            }

            @Suppress("LiftReturnOrAssignment")
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
}
