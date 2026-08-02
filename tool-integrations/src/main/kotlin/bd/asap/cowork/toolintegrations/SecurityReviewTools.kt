package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.buildrunner.BuildRunnerClient
import java.io.File

/**
 * The security review agent's tool roster. [ScanForSecretsTool] and
 * [CheckDependencyVulnerabilitiesTool] are the two genuinely new tools;
 * [TerminalTool] covers reading dependency/lock files to build the
 * vulnerability check's input, and running whatever stack-specific
 * linter is already available (eslint, bandit, detekt, …) — this agent
 * doesn't install one itself, only uses what's already there.
 */
object SecurityReviewTools {
    private val buildRunner = BuildRunnerClient()

    val specs: List<ToolSpec> = listOf(TerminalTool.spec, ScanForSecretsTool.spec, CheckDependencyVulnerabilitiesTool.spec)

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
        ScanForSecretsTool.NAME -> "Scanning for secrets"
        CheckDependencyVulnerabilitiesTool.NAME -> {
            val count = (input["dependencies"] as? List<*>)?.size
            "Checking${count?.let { " $it" } ?: ""} dependencies for known vulnerabilities"
        }
        else -> "Running $name"
    }
}
