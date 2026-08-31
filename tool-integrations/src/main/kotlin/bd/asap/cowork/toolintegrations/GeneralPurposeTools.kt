package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The general-purpose agent's tool roster — a catch-all for requests that
 * don't fit any specialized agent (send an email via a CLI mailer, edit an
 * arbitrary XML/config file, install or uninstall a package, run a one-off
 * command). [TerminalTool] already covers all of this as a shell escape
 * hatch, so there's nothing specialized to add here.
 */
object GeneralPurposeTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(TerminalTool.spec)

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
        else -> "Running $name"
    }
}
