package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/** The imaging agent's tool roster — generate/edit/optimize the actual raster images; TerminalTool lists what's already in images/. */
object ImagingTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(TerminalTool.spec, GenerateImageTool.spec, EditImageTool.spec, OptimizeImageTool.spec)

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
        GenerateImageTool.NAME -> "Generating \"${(input["filename"] as? String) ?: ""}\""
        EditImageTool.NAME -> "Editing \"${(input["filename"] as? String) ?: ""}\""
        OptimizeImageTool.NAME -> "Optimizing \"${(input["filename"] as? String) ?: ""}\""
        else -> "Running $name"
    }
}
