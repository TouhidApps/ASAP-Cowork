package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The Android agent's tool roster, dispatched by name. Every tool that
 * actually shells out (Gradle, adb, the emulator, arbitrary terminal
 * commands) is dispatched through [BuildRunnerClient] rather than executed
 * in-process — see PLAN.md §5 and the `build-runner` module: this process
 * (the android-agent, running inside chat-gateway) never invokes those
 * tools itself anymore. Wraps every call in one top-level try/catch — this
 * runs mid-stream inside the agentic loop, so an uncaught exception here
 * would otherwise tear down the whole streaming response instead of just
 * failing one tool call.
 */
object AndroidTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        AndroidProjectTool.spec,
        GradleTool.spec,
        EmulatorTool.spec,
        LaunchAppTool.spec,
        DeviceScreenshotTool.spec,
        RecordDeviceVideoTool.spec,
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

    /** A short, human-readable label for a tool call in progress (e.g. "Running Gradle: assembleDebug"), shown live in the chat UI via AgentEvent.ToolActivity. */
    fun describe(name: String, input: Map<String, Any?>): String = when (name) {
        TerminalTool.NAME -> {
            val command = (input["command"] as? String).orEmpty()
            "Running: ${command.take(MAX_COMMAND_LABEL_LENGTH)}${if (command.length > MAX_COMMAND_LABEL_LENGTH) "…" else ""}"
        }
        AndroidProjectTool.NAME -> "Creating Android project ${(input["name"] as? String) ?: ""}".trim()
        GradleTool.NAME -> "Running Gradle: ${(input["task"] as? String) ?: ""}".trim()
        EmulatorTool.NAME -> when (input["action"] as? String) {
            "start" -> "Starting emulator ${(input["avdName"] as? String) ?: ""}".trim()
            "stop" -> "Stopping emulator"
            else -> "Listing emulators"
        }
        LaunchAppTool.NAME -> "Launching ${(input["packageName"] as? String) ?: "app"}".trim()
        DeviceScreenshotTool.NAME -> "Capturing emulator screenshot"
        RecordDeviceVideoTool.NAME -> {
            val seconds = (input["seconds"] as? Number)?.toInt() ?: 10
            "Recording ${seconds}s video of the emulator"
        }
        else -> "Running $name"
    }

    private const val MAX_COMMAND_LABEL_LENGTH = 80
}
