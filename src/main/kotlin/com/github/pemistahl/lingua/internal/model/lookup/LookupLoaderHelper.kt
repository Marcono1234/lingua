package com.github.pemistahl.lingua.internal.model.lookup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

private fun openResourceInputStream(resourcePath: String): InputStream {
    return UniBiTrigramLookup::class.java.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Resource '$resourcePath' does not exist")
}

internal fun openBinaryDataInput(resourcePath: String): DataInputStream {
    return DataInputStream(openResourceInputStream(resourcePath).buffered())
}

internal data class FileDataOutput(val filePath: Path, val dataOut: DataOutputStream)
internal fun openBinaryDataOutput(
    resourcesDirectory: Path,
    resourcePath: String,
    changeSummaryCallback: (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit
): FileDataOutput {
    val file = resourcesDirectory.resolve(resourcePath.removePrefix("/"))
    Files.createDirectories(file.parent)

    val oldSizeBytes = if (Files.isRegularFile(file)) Files.size(file) else null
    val dataOut = object : DataOutputStream(Files.newOutputStream(file).buffered()) {
        override fun close() {
            super.close()
            val newSizeBytes = Files.size(file)
            changeSummaryCallback(oldSizeBytes, newSizeBytes)
        }
    }

    return FileDataOutput(file, dataOut)
}

internal fun getBinaryModelResourceName(languageCode: String, fileName: String): String {
    return "/language-models/$languageCode/$fileName"
}
