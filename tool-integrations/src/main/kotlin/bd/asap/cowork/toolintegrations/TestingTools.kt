package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The testing agent's tool roster — deliberately introduces no new tools
 * at all. Writing a test file is just writing a file (run_terminal_command
 * already does that, e.g. via a heredoc — the same pattern every other
 * agent's system prompt already asks for; agent-sdk's `parseGeneratedFiles`
 * convention exists but no agent actually uses it), and running one is
 * just invoking whichever stack's own test runner, which the platform
 * agents already cover: [GradleTool] (`test` task — Android, KMP, and a
 * `spring-boot` backend project's own Gradle wrapper), [XcodeBuildTool]
 * (`test` action — iOS), and [FlutterBuildTool] (`test` command —
 * Flutter). React Native (Jest) and the Node/Python/PHP backend stacks
 * have no dedicated typed tool for "run the tests" — [TerminalTool]
 * covers those generically (`npm test`, `pytest`, `vendor/bin/phpunit`, …).
 */
object TestingTools {
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
