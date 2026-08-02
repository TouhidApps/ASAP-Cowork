package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared subprocess execution primitive every tool that shells out (Gradle,
 * adb, emulator, ...) runs through. Output is drained on a dedicated
 * background thread rather than a coroutine — deliberately, so a chatty
 * process can never deadlock `waitFor()` by filling the OS pipe buffer —
 * and progress callbacks are throttled so a noisy build doesn't flood the
 * caller with events.
 */
object ProcessRunner {
    private const val POLL_INTERVAL_MS = 300L
    private const val PROGRESS_THROTTLE_MS = 500L
    private const val MAX_PROGRESS_LINE_LENGTH = 100
    private const val READER_JOIN_TIMEOUT_MS = 5_000L

    /** Returns (success, combined stdout+stderr text prefixed with the exit code). Never throws. */
    suspend fun run(
        command: List<String>,
        workDir: File,
        timeoutSeconds: Long,
        maxOutputChars: Int,
        progressPrefix: String,
        onProgress: suspend (String) -> Unit = {},
        extraEnv: Map<String, String> = emptyMap(),
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val process = try {
            val builder = ProcessBuilder(command).directory(workDir).redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            extraEnv.forEach { (key, value) -> builder.environment()[key] = value }
            builder.start()
        } catch (e: Exception) {
            return@withContext false to "Failed to run ${command.firstOrNull()}: ${e.message}"
        }

        val output = StringBuilder()
        val latestLine = AtomicReference<String?>(null)
        val readerThread = Thread({
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    output.appendLine(line)
                    latestLine.set(line.take(MAX_PROGRESS_LINE_LENGTH))
                }
            }
        }, "process-runner-reader").apply { isDaemon = true; start() }

        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        var lastProgressAt = 0L
        var finished = false
        while (System.currentTimeMillis() < deadline) {
            if (process.waitFor(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                finished = true
                break
            }
            val now = System.currentTimeMillis()
            if (now - lastProgressAt >= PROGRESS_THROTTLE_MS) {
                latestLine.getAndSet(null)?.let { line -> onProgress("$progressPrefix — $line") }
                lastProgressAt = now
            }
        }

        if (!finished) {
            process.destroyForcibly()
            readerThread.join(READER_JOIN_TIMEOUT_MS)
            return@withContext false to "Timed out after ${timeoutSeconds / 60} minutes."
        }

        readerThread.join(READER_JOIN_TIMEOUT_MS)

        val exitCode = process.exitValue()
        val fullOutput = output.toString()
        val truncated = fullOutput.length > maxOutputChars
        val resultText = if (truncated) fullOutput.takeLast(maxOutputChars) else fullOutput
        val truncatedNote = if (truncated) "(...output truncated, showing the end...)\n\n" else ""

        (exitCode == 0) to "Exit code: $exitCode\n$truncatedNote$resultText"
    }
}
