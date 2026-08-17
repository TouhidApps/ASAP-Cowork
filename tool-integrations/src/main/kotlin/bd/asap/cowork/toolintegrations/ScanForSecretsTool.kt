package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * A curated, pattern-based scan for accidentally committed secrets —
 * `grep -rE` over the workspace with a fixed set of well-known credential
 * formats, the same mechanical check every other tool here reaches for
 * before something riskier: real subprocess, real regex engine, no
 * reinvented file-walking logic. Deliberately not exhaustive and says so
 * in its own result text — a pattern list can never replace an actual
 * secret-scanning product (gitleaks, trufflehog), it just catches the
 * common accidental-commit cases without needing either installed.
 */
object ScanForSecretsTool {
    const val NAME = "scan_for_secrets"
    private const val TIMEOUT_SECONDS = 60L
    private const val MAX_OUTPUT_CHARS = 6_000
    private val EXCLUDE_DIRS = listOf(".git", "node_modules", "build", ".gradle", ".kotlin", "venv", "dist", ".next", "vendor", ".asap-screenshots", ".asap-videos", ".asap-history")

    // Case-insensitive matching (grep -i) throughout — the fixed-prefix
    // formats (AKIA, AIza, ghp_, sk_live_, xox…) are only ever generated in
    // exactly that case in practice, so this loses no precision, and it
    // means the generic assignment pattern doesn't need its own flag.
    private val PATTERN = listOf(
        "AKIA[0-9A-Z]{16}", // AWS access key ID
        "AIza[0-9A-Za-z_-]{35}", // Google API key
        "-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----", // PEM private key header
        "xox[baprs]-[0-9A-Za-z-]{10,48}", // Slack token
        "sk_live_[0-9A-Za-z]{16,}", // Stripe live secret key
        "gh[oprsu]_[0-9A-Za-z]{36}", // GitHub token (personal access / OAuth / etc.)
        "(api[_-]?key|secret|password|token)[\"']?[[:space:]]*[:=][[:space:]]*[\"'][A-Za-z0-9/+_.-]{16,}[\"']", // generic hardcoded assignment
    ).joinToString("|")

    val spec = ToolSpec(
        name = NAME,
        description = "Scans the workspace for accidentally committed secrets — AWS/Google/Stripe/GitHub/Slack key formats, PEM private key headers, and generic hardcoded api_key/secret/password/token assignments. Not exhaustive, and can false-positive on placeholder/example values — review every match, don't assume a clean result means there are no secrets.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "directory" to mapOf("type" to "string", "description" to "Subdirectory to scan, relative to the workspace root. Defaults to the whole workspace."),
            ),
            "required" to emptyList<String>(),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val directory = (input["directory"] as? String)?.trim()?.ifBlank { null }
        val targetDir = if (directory != null) {
            Workspace(workspaceRoot.toPath()).resolve(directory)?.toFile()
                ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        } else {
            workspaceRoot
        }
        if (!targetDir.isDirectory) return ToolResult("No such directory: ${directory ?: "."}", isError = true)

        val excludeArgs = EXCLUDE_DIRS.map { "--exclude-dir=$it" }
        val command = listOf("grep", "-rInE", "-i") + excludeArgs + listOf(PATTERN, ".")
        val (success, output) = ProcessRunner.run(
            command = command,
            workDir = targetDir,
            timeoutSeconds = TIMEOUT_SECONDS,
            maxOutputChars = MAX_OUTPUT_CHARS,
            progressPrefix = "Scanning for secrets",
        )

        // grep exits 1 for "ran fine, found nothing" — that's a clean scan,
        // not a tool failure. Exit codes >= 2 are real errors (bad path, etc.).
        val exitCode = Regex("""Exit code: (-?\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            success -> ToolResult(
                "Potential secrets found — review each one; this pattern-based scan can false-positive on placeholder/example values:\n$output",
                isError = true,
            )
            exitCode == 1 -> ToolResult("No obvious secrets found (pattern-based scan, not exhaustive — it can miss real secrets it has no pattern for).")
            else -> ToolResult("Scan failed:\n$output", isError = true)
        }
    }
}
