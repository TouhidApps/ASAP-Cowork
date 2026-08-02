package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/** The store asset agent's tool roster — [GenerateStoreImageTool] does the actual image compositing; [TerminalTool] finds existing screenshots to use as source material (e.g. under `.asap-screenshots/`) and lists/renames output files. */
object StoreAssetTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(TerminalTool.spec, GenerateStoreImageTool.spec)

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
        GenerateStoreImageTool.NAME -> {
            val width = (input["width"] as? Number)?.toInt()
            val height = (input["height"] as? Number)?.toInt()
            "Generating store image${if (width != null && height != null) " (${width}x${height})" else ""}"
        }
        else -> "Running $name"
    }
}
