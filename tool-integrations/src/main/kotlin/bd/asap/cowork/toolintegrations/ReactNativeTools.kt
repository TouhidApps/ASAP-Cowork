package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The React Native agent's tool roster — same build-runner dispatch shape
 * as [AndroidTools]/[IosTools]. [ReactNativeProjectTool] and [MetroTool]
 * are the only React-Native-specific tools; a scaffolded project's
 * `android/`/`ios/` subprojects build and run exactly like plain
 * Android/iOS projects, so building, emulator/simulator management,
 * install+launch, and screenshot/video capture all reuse
 * [GradleTool]/[XcodeBuildTool]/[EmulatorTool]/[IosSimulatorTool]/
 * [LaunchAppTool]/[IosLaunchAppTool]/[DeviceScreenshotTool]/
 * [IosScreenshotTool]/[RecordDeviceVideoTool]/[IosVideoTool] as-is.
 */
object ReactNativeTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        ReactNativeProjectTool.spec,
        MetroTool.spec,
        GradleTool.spec,
        XcodeBuildTool.spec,
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
        ReactNativeProjectTool.NAME -> "Creating React Native project ${(input["name"] as? String) ?: ""}".trim()
        MetroTool.NAME -> when (input["action"] as? String) {
            "start" -> "Starting Metro bundler"
            else -> "Stopping Metro bundler"
        }
        GradleTool.NAME -> "Running Gradle: ${(input["task"] as? String) ?: ""}".trim()
        XcodeBuildTool.NAME -> "Running xcodebuild: ${(input["scheme"] as? String) ?: ""}".trim()
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
