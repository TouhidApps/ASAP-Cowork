package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Writes a text file (an SVG logo, brand-guide.md, ...) into the
 * workspace's `branding/` folder and returns a real, servable URL —
 * [ToolResult.imageUrl] for an `.svg` (chat-gateway's
 * `/api/v1/branding/{filename}` route, see Routing.kt), [ToolResult.fileUrl]
 * otherwise. Exists specifically so the branding agent never has to
 * construct that URL itself in freeform reply text: writing SVGs via
 * `run_terminal_command` heredocs left the model with nothing to actually
 * link to, so it fabricated a plausible-looking but nonexistent one instead.
 */
object WriteBrandAssetTool {
    const val NAME = "write_brand_asset"

    val spec = ToolSpec(
        name = NAME,
        description = "Writes a text file (an SVG logo, brand-guide.md, ...) to the workspace's branding/ folder. The result includes a real URL the chat UI renders inline/links to automatically — never construct that URL yourself, always rely on what this tool returns.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "filename" to mapOf(
                    "type" to "string",
                    "description" to "Bare filename, no subdirectories, e.g. \"logo-wordmark.svg\", \"icon.svg\", or \"brand-guide.md\".",
                ),
                "content" to mapOf("type" to "string", "description" to "The full file content to write."),
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

        val brandingDir = File(workspaceRoot, "branding").apply { mkdirs() }
        File(brandingDir, filename).writeText(content)

        val url = "/api/v1/branding/$filename"
        return if (filename.endsWith(".svg", ignoreCase = true)) {
            ToolResult("Wrote branding/$filename.", imageUrl = url, imageAlt = filename.substringBeforeLast('.'))
        } else {
            ToolResult("Wrote branding/$filename.", fileUrl = url)
        }
    }
}
