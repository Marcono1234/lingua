/**
 * Helper functions for indirect encoded frequency lookup.
 *
 * The `Immutable...2FloatMap` classes (except for [ImmutableByte2FloatMap]) support two lookup modes for encoded
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

package com.github.pemistahl.lingua.internal.model.floatmap

import it.unimi.dsi.fastutil.floats.FloatCollection
import it.unimi.dsi.fastutil.floats.FloatLinkedOpenHashSet

private inline fun FloatArray.forEachIndexedStartingAt(start: Int, action: (index: Int, f: Float) -> Unit) {
    for (i in start until size) {
        action(i - start, get(i))
    }
}

private inline fun FloatArray.forEachIndexedUntil(endExclusive: Int, action: (index: Int, f: Float) -> Unit) {
    repeat(endExclusive) {
        action(it, get(it))
    }
}

internal const val maxIndirectionIndices = 65536 // number of values representable by a short

internal inline fun <T> createValueArrays(
    floatValues: FloatCollection,
    resultHandler: (indValuesIndices: ShortArray, values: FloatArray) -> T
) = createValueArrays(floatValues.toFloatArray(), resultHandler)

// Uses FloatArray to avoid boxing and for faster lookup
internal inline fun <T> createValueArrays(
    floatValues: FloatArray,
    resultHandler: (indValuesIndices: ShortArray, values: FloatArray) -> T
): T {
    val indirectValues = FloatLinkedOpenHashSet()
    var indirectLookupEndIndex = 0

    run indirectlyAccessibleValues@{
        floatValues.forEach {
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
    val indirectValuesArray = indirectValues.toFloatArray()

    val shortWeight = 1 // 16bit
    val intWeight = 2 // 32bit = 2 * short

    val directLookupCount = floatValues.size - indirectLookupEndIndex
    val indirectCost = indirectLookupEndIndex * shortWeight + (indirectValuesCount + directLookupCount) * intWeight

    val directCost = floatValues.size * intWeight

    return if (indirectCost < directCost) {
        val values = indirectValuesArray.copyOf(indirectValuesCount + directLookupCount)

        floatValues.forEachIndexedStartingAt(indirectLookupEndIndex) { index, f ->
            values[indirectValuesCount + index] = f
        }

        val indValuesIndices = ShortArray(indirectLookupEndIndex)
        floatValues.forEachIndexedUntil(indirectLookupEndIndex) { index, f ->
            val indirectIndex = indirectValuesArray.indexOfFirst { it == f }
            assert(indirectIndex != -1)
            indValuesIndices[index] = indirectIndex.toShort()
        }

        resultHandler(indValuesIndices, values)
    } else {
        resultHandler(ShortArray(0), floatValues)
    }
}
