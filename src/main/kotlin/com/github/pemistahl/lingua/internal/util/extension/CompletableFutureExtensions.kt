package com.github.pemistahl.lingua.internal.util.extension

import java.util.concurrent.CompletableFuture

internal fun <V> List<CompletableFuture<out V>>.allOfToList(): CompletableFuture<List<V>> {
    return CompletableFuture.allOf(*this.toTypedArray()).thenApply {
        // Can call join() here without risking deadlock because allOf made sure all futures are completed
        return@thenApply this.map { it.join() }
    }
}
