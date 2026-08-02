package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Records a short screen recording of the emulator [EmulatorSession]
 * currently has running, via `adb shell screenrecord` + `adb pull`. Saved
 * under `<workspaceRoot>/.asap-videos/`, served back by chat-gateway's
 * `/api/v1/videos/{filename}` route — the video equivalent of
 * [DeviceScreenshotTool]. Blocks for the recording's full duration, same
 * reasoning as [GradleTool] blocking for a build: there's no way to stream
 * partial progress from a single tool call, so the call just takes as long
 * as the work does.
 *
 * Passes `--bit-rate` well below screenrecord's own default (20 Mbps) and
 * downscales resolution via `--size` — real-time H.264 encoding at full
 * device resolution/bitrate is CPU-heavy enough to make the emulator
 * noticeably laggy/unresponsive to input for the recording's duration on a
 * resource-constrained host. The scaled size is derived from `adb shell wm
 * size` rather than a guessed constant, so it always matches the device's
 * actual aspect ratio instead of risking a stretched recording on an AVD
 * profile with different proportions than assumed.
 */
object RecordDeviceVideoTool {
    const val NAME = "record_device_video"
    private const val DEFAULT_SECONDS = 30
    private const val MAX_SECONDS = 60
    private const val ADB_TIMEOUT_SECONDS = 20L
    private const val BIT_RATE_BPS = 4_000_000
    private const val MAX_DIMENSION_PX = 720
    private const val EMULATOR_NOTICE =
        "Recording on the emulator isn't hardware-accelerated, so it can look laggier than the real app — connect a physical device over USB for a smoother recording (it'll be used automatically once connected)."

    val spec = ToolSpec(
        name = NAME,
        description = "Records a short screen recording of the currently running Android emulator and shows it in the chat. Call this directly whenever the user asks for a video, recording, or screen capture of the emulator — do not ask for confirmation first. The emulator must already be running (start it with manage_emulator first). This call blocks for the recording's full duration before returning, so don't assume it hung.",
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
        val serial = DeviceTargeting.resolveSerial()
            ?: return@withContext ToolResult("No emulator or device is connected. Start one with manage_emulator first.", isError = true)

        val seconds = ((input["seconds"] as? Number)?.toInt() ?: DEFAULT_SECONDS).coerceIn(1, MAX_SECONDS)
        val videosDir = File(workspaceRoot, ".asap-videos").apply { mkdirs() }
        val filename = "device-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.mp4"
        val outputFile = File(videosDir, filename)
        val devicePath = "/sdcard/asap-recording-${UUID.randomUUID().toString().take(8)}.mp4"

        try {
            val scaledSize = resolveScaledSize(serial)
            val recordCommand = buildList {
                addAll(listOf("adb", "-s", serial, "shell", "screenrecord", "--bit-rate", BIT_RATE_BPS.toString()))
                scaledSize?.let { addAll(listOf("--size", it)) }
                addAll(listOf("--time-limit", seconds.toString(), devicePath))
            }
            val recordBuilder = ProcessBuilder(recordCommand).redirectErrorStream(true)
            ToolchainEnvironment.configure(recordBuilder)
            val record = recordBuilder.start()
            val recordFinished = record.waitFor(seconds + ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!recordFinished) {
                record.destroyForcibly()
                return@withContext ToolResult("Recording timed out.", isError = true)
            }

            val pullBuilder = ProcessBuilder("adb", "-s", serial, "pull", devicePath, outputFile.absolutePath)
                .redirectErrorStream(true)
            ToolchainEnvironment.configure(pullBuilder)
            val pull = pullBuilder.start()
            val pullFinished = pull.waitFor(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            runCatching {
                val rmBuilder = ProcessBuilder("adb", "-s", serial, "shell", "rm", devicePath)
                ToolchainEnvironment.configure(rmBuilder)
                rmBuilder.start()
            }

            if (!pullFinished || pull.exitValue() != 0 || !outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext ToolResult("Failed to pull the recording from the emulator.", isError = true)
            }

            ToolResult(
                "Recorded a ${seconds}s video of the emulator.",
                videoUrl = "/api/v1/videos/$filename",
                notice = EMULATOR_NOTICE.takeIf { DeviceTargeting.isEmulator(serial) },
            )
        } catch (e: IOException) {
            ToolResult(
                "adb is not installed (or not on PATH). Install the Android SDK platform-tools and set ANDROID_HOME. (${e.message})",
                isError = true,
            )
        }
    }

    /**
     * A `--size` value scaling the device's actual resolution (from `adb
     * shell wm size`) down to at most [MAX_DIMENSION_PX] on its longer
     * side, preserving aspect ratio — `null` if the device is already
     * smaller than that (nothing to scale down) or its resolution can't be
     * determined, in which case [execute] just omits `--size` and records
     * at native resolution rather than guessing one.
     */
    private fun resolveScaledSize(serial: String): String? = try {
        val builder = ProcessBuilder("adb", "-s", serial, "shell", "wm", "size").redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        val finished = process.waitFor(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return null
        }
        val output = process.inputStream.bufferedReader().readText()
        // An active "Override size" (e.g. from a prior `wm size` call) reflects
        // what actually renders and comes after "Physical size" in the output,
        // so the last match is whichever one is currently in effect.
        val (width, height) = Regex("(?:Physical|Override) size: (\\d+)x(\\d+)").findAll(output).lastOrNull()
            ?.destructured?.let { (w, h) -> w.toInt() to h.toInt() }
            ?: return null

        val scale = MAX_DIMENSION_PX.toDouble() / maxOf(width, height)
        if (scale >= 1.0) return null

        // screenrecord's encoder needs even dimensions.
        fun scaled(dimension: Int) = ((dimension * scale).toInt() and 1.inv()).coerceAtLeast(2)
        "${scaled(width)}x${scaled(height)}"
    } catch (e: IOException) {
        null
    }
}
