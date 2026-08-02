package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The performance agent's tool roster — introduces no new tools.
 * Startup time (`adb shell am start -W`), memory (`adb shell dumpsys
 * meminfo`), and battery (`adb shell dumpsys batterystats`) are all just
 * [TerminalTool] commands with output the agent interprets itself; the
 * value this agent adds is knowing which commands to run and how to read
 * them, not a new mechanical capability. [GradleTool]/[EmulatorTool]/
 * [LaunchAppTool] (and their iOS counterparts) get the app built,
 * installed, and running first, reused as-is from the platform agents.
 */
object PerformanceTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        GradleTool.spec,
        EmulatorTool.spec,
        LaunchAppTool.spec,
        IosSimulatorTool.spec,
        IosLaunchAppTool.spec,
    )

    private val TOOL_NAMES = specs.map { it.name }.toSet()

    fun executorFor(workspaceRoot: File): ToolExecutor = ToolExecutor { name, input, onProgress ->
        try {
            if (name !in TOOL_NAMES) {
                ToolResult("Unknown tool: $name", isError = true)
            } else {
                buildRunner.execute(name, workspaceRoot, input, onProgress)
            }
        } catch (e: Exception) {
            ToolResult("Tool \"$name\" failed unexpectedly: ${e.message}", isError = true)
        }
    }

    fun describe(name: String, input: Map<String, Any?>): String = when (name) {
        TerminalTool.NAME -> {
            val command = (input["command"] as? String).orEmpty()
            "Running: ${command.take(80)}${if (command.length > 80) "…" else ""}"
        }
        GradleTool.NAME -> "Running Gradle: ${(input["task"] as? String) ?: ""}".trim()
        EmulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Starting emulator ${(input["avdName"] as? String) ?: ""}".trim()
            "stop" -> "Stopping emulator"
            else -> "Listing emulators"
        }
        LaunchAppTool.NAME -> "Launching ${(input["packageName"] as? String) ?: "app"}".trim()
        IosSimulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Booting simulator ${(input["deviceName"] as? String) ?: ""}".trim()
            "stop" -> "Shutting down simulator"
            else -> "Listing simulators"
        }
        IosLaunchAppTool.NAME -> "Launching ${(input["bundleId"] as? String) ?: "app"}".trim()
        else -> "Running $name"
    }
}
