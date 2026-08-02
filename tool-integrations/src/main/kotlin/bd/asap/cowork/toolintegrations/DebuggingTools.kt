package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The debugging agent's tool roster — the only new tool is
 * [ReadDeviceLogsTool] (a one-shot log snapshot; the existing
 * `LogcatRoutes` SSE stream is for the frontend's Device Logs panel, not
 * something an agentic tool-use loop can call). Everything else —
 * building, booting an emulator/simulator, installing/launching,
 * screenshots, video — reuses the exact tools the platform agents already
 * have working, since diagnosing a failure means doing the same things a
 * developer manually reproducing a bug would: rebuild, relaunch, look at
 * the screen, read the logs.
 */
object DebuggingTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        GradleTool.spec,
        XcodeBuildTool.spec,
        FlutterBuildTool.spec,
        EmulatorTool.spec,
        IosSimulatorTool.spec,
        LaunchAppTool.spec,
        IosLaunchAppTool.spec,
        DeviceScreenshotTool.spec,
        IosScreenshotTool.spec,
        RecordDeviceVideoTool.spec,
        IosVideoTool.spec,
        ReadDeviceLogsTool.spec,
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

    /** A short, human-readable label for a tool call in progress, shown live in the chat UI via AgentEvent.ToolActivity. */
    fun describe(name: String, input: Map<String, Any?>): String = when (name) {
        TerminalTool.NAME -> {
            val command = (input["command"] as? String).orEmpty()
            "Running: ${command.take(MAX_COMMAND_LABEL_LENGTH)}${if (command.length > MAX_COMMAND_LABEL_LENGTH) "…" else ""}"
        }
        GradleTool.NAME -> "Running Gradle: ${(input["task"] as? String) ?: ""}".trim()
        XcodeBuildTool.NAME -> "Running xcodebuild: ${(input["scheme"] as? String) ?: ""}".trim()
        FlutterBuildTool.NAME -> "Running flutter: ${(input["command"] as? String) ?: ""}".trim()
        EmulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Starting emulator ${(input["avdName"] as? String) ?: ""}".trim()
            "stop" -> "Stopping emulator"
            else -> "Listing emulators"
        }
        IosSimulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Booting simulator ${(input["deviceName"] as? String) ?: ""}".trim()
            "stop" -> "Shutting down simulator"
            else -> "Listing simulators"
        }
        LaunchAppTool.NAME -> "Launching ${(input["packageName"] as? String) ?: "app"}".trim()
        IosLaunchAppTool.NAME -> "Launching ${(input["bundleId"] as? String) ?: "app"}".trim()
        DeviceScreenshotTool.NAME -> "Capturing emulator screenshot"
        IosScreenshotTool.NAME -> "Capturing simulator screenshot"
        RecordDeviceVideoTool.NAME -> {
            val seconds = (input["seconds"] as? Number)?.toInt() ?: 30
            "Recording ${seconds}s video of the emulator"
        }
        IosVideoTool.NAME -> {
            val seconds = (input["seconds"] as? Number)?.toInt() ?: 15
            "Recording ${seconds}s video of the simulator"
        }
        ReadDeviceLogsTool.NAME -> "Reading ${(input["platform"] as? String) ?: "device"} logs"
        else -> "Running $name"
    }

    private const val MAX_COMMAND_LABEL_LENGTH = 80
}
