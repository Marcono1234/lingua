import com.github.pemistahl.lingua.internal.model.lookup.QuadriFivegramBinarySearchLookup
import com.github.pemistahl.lingua.internal.model.lookup.UniBiTrigramBinarySearchLookup
import com.squareup.moshi.JsonReader
import it.unimi.dsi.fastutil.objects.Object2FloatLinkedOpenHashMap
import okio.buffer
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.math.abs

@CacheableTask
abstract class GenerateLanguageModelsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE) // only content matters, file name does not matter
    abstract val linguaArtifact: RegularFileProperty
    /*
     * TODO: Does not seem to detect when unused files exist, might have to adjust task, see also
     *      https://github.com/gradle/gradle/blob/19a20e98d7697333674c88f2db6dae1d0e3d206b/subprojects/core/src/main/java/org/gradle/api/internal/tasks/execution/CleanupStaleOutputsExecuter.java
     *      (?) https://github.com/gradle/gradle/issues/1349
     */
    // Note: Currently creates `language-models/language-models/...` path (depending on which path `build.gradle.kts`
    //       specifies) but that is probably acceptable
    @get:OutputDirectory
    abstract val modelOutputDir: DirectoryProperty

    init {
        group = "build"
        description = "Generates binary language models from upstream Lingua language models"
    }

    @TaskAction
    fun generateModels() {
        val modelOutputDir = this.modelOutputDir.get().asFile.toPath()
        Files.createDirectories(modelOutputDir)

        var filesToDelete: MutableSet<Path> = HashSet(128)
        Files.walkFileTree(modelOutputDir, object: SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                filesToDelete.add(file.toAbsolutePath())

                return FileVisitResult.CONTINUE
            }
        })

        filesToDelete = if (filesToDelete.isEmpty()) {
            // Subsequent usage only tries to remove and iterate over elements; replace set to avoid redundant
            // synchronization
            emptyRemovableSet()
        } else {
            // Synchronized because model creator accesses it asynchronously
            Collections.synchronizedSet(filesToDelete)
        }

        val modelFileCreator = ModelFileCreator()

        JarFile(linguaArtifact.get().asFile).use { jarFile ->
            jarFile.entries().asSequence()
                .filter { it.name.startsWith("language-models/") && !it.isDirectory }
                // Group by language code
                .groupBy { it.name.substringAfter('/').substringBefore('/') }
                .mapValues {
                    val map = mutableMapOf<String, JarEntry>()
                    it.value.forEach { entry ->
                        val fileName = entry.name.split('/', limit = 3)[2]
                        map[fileName] = entry
                    }
                    map
                }
                .forEach { (languageCode, entries) ->
                    fun readModel(fileName: String, estimatedNgramsCount: Int): Object2FloatLinkedOpenHashMap<String> {
                        // If model file does not exist return empty map; some languages such as Chinese don't
                        // have model files for all ngram lengths, see
                        // https://github.com/pemistahl/lingua/commit/444aaa0848840e542d5c8bdc54ea1aff092f209b
                        return entries[fileName]?.let {
                            jarFile.getInputStream(it).use { stream ->
                                readJsonLanguageModel(stream, estimatedNgramsCount)
                            }
                        } ?: Object2FloatLinkedOpenHashMap<String>()
                    }

                    // Read JAR contents single-threaded because JarFile does not seem to be thread-safe
                    val unigrams = readModel("unigrams.json", 64)
                    val bigrams = readModel("bigrams.json", 1024)
                    val trigrams = readModel("trigrams.json", 8192)
                    modelFileCreator.submitCreationTask(
                        {
                            UniBiTrigramBinarySearchLookup.fromJson(unigrams, bigrams, trigrams)
                        },
                        {
                            val file = it.writeBinary(
                                modelOutputDir,
                                languageCode,
                                printingSizeChange(languageCode, "uni-bi-trigram")
                            )
                            filesToDelete.remove(file.toAbsolutePath())
                        }
                    )

                    val quadrigrams = readModel("quadrigrams.json", 32768)
                    val fivegrams = readModel("fivegrams.json", 65536)
                    modelFileCreator.submitCreationTask(
                        {
                            QuadriFivegramBinarySearchLookup.fromJson(quadrigrams, fivegrams)
                        },
                        {
                            val file = it.writeBinary(
                                modelOutputDir,
                                languageCode,
                                printingSizeChange(languageCode, "quadri-fivegram")
                            )
                            filesToDelete.remove(file.toAbsolutePath())
                        }
                    )
                }
        }

        modelFileCreator.awaitCompletion()

        // Clean up unused files
        filesToDelete.forEach {
            try {
                logger.info("Deleting unused file $it")
                Files.delete(it)
            } catch (e: IOException) {
                throw IOException("Failed deleting $it", e)
            }
        }
        // Clean up empty dirs
        Files.walkFileTree(modelOutputDir, object: SimpleFileVisitor<Path>() {
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                val isEmpty = Files.newDirectoryStream(dir).use { dirStream ->
                    !dirStream.iterator().hasNext()
                }

                if (isEmpty) {
                    logger.info("Deleting empty dir $dir")
                    Files.delete(dir)
                }

                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun readJsonLanguageModel(
        jsonStream: InputStream,
        estimatedNgramsCount: Int
    ): Object2FloatLinkedOpenHashMap<String> {
        val jsonReader = JsonReader.of(jsonStream.source().buffer())
        jsonReader.beginObject()

        var ngramsMap: Object2FloatLinkedOpenHashMap<String>? = null

        while (jsonReader.hasNext()) {
            when (val name = jsonReader.nextName()) {
                "language" -> { jsonReader.skipValue() }
                "ngrams" -> {
                    if (ngramsMap == null) {
                        ngramsMap = Object2FloatLinkedOpenHashMap(estimatedNgramsCount)
                        jsonReader.beginObject()
                        while (jsonReader.hasNext()) {
                            val frequencyString = jsonReader.nextName()
                            val separatorIndex = frequencyString.indexOf('/')
                            val numerator = Integer.parseInt(frequencyString, 0, separatorIndex, 10)
                            val denominator = Integer.parseInt(frequencyString,separatorIndex + 1, frequencyString.length, 10)

                            val frequency = numerator.toFloat() / denominator
                            // Note: Specifies delimiter as String `" "` instead of Char `' '` because currently
                            // implementation of `split` function would convert that to String anyway
                            val ngrams = jsonReader.nextString().split(" ")
                            ngrams.forEach { ngram ->
                                ngramsMap.put(ngram, frequency)
                            }
                        }
                        jsonReader.endObject()
                    } else throw IllegalArgumentException("Duplicate ngrams at ${jsonReader.path}")
                }
                else -> throw IllegalArgumentException("Unknown name '$name' at ${jsonReader.path}")
            }
        }
        jsonReader.endObject()

        if (ngramsMap == null) throw IllegalArgumentException("Model data is missing ngrams")
        return ngramsMap
    }


    private fun printingSizeChange(languageCode: String, name: String): (oldSizeBytes: Long?, newSizeBytes: Long) -> Unit {
        return { oldSizeBytes: Long?, newSizeBytes: Long ->
            if (oldSizeBytes == null) {
                logger.info("NEW: $languageCode $name ${formatFileSize(newSizeBytes, false)}")
            } else if (oldSizeBytes != newSizeBytes) {
                val sizeDiff = newSizeBytes - oldSizeBytes
                val sizeDiffStr = formatFileSize(sizeDiff, true)
                val percentage = String.format(Locale.ENGLISH, "%+.1f", (sizeDiff / oldSizeBytes.toDouble()) * 100)
                logger.info("CHANGE: $languageCode $name $sizeDiffStr ($percentage%)")
            }
        }
    }

    private fun formatFileSize(sizeBytes: Long, addSign: Boolean): String {
        if (abs(sizeBytes) < 1024) {
            return if (addSign && sizeBytes > 0) {
                "+$sizeBytes B"
            } else {
                // Only adds sign for negative values
                "$sizeBytes B"
            }
        }

        // Kilo, Mega, Giga, Tera, Peta, Exa
        val prefixes = "KMGTPE"
        var convertedSize = sizeBytes
        for (i in prefixes.indices) {
            val preConvertedSize = convertedSize
            convertedSize /= 1024
            if (abs(convertedSize) < 1024 || i  >= prefixes.length - 1) {
                val pattern = if (addSign) "%+.2f" else "%.2f"
                val formatted = String.format(Locale.ENGLISH, pattern, preConvertedSize / 1024.0)
                return "$formatted ${prefixes[i]}iB"
            }
        }
        throw AssertionError("unreachable")
    }
}
