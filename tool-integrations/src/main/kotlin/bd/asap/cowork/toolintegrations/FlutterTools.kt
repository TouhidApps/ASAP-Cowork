package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The Flutter agent's tool roster — same build-runner dispatch shape as
 * [AndroidTools]/[IosTools]. Device/emulator/simulator management,
 * install+launch, screenshots, and video capture are *not* reimplemented
 * here: a Flutter app still ultimately runs on the same Android emulator
 * or iOS Simulator those two agents already have working tools for, so
 * this roster just reuses [EmulatorTool]/[LaunchAppTool]/
 * [DeviceScreenshotTool]/[RecordDeviceVideoTool] (Android) and
 * [IosSimulatorTool]/[IosLaunchAppTool]/[IosScreenshotTool]/[IosVideoTool]
 * (iOS) alongside the two Flutter-specific tools.
 */
object FlutterTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        FlutterProjectTool.spec,
        FlutterBuildTool.spec,
        EmulatorTool.spec,
        LaunchAppTool.spec,
        DeviceScreenshotTool.spec,
        RecordDeviceVideoTool.spec,
        IosSimulatorTool.spec,
        IosLaunchAppTool.spec,
        IosScreenshotTool.spec,
        IosVideoTool.spec,
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
        FlutterProjectTool.NAME -> "Creating Flutter project ${(input["name"] as? String) ?: ""}".trim()
        FlutterBuildTool.NAME -> "Running flutter: ${(input["command"] as? String) ?: ""}".trim()
        EmulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Starting emulator ${(input["avdName"] as? String) ?: ""}".trim()
            "stop" -> "Stopping emulator"
            else -> "Listing emulators"
        }
        LaunchAppTool.NAME -> "Launching ${(input["packageName"] as? String) ?: "app"}".trim()
        DeviceScreenshotTool.NAME -> "Capturing emulator screenshot"
        RecordDeviceVideoTool.NAME -> {
            val seconds = (input["seconds"] as? Number)?.toInt() ?: 30
            "Recording ${seconds}s video of the emulator"
        }
        IosSimulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Booting simulator ${(input["deviceName"] as? String) ?: ""}".trim()
            "stop" -> "Shutting down simulator"
            else -> "Listing simulators"
        }
        IosLaunchAppTool.NAME -> "Launching ${(input["bundleId"] as? String) ?: "app"}".trim()
        IosScreenshotTool.NAME -> "Capturing simulator screenshot"
        IosVideoTool.NAME -> {
            val seconds = (input["seconds"] as? Number)?.toInt() ?: 15
            "Recording ${seconds}s video of the simulator"
        }
        else -> "Running $name"
    }

    private const val MAX_COMMAND_LABEL_LENGTH = 80
}
