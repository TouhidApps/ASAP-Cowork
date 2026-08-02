package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The CI/CD agent's tool roster — introduces no new tools, same reasoning
 * as [TestingTools]: writing a workflow file is just writing a file
 * ([TerminalTool] already does that via a heredoc), and validating it
 * means running the exact same build/test commands the workflow would run
 * in CI, which the platform agents' tools already cover
 * ([GradleTool]/[XcodeBuildTool]/[FlutterBuildTool], plus [TerminalTool]
 * again for npm/pip/php commands with no dedicated typed tool).
 */
object CicdTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(
        TerminalTool.spec,
        GradleTool.spec,
        XcodeBuildTool.spec,
        FlutterBuildTool.spec,
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
        else -> "Running $name"
    }

    private const val MAX_COMMAND_LABEL_LENGTH = 80
}
