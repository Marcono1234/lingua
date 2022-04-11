package com.github.pemistahl.lingua.internal.util.extension

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

internal fun InputStream.readByte(): Int {
    val v = read()
    if (v < 0) throw EOFException()
    return v
}

internal fun InputStream.readShort(): Int {
    val v = read().shl(8).or(read())
    if (v < 0) throw EOFException()
    return v
}

internal fun InputStream.readInt(): Int {
    val v = read().shl(24)
        .or(read().shl(16))
        .or((read().shl(8)))
        .or(read())
    if (v < 0) throw EOFException()
    return v
}

internal fun InputStream.readByteArray(length: Int): ByteArray {
    val array = ByteArray(length)
    val read = readNBytes(array, 0, array.size)
    if (read < length) throw EOFException()
    return array
}

// Note: Convert to non-byte array using ByteBuffer; this might (especially for newer Java versions) yield better
// performance than reading from DataInputStream
// TODO: Verify whether this really justifies increased memory usage; instead of DataInputStream could also use
//       methods similar to readInt() above

internal fun InputStream.readShortArray(length: Int): ShortArray {
    val byteArray = readByteArray(length * Short.SIZE_BYTES)
    val array = ShortArray(length)
    ByteBuffer.wrap(byteArray).asShortBuffer().get(array)
    return array
}

internal fun InputStream.readIntArray(length: Int): IntArray {
    val byteArray = readByteArray(length * Int.SIZE_BYTES)
    val array = IntArray(length)
    ByteBuffer.wrap(byteArray).asIntBuffer().get(array)
    return array
}

internal fun InputStream.readLongArray(length: Int): LongArray {
    val byteArray = readByteArray(length * Long.SIZE_BYTES)
    val array = LongArray(length)
    ByteBuffer.wrap(byteArray).asLongBuffer().get(array)
    return array
}

internal fun InputStream.readFivegramArray(length: Int): Array<String> {
    val byteArray = readByteArray(length * Char.SIZE_BYTES * 5)
    val charArray = CharArray(length * 5)
    // TODO: Not sure if this is really efficient here
    ByteBuffer.wrap(byteArray).asCharBuffer().get(charArray)

    return Array(length) { String(charArray, it * 5, 5) }
}
