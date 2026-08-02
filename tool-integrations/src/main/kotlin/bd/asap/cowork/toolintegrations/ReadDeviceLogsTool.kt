package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A one-shot log **snapshot**, for the debugging agent's tool-use loop —
 * distinct from chat-gateway's `LogcatRoutes`, which streams `adb logcat`
 * live (`-v time`, no `-t`) to the frontend's Device Logs panel over SSE.
 * An agentic tool call needs a bounded string it gets back immediately,
 * not an indefinite stream.
 *
 * Uses [ProcessRunner] rather than a bare `ProcessBuilder` + `waitFor()`
 * — verified live against a real physical device with a huge log buffer
 * (200k+ lines): calling `waitFor()` before reading stdout deadlocks once
 * output exceeds the OS pipe buffer, since `adb`/`log show` block writing
 * to a full pipe that nothing is draining yet. `ProcessRunner` already
 * solves this correctly (a dedicated reader thread drains output while
 * waiting) — the same reason every other tool here goes through it.
 * `-t <maxLines>` (bounds at the source, not just truncates the result)
 * on top of that means Android practically never produces enough output
 * to stress this anyway.
 *
 * iOS has no equivalent of [AppSession] tracking a "last launched"
 * process, so `platform=ios` can't default to app-only scope the way
 * Android does — it always returns the device's recent log window,
 * optionally narrowed by `processName` if given.
 */
object ReadDeviceLogsTool {
    const val NAME = "read_device_logs"
    private const val TIMEOUT_SECONDS = 30L
    private const val MAX_OUTPUT_CHARS = 8_000
    private const val DEFAULT_MAX_LINES = 500
    private const val PIDOF_TIMEOUT_SECONDS = 10L
    private val VALID_LEVELS = setOf("V", "D", "I", "W", "E")

    val spec = ToolSpec(
        name = NAME,
        description = "Reads a snapshot of recent device logs — for platform=\"android\", the currently running emulator/device's logcat (defaults to just the last-launched app's process; scope=\"all\" for the whole device). For platform=\"ios\", the currently booted Simulator's recent system log (optionally narrowed with processName).",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "platform" to mapOf("type" to "string", "enum" to listOf("android", "ios")),
                "scope" to mapOf("type" to "string", "enum" to listOf("app", "all"), "description" to "android only. Defaults to \"app\"."),
                "level" to mapOf("type" to "string", "enum" to VALID_LEVELS.toList(), "description" to "android only. Minimum priority to include."),
                "maxLines" to mapOf("type" to "integer", "description" to "android only. Most recent lines to fetch. Defaults to $DEFAULT_MAX_LINES."),
                "processName" to mapOf("type" to "string", "description" to "ios only. Narrows the log to one process, e.g. the app's name."),
                "lastMinutes" to mapOf("type" to "integer", "description" to "ios only. How far back to look. Defaults to 2."),
            ),
            "required" to listOf("platform"),
        ),
    )

    suspend fun execute(input: Map<String, Any?>): ToolResult = when (input["platform"] as? String) {
        "android" -> androidLogcat(input)
        "ios" -> iosLogShow(input)
        else -> ToolResult("\"platform\" must be \"android\" or \"ios\".", isError = true)
    }

    private suspend fun androidLogcat(input: Map<String, Any?>): ToolResult {
        val serial = DeviceTargeting.resolveSerial()
            ?: return ToolResult("No emulator or device is connected. Start one with manage_emulator first.", isError = true)

        val scope = (input["scope"] as? String) ?: "app"
        val level = (input["level"] as? String)?.uppercase()?.takeIf { it in VALID_LEVELS }
        val maxLines = (input["maxLines"] as? Number)?.toInt()?.coerceIn(1, 5_000) ?: DEFAULT_MAX_LINES
        val pid = if (scope == "app") {
            val packageName = AppSession.current()
                ?: return ToolResult("No app has been launched yet — launch one first, or pass scope=\"all\".", isError = true)
            resolvePid(serial, packageName)
                ?: return ToolResult("\"$packageName\" isn't currently running — launch it again, or pass scope=\"all\".", isError = true)
        } else {
            null
        }

        return try {
            val command = buildList {
                addAll(listOf("adb", "-s", serial, "logcat", "-t", maxLines.toString(), "-v", "time"))
                pid?.let { add("--pid=$it") }
                level?.let { add("*:$it") }
            }
            val (_, output) = ProcessRunner.run(
                command = command,
                workDir = java.io.File("."),
                timeoutSeconds = TIMEOUT_SECONDS,
                maxOutputChars = MAX_OUTPUT_CHARS,
                progressPrefix = "adb logcat",
            )
            ToolResult(output.ifBlank { "(no log output)" })
        } catch (e: IOException) {
            ToolResult("adb is not installed (or not on PATH). Install the Android SDK platform-tools and set ANDROID_HOME. (${e.message})", isError = true)
        }
    }

    private suspend fun iosLogShow(input: Map<String, Any?>): ToolResult {
        val udid = IosSimulatorTargeting.resolveUdid()
            ?: return ToolResult("No simulator is booted. Start one with manage_ios_simulator first.", isError = true)

        val processName = (input["processName"] as? String)?.trim()?.ifBlank { null }
        val lastMinutes = (input["lastMinutes"] as? Number)?.toInt()?.coerceIn(1, 30) ?: 2

        return try {
            val command = buildList {
                addAll(listOf("xcrun", "simctl", "spawn", udid, "log", "show", "--last", "${lastMinutes}m", "--style", "compact"))
                processName?.let { addAll(listOf("--predicate", "process == \"$it\"")) }
            }
            val (_, output) = ProcessRunner.run(
                command = command,
                workDir = java.io.File("."),
                timeoutSeconds = TIMEOUT_SECONDS,
                maxOutputChars = MAX_OUTPUT_CHARS,
                progressPrefix = "log show",
            )
            ToolResult(output.ifBlank { "(no log output)" })
        } catch (e: IOException) {
            ToolResult("\"xcrun simctl\" isn't available (${e.message}) — this requires the full Xcode.app, not just the Command Line Tools.", isError = true)
        }
    }

    /** `pidof`'s output is tiny (just a number), so the plain waitFor-then-read here doesn't hit the pipe-buffer deadlock [ProcessRunner] exists to avoid for logcat's much larger output. */
    private fun resolvePid(serial: String, packageName: String): String? = try {
        val builder = ProcessBuilder("adb", "-s", serial, "shell", "pidof", packageName).redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        val finished = process.waitFor(PIDOF_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            null
        } else {
            process.inputStream.bufferedReader().readText().trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
        }
    } catch (e: IOException) {
        null
    }
}
