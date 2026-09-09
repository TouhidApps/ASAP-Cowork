package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File
import java.nio.file.Files

/**
 * Writes a text file (an SVG logo, brand-guide.md, ...) into a `branding/`
 * folder and returns a real, servable URL — [ToolResult.imageUrl] for an
 * `.svg` (chat-gateway's `/api/v1/branding/{path...}` route, see
 * Routing.kt), [ToolResult.fileUrl] otherwise. [directory], when given,
 * nests that `branding/` folder inside a specific scaffolded project
 * subdirectory (e.g. `directory = "WeatherNow"` writes to
 * `WeatherNow/branding/...`) rather than the shared workspace root, so
 * multiple projects' branding don't collide — leave it blank only when
 * nothing project-specific has been scaffolded yet. Exists specifically so
 * the branding agent never has to construct that URL itself in freeform
 * reply text: writing SVGs via `run_terminal_command` heredocs left the
 * model with nothing to actually link to, so it fabricated a
 * plausible-looking but nonexistent one instead.
 */
object WriteBrandAssetTool {
    const val NAME = "write_brand_asset"

    val spec = ToolSpec(
        name = NAME,
        description = "Writes a text file (an SVG logo, brand-guide.md, ...) to a branding/ folder. The result includes a real URL the chat UI renders inline/links to automatically — never construct that URL yourself, always rely on what this tool returns.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "filename" to mapOf(
                    "type" to "string",
                    "description" to "Bare filename, no subdirectories, e.g. \"logo-wordmark.svg\", \"icon.svg\", or \"brand-guide.md\".",
                ),
                "content" to mapOf("type" to "string", "description" to "The full file content to write."),
                "directory" to mapOf(
                    "type" to "string",
                    "description" to "The scaffolded project's directory name (e.g. \"WeatherNow\"), so branding/ nests inside that project instead of the shared workspace root — pass the same value every time for the same project. Leave blank only if nothing project-specific has been scaffolded yet.",
                ),
            ),
            "required" to listOf("filename", "content"),
        ),
    )

    fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val filename = (input["filename"] as? String)?.trim()
        val content = input["content"] as? String
        if (filename.isNullOrBlank() || content == null) {
            return ToolResult("\"filename\" and \"content\" are both required.", isError = true)
        }
        if ("/" in filename || "\\" in filename || ".." in filename) {
            return ToolResult("\"filename\" must be a bare filename, no path separators.", isError = true)
        }
        val directory = normalizedProjectDirectory(input["directory"] as? String)

        val workspace = Workspace(workspaceRoot.toPath())
        val relativePath = projectRelativePath(directory, "branding", filename)
        val outputPath = workspace.resolve(relativePath)
            ?: return ToolResult("Invalid directory or filename: $directory/$filename", isError = true)

        Files.createDirectories(outputPath.parent)
        outputPath.toFile().writeText(content)

        val url = projectAssetUrl(directory, "/api/v1/branding", filename)
        return if (filename.endsWith(".svg", ignoreCase = true)) {
            ToolResult("Wrote $relativePath.", imageUrl = url, imageAlt = filename.substringBeforeLast('.'))
        } else {
            ToolResult("Wrote $relativePath.", fileUrl = url)
        }
    }
}
