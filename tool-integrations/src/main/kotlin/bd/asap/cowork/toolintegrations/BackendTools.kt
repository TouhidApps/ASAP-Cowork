package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The backend agent's tool roster — same build-runner dispatch shape as
 * [AndroidTools]. [BackendProjectTool] and [BackendServerTool] are the
 * only backend-specific tools; a scaffolded `spring-boot` project builds
 * and tests exactly like a plain Gradle project, so [GradleTool] is
 * reused as-is for those tasks (never for actually *running* it, which
 * doesn't fit its run-to-completion model — that's what
 * [BackendServerTool] is for, across all four stacks).
 */
object BackendTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        BackendProjectTool.spec,
        BackendServerTool.spec,
        GradleTool.spec,
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
        BackendProjectTool.NAME -> "Creating ${(input["stack"] as? String) ?: "backend"} project ${(input["name"] as? String) ?: ""}".trim()
        BackendServerTool.NAME -> when (input["action"] as? String) {
            "start" -> "Starting ${(input["stack"] as? String) ?: "backend"} server"
            else -> "Stopping backend server"
        }
        GradleTool.NAME -> "Running Gradle: ${(input["task"] as? String) ?: ""}".trim()
        else -> "Running $name"
    }

    private const val MAX_COMMAND_LABEL_LENGTH = 80
}
