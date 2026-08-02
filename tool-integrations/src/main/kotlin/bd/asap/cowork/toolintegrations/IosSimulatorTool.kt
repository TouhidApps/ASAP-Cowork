package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/** iOS Simulator counterpart of [EmulatorTool] — same list/start/stop shape, `xcrun simctl` instead of the Android `emulator`/`adb` binaries. */
object IosSimulatorTool {
    const val NAME = "manage_ios_simulator"
    private const val BOOT_TIMEOUT_SECONDS = 90L
    private const val POLL_INTERVAL_MS = 1_500L
    private const val SIMCTL_TIMEOUT_SECONDS = 20L

    val spec = ToolSpec(
        name = NAME,
        description = "Lists, boots, or shuts down an iOS Simulator device. action=\"list\" shows available simulators (name, state). action=\"start\" with deviceName (e.g. \"iPhone 15\") boots one — reuses it if already booted. action=\"stop\" shuts down the one this session started.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("list", "start", "stop")),
                "deviceName" to mapOf("type" to "string", "description" to "Required when action is \"start\", e.g. \"iPhone 15\"."),
            ),
            "required" to listOf("action"),
        ),
    )

    private val mutex = Mutex()

    suspend fun execute(input: Map<String, Any?>): ToolResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            when (input["action"] as? String) {
                "list" -> list()
                "start" -> start(input["deviceName"] as? String)
                "stop" -> stop()
                else -> ToolResult("\"action\" must be one of: list, start, stop.", isError = true)
            }
        }
    }

    private fun list(): ToolResult {
        val devices = IosSimulatorTargeting.listDevices().filter { it.isAvailable }
        if (devices.isEmpty()) {
            return ToolResult(
                "No simulators found (or \"xcrun simctl\" isn't available — this requires the full Xcode.app, not just the Command Line Tools).",
                isError = true,
            )
        }
        val listing = devices.joinToString("\n") { "${it.name} — ${it.state} (${it.udid})" }
        return ToolResult("Available simulators:\n$listing")
    }

    private fun start(deviceName: String?): ToolResult {
        if (deviceName.isNullOrBlank()) return ToolResult("\"deviceName\" is required for action=\"start\".", isError = true)

        val match = IosSimulatorTargeting.listDevices()
            .filter { it.isAvailable }
            .firstOrNull { it.name.equals(deviceName, ignoreCase = true) }
            ?: IosSimulatorTargeting.listDevices().filter { it.isAvailable }.firstOrNull { it.name.contains(deviceName, ignoreCase = true) }
            ?: return ToolResult("No simulator matching \"$deviceName\" found. Call action=\"list\" to see available devices.", isError = true)

        if (match.state == "Booted") {
            IosSimulatorSession.set(match.udid)
            return ToolResult("Simulator \"${match.name}\" is already booted (${match.udid}) — reusing it.")
        }

        return try {
            val builder = ProcessBuilder("xcrun", "simctl", "boot", match.udid).redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            val process = builder.start()
            val started = process.waitFor(SIMCTL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!started) {
                process.destroyForcibly()
                return ToolResult("Timed out issuing the boot command for \"${match.name}\".", isError = true)
            }
            waitForBoot(match)
        } catch (e: IOException) {
            notInstalledResult(e)
        }
    }

    private fun waitForBoot(target: SimulatorDevice): ToolResult {
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            val current = IosSimulatorTargeting.listDevices().firstOrNull { it.udid == target.udid }
            if (current?.state == "Booted") {
                IosSimulatorSession.set(target.udid)
                return ToolResult("Simulator \"${target.name}\" is booted (${target.udid}).")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return ToolResult("Simulator \"${target.name}\" didn't finish booting within ${BOOT_TIMEOUT_SECONDS}s.", isError = true)
    }

    private fun stop(): ToolResult {
        val udid = IosSimulatorSession.currentUdid() ?: return ToolResult("No simulator is running.")
        return try {
            val builder = ProcessBuilder("xcrun", "simctl", "shutdown", udid).redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            builder.start().waitFor(SIMCTL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            IosSimulatorSession.set(null)
            ToolResult("Shut down simulator $udid.")
        } catch (e: IOException) {
            notInstalledResult(e)
        }
    }

    private fun notInstalledResult(e: IOException): ToolResult =
        ToolResult(
            "\"xcrun simctl\" isn't available (${e.message}) — this requires the full Xcode.app (not just the Command Line Tools). Install Xcode from the App Store, then run \"sudo xcode-select -s /Applications/Xcode.app\".",
            isError = true,
        )
}
