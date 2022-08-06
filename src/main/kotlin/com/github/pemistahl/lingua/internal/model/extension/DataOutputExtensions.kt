package com.github.pemistahl.lingua.internal.model.extension

import java.io.DataOutput

internal fun DataOutput.writeCharArray(array: CharArray) {
    array.forEach { writeChar(it.code) }
}

internal fun DataOutput.writeShortArray(array: ShortArray) {
    array.forEach { writeShort(it.toInt()) }
}

internal fun DataOutput.writeIntArray(array: IntArray) {
    array.forEach(::writeInt)
}

internal fun DataOutput.writeLongArray(array: LongArray) {
    array.forEach(::writeLong)
}

internal fun DataOutput.writeFloatArray(array: FloatArray) {
    array.forEach(::writeFloat)
}
