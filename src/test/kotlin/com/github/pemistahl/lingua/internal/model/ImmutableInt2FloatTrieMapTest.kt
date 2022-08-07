package com.github.pemistahl.lingua.internal.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class ImmutableInt2FloatTrieMapTest {
    // Small test to verify basic functionality
    @Test
    fun test() {
        // Use JDK Random to produce deterministic set of keys
        val keys = Random(2).ints(79999).toArray()
        val builder = ImmutableInt2FloatTrieMap.Builder()

        keys.forEach {
            builder.add(it, it.toFloat())
        }

        var map = builder.build()

        val out = ByteArrayOutputStream()
        map.writeBinary(out)
        assertEquals(712698, out.size())

        val inStream = ByteArrayInputStream(out.toByteArray())
        map = ImmutableInt2FloatTrieMap.fromBinary(inStream)
        assertEquals(-1, inStream.read(), "Binary data should have been fully consumed")

        keys.forEach { key ->
            val expectedValue = key.toFloat()
            val value = map.get(key)
            assertEquals(expectedValue, value, "Failed: $key, $value")
        }
    }
}
