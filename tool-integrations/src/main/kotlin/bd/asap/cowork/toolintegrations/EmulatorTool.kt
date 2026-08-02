package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Tracks the one emulator this server manages — mirrors the real constraint that only one boots meaningfully on a dev machine at a time. Starting a different AVD force-kills whatever's currently running. */
object EmulatorSession {
    data class Running(val process: Process, val avdName: String, val serial: String)

    @Volatile private var running: Running? = null

    fun current(): Running? = running
    fun currentSerial(): String? = running?.serial
    fun set(value: Running?) {
        running = value
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread { running?.process?.destroyForcibly() })
    }
}

object EmulatorTool {
    const val NAME = "manage_emulator"
    private const val BOOT_TIMEOUT_SECONDS = 150L
    private const val POLL_INTERVAL_MS = 2_000L
    private const val LIST_TIMEOUT_SECONDS = 30L
    private const val ADB_PROBE_TIMEOUT_SECONDS = 10L

    val spec = ToolSpec(
        name = NAME,
        description = "Lists, starts, or stops an Android emulator (AVD). action=\"list\" shows available AVDs, action=\"start\" with avdName boots one and waits for it to finish booting — but if an emulator or a real device is already connected, it reuses that instead of starting a new one, so it's safe to call even when something's already running. action=\"stop\" kills the one this session started.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("list", "start", "stop")),
                "avdName" to mapOf("type" to "string", "description" to "Required when action is \"start\"."),
            ),
            "required" to listOf("action"),
        ),
    )

    private val mutex = Mutex()

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            when (input["action"] as? String) {
                "list" -> list()
                "start" -> start(workspaceRoot, input["avdName"] as? String)
                "stop" -> stop()
                else -> ToolResult("\"action\" must be one of: list, start, stop.", isError = true)
            }
        }
    }

    private fun list(): ToolResult = try {
        val builder = ProcessBuilder("emulator", "-list-avds").redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        if (!process.waitFor(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            ToolResult("Timed out listing AVDs.", isError = true)
        } else {
            val names = process.inputStream.bufferedReader().readText().trim()
            if (names.isBlank()) {
                ToolResult("No AVDs found. Create one with Android Studio's Device Manager first.")
            } else {
                ToolResult("Available AVDs:\n$names")
            }
        }
    } catch (e: IOException) {
        notInstalledResult("emulator", e)
    }

    private fun start(workspaceRoot: File, avdName: String?): ToolResult {
        if (avdName.isNullOrBlank()) return ToolResult("\"avdName\" is required for action=\"start\".", isError = true)

        // A device is already usable — an emulator this server booted earlier,
        // one the user already had open, or a real phone plugged in over USB —
        // so reuse it instead of force-killing a perfectly good session just to
        // boot another one.
        DeviceTargeting.resolveSerial()?.let { serial ->
            return ToolResult("A device is already connected (serial $serial) — reusing it instead of starting a new emulator.")
        }

        val logFile = File(workspaceRoot, ".asap-emulator/emulator.log").apply { parentFile.mkdirs() }

        return try {
            val builder = ProcessBuilder(
                "emulator", "-avd", avdName, "-no-snapshot-load", "-netdelay", "none", "-netspeed", "full",
            ).redirectErrorStream(true).redirectOutput(logFile)
            ToolchainEnvironment.configure(builder)
            waitForBoot(builder.start(), avdName, logFile)
        } catch (e: IOException) {
            notInstalledResult("emulator", e)
        }
    }

    private fun waitForBoot(process: Process, avdName: String, logFile: File): ToolResult {
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_SECONDS * 1000
        var serial: String? = null

        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                process.destroyForcibly()
                return ToolResult("Emulator process exited unexpectedly. Log:\n${logFile.readText().takeLast(2000)}", isError = true)
            }
            if (serial == null) {
                serial = findBootedSerial()
            } else if (isBootCompleted(serial)) {
                EmulatorSession.set(EmulatorSession.Running(process, avdName, serial))
                return ToolResult("Emulator \"$avdName\" is booted (serial $serial).")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }

        process.destroyForcibly()
        return ToolResult(
            "Emulator \"$avdName\" didn't finish booting within ${BOOT_TIMEOUT_SECONDS}s. Log:\n${logFile.readText().takeLast(2000)}",
            isError = true,
        )
    }

    private fun findBootedSerial(): String? = try {
        val builder = ProcessBuilder("adb", "devices").redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        process.waitFor(ADB_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readLines()
            .firstOrNull { it.startsWith("emulator-") && it.trimEnd().endsWith("device") }
            ?.substringBefore('\t')?.trim()
    } catch (e: IOException) {
        null
    }

    private fun isBootCompleted(serial: String): Boolean = try {
        val builder = ProcessBuilder("adb", "-s", serial, "shell", "getprop", "sys.boot_completed").redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        process.waitFor(ADB_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim() == "1"
    } catch (e: IOException) {
        false
    }

    private fun stop(): ToolResult {
        val current = EmulatorSession.current() ?: return ToolResult("No emulator is running.")
        try {
            val builder = ProcessBuilder("adb", "-s", current.serial, "emu", "kill").redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            builder.start().waitFor(ADB_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: IOException) {
            // Best-effort graceful stop; force-kill below regardless.
        }
        current.process.destroyForcibly()
        EmulatorSession.set(null)
        AppSession.set(null)
        return ToolResult("Stopped emulator \"${current.avdName}\".")
    }

    private fun notInstalledResult(binary: String, e: IOException): ToolResult =
        ToolResult(
            "\"$binary\" is not installed (or not on PATH). Install the Android SDK, add its platform-tools/emulator directories to PATH, and set ANDROID_HOME. (${e.message})",
            isError = true,
        )
}
