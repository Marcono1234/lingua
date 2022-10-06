package com.github.pemistahl.lingua.internal.util

// Based on Kotlin's AssertionsJVM.kt implementation
@Suppress("ClassName")
internal object _Assertions {
    @JvmField
    internal val ENABLED: Boolean = javaClass.desiredAssertionStatus()
}

// Evaluates the condition lazily in contrast to Kotlin's assert(...), see also
// https://youtrack.jetbrains.com/issue/KT-22292
internal inline fun lazyAssert(assertion: () -> Boolean) {
    if (_Assertions.ENABLED) {
        if (!assertion()) {
            throw AssertionError("Assertion failed")
        }
    }
}
