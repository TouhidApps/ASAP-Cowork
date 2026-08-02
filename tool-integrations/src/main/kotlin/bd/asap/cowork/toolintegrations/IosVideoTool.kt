package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * iOS Simulator counterpart of [RecordDeviceVideoTool]. Unlike `adb shell
 * screenrecord` (which takes `--time-limit` and exits on its own),
 * `simctl io recordVideo` records indefinitely until it receives SIGINT —
 * killing it with a plain SIGTERM (Java's `Process.destroy()`) risks
 * leaving the .mp4's container unfinalized, so this shells out to `kill
 * -INT` on the process's own pid instead, then gives it a moment to flush
 * before falling back to a forceful kill.
 */
object IosVideoTool {
    const val NAME = "record_ios_video"
    private const val DEFAULT_SECONDS = 15
    private const val MAX_SECONDS = 60
    private const val FINALIZE_TIMEOUT_SECONDS = 10L

    val spec = ToolSpec(
        name = NAME,
        description = "Records a short screen recording of the currently booted iOS Simulator and shows it in the chat. Call this directly whenever the user asks for a video, recording, or screen capture — do not ask for confirmation first. The simulator must already be booted (start it with manage_ios_simulator first). This call blocks for the recording's full duration.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "seconds" to mapOf(
                    "type" to "integer",
                    "description" to "Recording length in seconds. Defaults to $DEFAULT_SECONDS, capped at $MAX_SECONDS.",
                ),
            ),
            "required" to emptyList<String>(),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val udid = IosSimulatorTargeting.resolveUdid()
            ?: return@withContext ToolResult("No simulator is booted. Start one with manage_ios_simulator first.", isError = true)

        val seconds = ((input["seconds"] as? Number)?.toInt() ?: DEFAULT_SECONDS).coerceIn(1, MAX_SECONDS)
        val videosDir = File(workspaceRoot, ".asap-videos").apply { mkdirs() }
        val filename = "sim-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.mp4"
        val outputFile = File(videosDir, filename)

        try {
            val builder = ProcessBuilder("xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264", outputFile.absolutePath)
                .redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            val process = builder.start()

            delay(seconds * 1000L)

            runCatching {
                ProcessBuilder("kill", "-INT", process.pid().toString()).start().waitFor(5, TimeUnit.SECONDS)
            }
            val finalized = process.waitFor(FINALIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finalized) process.destroyForcibly()

            if (!outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext ToolResult("Failed to record video — no output was produced.", isError = true)
            }

            ToolResult("Recorded a ${seconds}s video of the simulator.", videoUrl = "/api/v1/videos/$filename")
        } catch (e: IOException) {
            ToolResult(
                "\"xcrun simctl\" isn't available (${e.message}) — this requires the full Xcode.app, not just the Command Line Tools.",
                isError = true,
            )
        }
    }
}
