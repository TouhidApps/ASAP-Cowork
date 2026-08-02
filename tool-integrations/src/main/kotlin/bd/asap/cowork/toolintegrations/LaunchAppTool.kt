package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Launches an already-installed app's launcher activity on the currently
 * running emulator, by package name — `run_gradle installDebug` only
 * installs the APK, it doesn't start it. Uses `adb shell monkey -c
 * android.intent.category.LAUNCHER 1` rather than `am start -n
 * pkg/.Activity` so the caller only needs the applicationId, not the exact
 * launcher activity class name.
 */
object LaunchAppTool {
    const val NAME = "launch_app"
    private const val ADB_TIMEOUT_SECONDS = 20L

    val spec = ToolSpec(
        name = NAME,
        description = "Launches an already-installed app on the currently running emulator by package name (e.g. after run_gradle installDebug). Starts the app's launcher activity, so you don't need to know its class name — just the applicationId.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "packageName" to mapOf("type" to "string", "description" to "The app's applicationId, e.g. com.example.myapp."),
            ),
            "required" to listOf("packageName"),
        ),
    )

    suspend fun execute(input: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val packageName = (input["packageName"] as? String)?.trim()
        if (packageName.isNullOrBlank()) return@withContext ToolResult("\"packageName\" is required.", isError = true)

        val serial = DeviceTargeting.resolveSerial()
            ?: return@withContext ToolResult("No emulator or device is connected. Start one with manage_emulator first.", isError = true)

        try {
            val builder = ProcessBuilder(
                "adb", "-s", serial, "shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1",
            ).redirectErrorStream(true)
            ToolchainEnvironment.configure(builder)
            val process = builder.start()
            val finished = process.waitFor(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            if (!finished) {
                process.destroyForcibly()
                return@withContext ToolResult("Timed out launching $packageName.", isError = true)
            }
            if (process.exitValue() != 0 || output.contains("No activities found", ignoreCase = true)) {
                return@withContext ToolResult("Failed to launch $packageName (is it installed?):\n$output", isError = true)
            }
            AppSession.set(packageName)
            ToolResult("Launched $packageName.")
        } catch (e: IOException) {
            ToolResult(
                "adb is not installed (or not on PATH). Install the Android SDK platform-tools and set ANDROID_HOME. (${e.message})",
                isError = true,
            )
        }
    }
}
