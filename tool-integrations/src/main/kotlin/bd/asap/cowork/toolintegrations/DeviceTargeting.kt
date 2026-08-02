package bd.asap.cowork.toolintegrations

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Picks which device/emulator serial [EmulatorTool], [LaunchAppTool],
 * [DeviceScreenshotTool], [RecordDeviceVideoTool] and [GradleTool] (install
 * tasks) should target. [EmulatorSession] only knows about an emulator this
 * server itself launched — it has no idea about an emulator the user
 * already had open (e.g. from Android Studio) or a real phone plugged in
 * over USB, so relying on it alone meant those tools claimed "no emulator
 * is running" even with a perfectly usable device sitting right there.
 * This checks `adb devices` directly instead, so anything already
 * connected just gets used.
 */
object DeviceTargeting {
    private const val ADB_TIMEOUT_SECONDS = 10L

    /**
     * Preference order: the emulator this server is tracking (if adb still
     * confirms it's connected), then a real device (so a plugged-in phone
     * is used whenever one's available), then any other already-booted
     * emulator adb sees.
     */
    fun resolveSerial(): String? {
        val connected = connectedDevices()
        EmulatorSession.currentSerial()?.let { tracked -> if (tracked in connected) return tracked }
        return connected.firstOrNull { !isEmulator(it) } ?: connected.firstOrNull()
    }

    /** adb always names emulator serials `emulator-<port>`; a real device's serial is its hardware ID, never that shape. */
    fun isEmulator(serial: String): Boolean = serial.startsWith("emulator-")

    /** Every serial `adb devices` currently reports in the ready "device" state (excludes "offline"/"unauthorized"). */
    fun connectedDevices(): List<String> = try {
        val builder = ProcessBuilder("adb", "devices").redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        process.waitFor(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readLines()
            .filter { it.trimEnd().endsWith("device") && !it.startsWith("List of devices") }
            .map { it.substringBefore('\t').trim() }
    } catch (e: IOException) {
        emptyList()
    }
}
