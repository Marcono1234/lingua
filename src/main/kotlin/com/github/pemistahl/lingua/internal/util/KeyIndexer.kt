package com.github.pemistahl.lingua.internal.util

/**
 * Converts map keys to internal indices and vice versa. Indexers should be thread-safe
 * and can be shared by multiple map instances.
 */
internal interface KeyIndexer<K> {
    /** Returns the number of indices this indexer uses. */
    fun indicesCount(): Int

    /**
     * Returns an index for the key in range 0..([indicesCount] - 1). May return
     * [NO_INDEX] if the key is not supported.
     */
    fun keyToIndex(key: K): Int

    /**
     * Returns the key for an index. This is the reverse function of [keyToIndex].
     * Behavior is undefined when an index not supported by this indexer is provided.
     */
    fun indexToKey(index: Int): K

    companion object {
        const val NO_INDEX = -1

        private fun <E> Collection<E>.asSet() = (this as? Set) ?: this.toSet()

        inline fun <reified E : Enum<E>> fromEnumConstants(constants: Collection<E>) =
            fromEnumConstants(constants.asSet())

        /** Creates an indexer for a subset of all enum constants. */
        inline fun <reified E : Enum<E>> fromEnumConstants(constants: Set<E>): KeyIndexer<E> {
            require(constants.isNotEmpty())
            val allConstants = E::class.java.enumConstants
            if (allConstants.size == constants.size) return forAllEnumConstants()

            val ordinalToIndex = IntArray(allConstants.size) { NO_INDEX }

            var index = 0
            constants.forEach {
                ordinalToIndex[it.ordinal] = index
                index++
            }

            val indexToConstant = arrayOfNulls<E>(index)
            constants.forEach {
                indexToConstant[ordinalToIndex[it.ordinal]] = it
            }
            val indicesCount = index

            return object : KeyIndexer<E> {
                override fun indicesCount() = indicesCount

                override fun keyToIndex(key: E) = ordinalToIndex[key.ordinal]

                // Using `!!` is fine here, assuming KeyIndexer is used correctly and only valid indices are used
                override fun indexToKey(index: Int) = indexToConstant[index]!!
            }
        }

        inline fun <reified E : Enum<E>> forAllEnumConstants(): KeyIndexer<E> {
            val enumConstants = E::class.java.enumConstants
            return object : KeyIndexer<E> {
                override fun indicesCount() = enumConstants.size

                override fun keyToIndex(key: E) = key.ordinal

                override fun indexToKey(index: Int) = enumConstants[index]
            }
        }
    }
}
