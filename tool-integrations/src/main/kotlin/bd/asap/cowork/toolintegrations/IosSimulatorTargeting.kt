package bd.asap.cowork.toolintegrations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One row of `xcrun simctl list devices --json`'s `devices` map, flattened out of its per-runtime grouping. */
data class SimulatorDevice(val udid: String, val name: String, val state: String, val isAvailable: Boolean)

/**
 * Picks/lists simulators the same way [DeviceTargeting] does for Android
 * devices — via `xcrun simctl` directly rather than trusting only what
 * [IosSimulatorSession] itself booted, so a simulator already running (e.g.
 * left open from Xcode) is used instead of ignored.
 */
object IosSimulatorTargeting {
    private const val SIMCTL_TIMEOUT_SECONDS = 15L
    private val json = Json { ignoreUnknownKeys = true }

    /** Every simulator `simctl` currently knows about, across every installed runtime. */
    fun listDevices(): List<SimulatorDevice> = try {
        val builder = ProcessBuilder("xcrun", "simctl", "list", "devices", "--json").redirectErrorStream(false)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        val finished = process.waitFor(SIMCTL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return emptyList()
        }
        val output = process.inputStream.bufferedReader().readText()
        parseDevices(output)
    } catch (e: IOException) {
        emptyList()
    }

    fun parseDevices(simctlJson: String): List<SimulatorDevice> {
        val root = runCatching { json.parseToJsonElement(simctlJson).jsonObject }.getOrNull() ?: return emptyList()
        val devicesByRuntime = root["devices"]?.jsonObject ?: return emptyList()
        return devicesByRuntime.values.filterIsInstance<JsonArray>().flatMap { it.jsonArray }
            .mapNotNull { it.jsonObject.toSimulatorDeviceOrNull() }
    }

    private fun JsonObject.toSimulatorDeviceOrNull(): SimulatorDevice? {
        val udid = this["udid"]?.jsonPrimitive?.content ?: return null
        val name = this["name"]?.jsonPrimitive?.content ?: return null
        val state = this["state"]?.jsonPrimitive?.content ?: "Unknown"
        val isAvailable = this["isAvailable"]?.jsonPrimitive?.boolean ?: true
        return SimulatorDevice(udid, name, state, isAvailable)
    }

    /** The simulator this session booted (if `simctl` still confirms it's running), otherwise any other already-booted simulator — same reuse-what's-already-running logic as [DeviceTargeting.resolveSerial]. */
    fun resolveUdid(): String? {
        val booted = listDevices().filter { it.state == "Booted" }
        IosSimulatorSession.currentUdid()?.let { tracked -> if (booted.any { it.udid == tracked }) return tracked }
        return booted.firstOrNull()?.udid
    }
}
