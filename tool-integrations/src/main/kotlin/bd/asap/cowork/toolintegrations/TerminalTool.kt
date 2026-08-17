package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Escape hatch for anything not covered by a dedicated tool (listing files,
 * git, package manager installs, ...). Ported from the old prototype's
 * `TerminalTools`, minus its `ASAP-Projects` stray-file-relocation quirk
 * (that convention isn't used here) and its regex absolute-path sniffing
 * (weak, false-positive-prone — this is a local single-developer tool per
 * PLAN.md's deployment decision, so the real trust boundary is "the user
 * is running their own agent against their own machine on purpose").
 */
object TerminalTool {
    const val NAME = "run_terminal_command"
    private const val TIMEOUT_SECONDS = 60L
    private const val MAX_OUTPUT_CHARS = 8_000

    val spec = ToolSpec(
        name = NAME,
        description = "Runs a shell command inside the project workspace. Use for anything not covered by a dedicated tool (listing files, git, npm/pub install, etc.).",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "command" to mapOf("type" to "string", "description" to "The shell command to run."),
            ),
            "required" to listOf("command"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val command = (input["command"] as? String)?.trim()
        if (command.isNullOrBlank()) return ToolResult("Missing required \"command\" input.", isError = true)
        if (isGitForcePush(command)) {
            return ToolResult("Refusing to run a force-push (git push -f/--force/...).", isError = true)
        }
        if (".asap-history" in command) {
            return ToolResult("Refusing to run a command referencing .asap-history — that's the workspace's internal change-history store, not a project file.", isError = true)
        }

        val (success, output) = ProcessRunner.run(
            command = listOf("sh", "-c", command),
            workDir = workspaceRoot,
            timeoutSeconds = TIMEOUT_SECONDS,
            maxOutputChars = MAX_OUTPUT_CHARS,
            progressPrefix = "Running: $command",
            onProgress = onProgress,
        )
        return ToolResult(output, isError = !success)
    }

    private fun isGitForcePush(command: String): Boolean {
        val tokens = command.split(Regex("\\s+"))
        val forceFlags = setOf("-f", "--force", "--force-with-lease", "--force-if-includes")
        return "git" in tokens && "push" in tokens && tokens.any { it in forceFlags }
    }
}
