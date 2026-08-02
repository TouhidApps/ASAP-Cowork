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

/** iOS Simulator counterpart of [DeviceScreenshotTool] — same `.asap-screenshots/` convention and `/api/v1/screenshots/{filename}` route, `simctl io screenshot` instead of `adb exec-out screencap`. */
object IosScreenshotTool {
    const val NAME = "capture_ios_screenshot"
    private const val SIMCTL_TIMEOUT_SECONDS = 20L
    private const val MAX_DELAY_SECONDS = 30

    val spec = ToolSpec(
        name = NAME,
        description = "Captures a screenshot of the currently booted iOS Simulator's screen — use this to verify the app rendered correctly after installing/launching it.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "delaySeconds" to mapOf(
                    "type" to "integer",
                    "description" to "Seconds to wait before capturing. A screenshot taken right after launch usually just shows the splash screen — pass 2-3 to let the real UI finish rendering first. Omit or pass 0 to capture immediately, e.g. to verify the splash screen itself.",
                ),
            ),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?> = emptyMap()): ToolResult = withContext(Dispatchers.IO) {
        val delaySeconds = ((input["delaySeconds"] as? Number)?.toInt() ?: 0).coerceIn(0, MAX_DELAY_SECONDS)
        if (delaySeconds > 0) delay(delaySeconds * 1000L)

        val udid = IosSimulatorTargeting.resolveUdid()
            ?: return@withContext ToolResult("No simulator is booted. Start one with manage_ios_simulator first.", isError = true)

        val screenshotsDir = File(workspaceRoot, ".asap-screenshots").apply { mkdirs() }
        val filename = "sim-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.png"
        val outputFile = File(screenshotsDir, filename)

        try {
            val builder = ProcessBuilder("xcrun", "simctl", "io", udid, "screenshot", outputFile.absolutePath).redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            val process = builder.start()
            val finished = process.waitFor(SIMCTL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outputFile.delete()
                return@withContext ToolResult("Timed out capturing screenshot.", isError = true)
            }
            if (process.exitValue() != 0 || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext ToolResult("Failed to capture screenshot (simctl exited ${process.exitValue()}).", isError = true)
            }
            ToolResult("Captured screenshot: /api/v1/screenshots/$filename", imageUrl = "/api/v1/screenshots/$filename")
        } catch (e: IOException) {
            ToolResult(
                "\"xcrun simctl\" isn't available (${e.message}) — this requires the full Xcode.app, not just the Command Line Tools.",
                isError = true,
            )
        }
    }
}
