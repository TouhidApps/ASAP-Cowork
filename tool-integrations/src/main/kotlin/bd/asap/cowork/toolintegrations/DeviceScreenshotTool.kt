package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Captures the running emulator's screen via `adb exec-out screencap`.
 * Saved under `.asap-screenshots/`, served back by chat-gateway's
 * `/api/v1/screenshots/{path...}` route. [directory], when given, nests
 * that folder inside a specific scaffolded project (the same one passed to
 * run_gradle etc.) instead of the shared workspace root, so multiple
 * projects' screenshots don't collide.
 */
object DeviceScreenshotTool {
    const val NAME = "capture_device_screenshot"
    private const val ADB_TIMEOUT_SECONDS = 20L
    private const val MAX_DELAY_SECONDS = 30

    val spec = ToolSpec(
        name = NAME,
        description = "Captures a screenshot of the currently running emulator's screen — use this to verify the app rendered correctly after installing/launching it.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "delaySeconds" to mapOf(
                    "type" to "integer",
                    "description" to "Seconds to wait before capturing. A screenshot taken right after launch_app usually just shows the splash screen — pass 2-3 to let the real UI finish rendering first. Omit or pass 0 to capture immediately, e.g. to verify the splash screen itself.",
                ),
                "directory" to mapOf(
                    "type" to "string",
                    "description" to "The project directory this screenshot is for (the same value passed to run_gradle/build tools), so it nests under that project instead of the shared workspace root.",
                ),
            ),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?> = emptyMap()): ToolResult = withContext(Dispatchers.IO) {
        val delaySeconds = ((input["delaySeconds"] as? Number)?.toInt() ?: 0).coerceIn(0, MAX_DELAY_SECONDS)
        if (delaySeconds > 0) delay(delaySeconds * 1000L)

        val serial = DeviceTargeting.resolveSerial()
            ?: return@withContext ToolResult("No emulator or device is connected. Start one with manage_emulator first.", isError = true)

        val directory = normalizedProjectDirectory(input["directory"] as? String)
        val filename = "device-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.png"
        val relativePath = projectRelativePath(directory, ".asap-screenshots", filename)
        val outputPath = Workspace(workspaceRoot.toPath()).resolve(relativePath)
            ?: return@withContext ToolResult("Invalid directory: $directory", isError = true)
        Files.createDirectories(outputPath.parent)
        val outputFile = outputPath.toFile()

        try {
            val builder = ProcessBuilder("adb", "-s", serial, "exec-out", "screencap", "-p")
                .redirectOutput(outputFile)
            ToolchainEnvironment.configure(builder)
            val process = builder.start()
            val finished = process.waitFor(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outputFile.delete()
                return@withContext ToolResult("Timed out capturing screenshot.", isError = true)
            }
            if (process.exitValue() != 0 || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext ToolResult("Failed to capture screenshot (adb exited ${process.exitValue()}).", isError = true)
            }
            val url = projectAssetUrl(directory, "/api/v1/screenshots", filename)
            ToolResult("Captured screenshot: $url", imageUrl = url)
        } catch (e: IOException) {
            ToolResult(
                "adb is not installed (or not on PATH). Install the Android SDK platform-tools and set ANDROID_HOME. (${e.message})",
                isError = true,
            )
        }
    }
}
