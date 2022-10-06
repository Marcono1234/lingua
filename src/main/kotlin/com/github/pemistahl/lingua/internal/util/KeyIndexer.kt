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

        fun <E : Enum<E>> fromEnumConstants(constants: Collection<E>) = fromEnumConstants(constants.asSet())

        /** Creates an indexer for a subset of all enum constants. */
        fun <E : Enum<E>> fromEnumConstants(constants: Set<E>): KeyIndexer<E> {
            val enumClass = constants.first().declaringJavaClass
            val allConstants = enumClass.enumConstants
            if (allConstants.size == constants.size) return forAllEnumConstants(enumClass)

            val ordinalToIndex = IntArray(allConstants.size) { NO_INDEX }

            var index = 0
            constants.forEach {
                ordinalToIndex[it.ordinal] = index
                index++
            }

            val indexToConstant = arrayOfNulls<Any>(index)
            constants.forEach {
                indexToConstant[ordinalToIndex[it.ordinal]] = it
            }

            return object : KeyIndexer<E> {
                override fun indicesCount() = index

                override fun keyToIndex(key: E) = ordinalToIndex[key.ordinal]

                @Suppress("UNCHECKED_CAST")
                override fun indexToKey(index: Int) = indexToConstant[index] as E
            }
        }

        inline fun <reified E : Enum<E>> forAllEnumConstants() = forAllEnumConstants(E::class.java)

        @JvmStatic
        fun <E : Enum<E>> forAllEnumConstants(enumClass: Class<E>): KeyIndexer<E> {
            val enumConstants = enumClass.enumConstants
            return object : KeyIndexer<E> {
                override fun indicesCount() = enumConstants.size

                override fun keyToIndex(key: E) = key.ordinal

                override fun indexToKey(index: Int) = enumConstants[index]
            }
        }
    }
}
