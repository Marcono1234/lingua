import java.io.BufferedReader
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class ProcessExecutor(private val command: List<String>, private val workingDir: Path? = null) {
    fun execute(treatStdErrAsError: Boolean, timeout: Duration) {
        require(!timeout.isNegative) { "Timeout $timeout is negative" }
        val outputTimeout = minOf(timeout, Duration.ofSeconds(5))

        val process = ProcessBuilder(command).also {
            if (workingDir != null) it.directory(workingDir.toFile())
        }.start()

        // Close std-in; not needed
        process.outputStream.close()
        // TODO: Use `Process#inputReader` and `Process#errorReader` when using newer JDK
        val stdOutCollector = OutputCollector("std-out", process.inputStream.bufferedReader())
        val stdErrCollector = OutputCollector("std-out", process.errorStream.bufferedReader())

        fun indent(s: String): String {
            val indent = "  "
            return if (s.isEmpty()) {
                s
            } else {
                indent + s.replace("\n", "\n" + indent).trimEnd()
            }
        }

        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroy()
            // Note: Might have to run `./gradlew ... --info` to see full message during build
            throw ProcessException("Process took too long\n" +
                "command: $command\n" +
                "std-out:\n${indent(stdOutCollector.getOutput(outputTimeout))}\nstd-err:\n${indent(stdErrCollector.getOutput(outputTimeout))}")
        }

        val exitCode = process.exitValue()
        val output = stdOutCollector.getOutput(outputTimeout)
        val errorOutput = stdErrCollector.getOutput(outputTimeout)

        if (exitCode != 0) {
            // Note: Might have to run `./gradlew ... --info` to see full message during build
            throw ProcessException("Process exited with code $exitCode\n" +
                "command: $command\n" +
                "std-out:\n${indent(output)}\nstd-err:\n${indent(errorOutput)}")
        }

        if (treatStdErrAsError && errorOutput.isNotBlank()) {
            // Note: Might have to run `./gradlew ... --info` to see full message during build
            throw ProcessException("Process produced error output\n" +
                "command: $command\n" +
                "std-out:\n${indent(output)}\nstd-err:\n${indent(errorOutput)}")
        }
    }

    class ProcessException(message: String): Exception(message) {}

    /**
     * Class for concurrently reading process output (this is necessary to avoid deadlock when output
     * buffers become full).
     */
    private class OutputCollector(name: String, private val reader: BufferedReader) {
        private val thread: Thread
        @Volatile private var isCancelled: Boolean = false
        private val outputBuilder: StringBuilder = StringBuilder()

        init {
            val thread = Thread({
                while (!isCancelled) {
                    val line = reader.readLine() ?: break
                    outputBuilder.append(line).append('\n')
                }
            }, "ProcessOutputCollector[$name]")
            thread.isDaemon = true
            thread.start()
            this.thread = thread
        }

        fun cancel() {
            isCancelled = true
            thread.interrupt()
        }

        fun getOutput(timeout: Duration): String {
            // TODO: Use `Thread#join(Duration)` when using newer JDK
            thread.join(timeout.toMillis())
            if (thread.isAlive) {
                cancel()
                throw ProcessException("Waiting for thread '${thread.name}' timed out")
            }
            return outputBuilder.toString()
        }
    }
}
