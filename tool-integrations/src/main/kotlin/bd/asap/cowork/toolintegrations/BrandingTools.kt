package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/** The branding agent's tool roster — [WriteBrandAssetTool] writes the logo SVGs and brand guide with a real, servable URL back; [TerminalTool] is the escape hatch for anything else (listing files, etc.). */
object BrandingTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(TerminalTool.spec, WriteBrandAssetTool.spec)

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
        WriteBrandAssetTool.NAME -> "Writing branding/${(input["filename"] as? String) ?: ""}".trim()
        else -> "Running $name"
    }
}
