package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Installs (if `appPath` is given — normally the path [XcodeBuildTool]
 * reported after a build) and launches an app on the currently booted
 * simulator by bundle identifier. Two `simctl` calls in one tool because,
 * unlike Android's `run_gradle installDebug` + `launch_app` split, there's
 * no equivalent "already installed, just start it" shortcut worth exposing
 * separately — every launch after a fresh build needs both anyway.
 */
object IosLaunchAppTool {
    const val NAME = "launch_ios_app"
    private const val SIMCTL_TIMEOUT_SECONDS = 30L

    val spec = ToolSpec(
        name = NAME,
        description = "Installs (if appPath is given, e.g. from run_xcodebuild's result) and launches an app on the currently booted iOS Simulator by bundle identifier.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "bundleId" to mapOf("type" to "string", "description" to "The app's bundle identifier, e.g. com.asap.myapp."),
                "appPath" to mapOf("type" to "string", "description" to "Path to the built .app bundle to install first. Omit if it's already installed."),
            ),
            "required" to listOf("bundleId"),
        ),
    )

    suspend fun execute(input: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val bundleId = (input["bundleId"] as? String)?.trim()
        if (bundleId.isNullOrBlank()) return@withContext ToolResult("\"bundleId\" is required.", isError = true)
        val appPath = (input["appPath"] as? String)?.trim()?.ifBlank { null }

        val udid = IosSimulatorTargeting.resolveUdid()
            ?: return@withContext ToolResult("No simulator is booted. Start one with manage_ios_simulator first.", isError = true)

        try {
            if (appPath != null) {
                val (success, output) = runSimctl("install", udid, appPath)
                if (!success) return@withContext ToolResult("Failed to install $appPath:\n$output", isError = true)
            }
            val (success, output) = runSimctl("launch", udid, bundleId)
            if (!success) return@withContext ToolResult("Failed to launch $bundleId (is it installed?):\n$output", isError = true)
            ToolResult("Launched $bundleId.")
        } catch (e: IOException) {
            ToolResult(
                "\"xcrun simctl\" isn't available (${e.message}) — this requires the full Xcode.app, not just the Command Line Tools.",
                isError = true,
            )
        }
    }

    private fun runSimctl(vararg args: String): Pair<Boolean, String> {
        val builder = ProcessBuilder(listOf("xcrun", "simctl") + args).redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        val finished = process.waitFor(SIMCTL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        if (!finished) {
            process.destroyForcibly()
            return false to "Timed out."
        }
        return (process.exitValue() == 0) to output
    }
}
