package com.github.pemistahl.lingua.internal.util

internal class ResettableLazy<T>(private val supplier: () -> T) {
    @Volatile
    private var value: T? = null

    fun value(): T {
        // Double-checked locking
        var value = this.value
        if (value == null) {
            synchronized(this) {
                value = this.value
                if (value == null) {
                    value = supplier()
                    this.value = value
                }
            }
        }
        return value!!
    }

    fun reset() {
        value = null
    }
}
