/**
 * Helper functions for indirect encoded frequency lookup.
 *
 * The `Immutable...2IntMap` classes (except for [ImmutableByte2IntMap]) support two lookup modes for encoded
 * frequencies: direct and indirect
 *
 * In _direct_ mode the index determined from the `keys` array can directly be used to look up the value from
 * the `values` array. In _indirect_ mode an additional short array is used. The array is used for indirection;
 * the index determined from `keys` is used to look up an intermediate index from `indValuesIndices` which is
 * then used to look up the value from `values`. This allows mapping multiple keys to the same encoded `Int`
 * frequency, saving some memory (at the cost of a slower lookup time).
 *
 * Indirect mode is only used where it results in memory reduction compared to _direct_ mode.
 */

package com.github.pemistahl.lingua.internal.model

import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet

private inline fun IntArray.forEachIndexedStartingAt(start: Int, action: (index: Int, i: Int) -> Unit) {
    for (i in start until size) {
        action(i - start, get(i))
    }
}

private inline fun IntArray.forEachIndexedUntil(endExclusive: Int, action: (index: Int, i: Int) -> Unit) {
    repeat(endExclusive) {
        action(it, get(it))
    }
}

internal const val maxIndirectionIndices = 65536 // number of values representable by a short

internal inline fun <T> createValueArrays(
    intValues: IntCollection,
    resultHandler: (indValuesIndices: ShortArray, values: IntArray) -> T
): T {
    val indirectValues = IntLinkedOpenHashSet()
    var indirectLookupEndIndex = 0

    // Use int array to avoid boxing, and for faster lookup
    val intValuesArray = intValues.toIntArray()
    run indirectlyAccessibleValues@{
        intValuesArray.forEach {
            // Only have to break the loop if value is not contained and max size is reached
            if (!indirectValues.contains(it)) {
                if (indirectValues.size >= maxIndirectionIndices) {
                    return@indirectlyAccessibleValues
                }

                indirectValues.add(it)
            }

            indirectLookupEndIndex++
        }
    }

    val indirectValuesCount = indirectValues.size
    val indirectValuesArray = indirectValues.toIntArray()

    val shortWeight = 1 // 16bit
    val intWeight = 2 // 32bit = 2 * short

    val directLookupCount = intValuesArray.size - indirectLookupEndIndex
    val indirectCost = indirectLookupEndIndex * shortWeight + (indirectValuesCount + directLookupCount) * intWeight

    val directCost = intValuesArray.size * intWeight

    return if (indirectCost < directCost) {
        val values = indirectValuesArray.copyOf(indirectValuesCount + directLookupCount)

        intValuesArray.forEachIndexedStartingAt(indirectLookupEndIndex) { index, i ->
            values[indirectValuesCount + index] = i
        }

        val indValuesIndices = ShortArray(indirectLookupEndIndex)
        intValuesArray.forEachIndexedUntil(indirectLookupEndIndex) { index, i ->
            val indirectIndex = indirectValuesArray.indexOf(i)
            assert(indirectIndex != -1)
            indValuesIndices[index] = indirectIndex.toShort()
        }

        resultHandler(indValuesIndices, values)
    } else {
        resultHandler(ShortArray(0), intValuesArray)
    }
}
