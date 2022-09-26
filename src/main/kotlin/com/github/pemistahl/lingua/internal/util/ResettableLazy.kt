package com.github.pemistahl.lingua.internal.util

internal class ResettableLazy<T, P>(
    @Volatile
    private var parameter: P,
    private val supplier: (parameter: P) -> T,
) {
    @Volatile
    private var value: T? = null

    fun value(): T {
        // Double-checked locking
        var value = this.value
        if (value == null) {
            synchronized(this) {
                value = this.value
                if (value == null) {
                    value = supplier(parameter)
                    this.value = value
                }
            }
        }
        return value!!
    }

    fun updateParameter(parameter: P) {
        synchronized(this) {
            this.parameter = parameter
            // If value has already been computed, update it
            if (value != null) {
                value = supplier(parameter)
            }
        }
    }

    fun reset() {
        value = null
    }
}
