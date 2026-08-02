package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Scaffolds a new Flutter project — unlike [AndroidProjectTool]/[IosProjectTool],
 * there's no hand-written template to maintain at all: `flutter create` is
 * the one true generator for both the Dart app skeleton and the
 * platform-specific `android/`/`ios/` subprojects (themselves a Gradle
 * project and an Xcode project respectively, generated the same way a
 * plain native project would be). This tool is just a thin, validated
 * wrapper around that command.
 */
object FlutterProjectTool {
    const val NAME = "create_flutter_project"
    private const val CREATE_TIMEOUT_SECONDS = 300L
    private val FOLDER_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*$")
    private val ORG_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    val spec = ToolSpec(
        name = NAME,
        description = "Scaffolds a new Flutter project (via `flutter create`) as a subdirectory of the workspace, including its Android and iOS platform subprojects.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf(
                    "type" to "string",
                    "description" to "Project directory name — must be a valid Dart package name: lowercase letters, digits, underscores, starting with a letter.",
                ),
                "org" to mapOf("type" to "string", "description" to "Organization identifier, e.g. com.example. Defaults to com.asap."),
                "platforms" to mapOf(
                    "type" to "string",
                    "description" to "Comma-separated target platforms, e.g. \"android,ios\" (the default).",
                ),
            ),
            "required" to listOf("name"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val name = (input["name"] as? String)?.trim().orEmpty()
        if (!FOLDER_NAME_PATTERN.matches(name)) {
            return ToolResult(
                "\"name\" must be a valid Dart package name: lowercase letters, digits, and underscores, starting with a letter.",
                isError = true,
            )
        }

        val org = (input["org"] as? String)?.trim()?.ifBlank { null } ?: "com.asap"
        if (!ORG_PATTERN.matches(org)) {
            return ToolResult("\"org\" must be a valid reverse-DNS identifier, e.g. com.example.", isError = true)
        }

        val platforms = (input["platforms"] as? String)?.trim()?.ifBlank { null } ?: "android,ios"

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(name)
            ?: return ToolResult("\"$name\" isn't a valid project directory name.", isError = true)
        if (projectDirPath.toFile().exists()) {
            return ToolResult("A project named \"$name\" already exists in the workspace.", isError = true)
        }

        val (success, output) = ProcessRunner.run(
            command = listOf("flutter", "create", "--org", org, "--platforms", platforms, name),
            workDir = workspaceRoot,
            timeoutSeconds = CREATE_TIMEOUT_SECONDS,
            maxOutputChars = 4_000,
            progressPrefix = "Creating Flutter project",
        )
        return ToolResult(output, isError = !success)
    }
}
