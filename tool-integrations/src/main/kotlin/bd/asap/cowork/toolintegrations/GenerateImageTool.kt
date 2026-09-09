package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.imagegen.ImageAspect
import bd.asap.cowork.imagegen.ImageProviderException
import bd.asap.cowork.imagegen.ImageProviders
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File
import java.nio.file.Files

/**
 * Generates a new image from a text prompt via whichever Image AI provider
 * (OpenAI or Gemini) is configured in the admin panel, and writes it to an
 * images/ folder under a caller-given, descriptive filename — that filename
 * is this image's permanent name for later edit_image calls and for the
 * chat UI's thumbnail alt text. [directory], when given, nests images/
 * inside a specific scaffolded project instead of the shared workspace
 * root, so multiple projects' images don't collide.
 */
object GenerateImageTool {
    const val NAME = "generate_image"

    val spec = ToolSpec(
        name = NAME,
        description = "Generates a new image (UI mockup, logo, background, icon concept, ...) from a text prompt via a real image-generation API, and saves it under a descriptive name in images/. The result includes a real URL the chat UI renders inline — never construct that URL yourself.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "prompt" to mapOf("type" to "string", "description" to "A detailed description of the image to generate."),
                "filename" to mapOf(
                    "type" to "string",
                    "description" to "A short, descriptive, kebab-case filename with no extension or path, e.g. \"login-screen-mockup\" or \"app-icon-concept\". This becomes the image's permanent name for later edit_image/optimize_image calls.",
                ),
                "aspect" to mapOf(
                    "type" to "string",
                    "enum" to listOf("square", "portrait", "landscape"),
                    "description" to "Overall shape. \"square\" for icons/logos, \"portrait\" for phone-screen mockups, \"landscape\" for backgrounds/banners. Defaults to square.",
                ),
                "directory" to mapOf(
                    "type" to "string",
                    "description" to "The scaffolded project's directory name (e.g. \"WeatherNow\"), so images/ nests inside that project instead of the shared workspace root — pass the same value every time for the same project. Leave blank only if nothing project-specific has been scaffolded yet.",
                ),
            ),
            "required" to listOf("prompt", "filename"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val prompt = (input["prompt"] as? String)?.trim()
        val rawFilename = (input["filename"] as? String)?.trim()
        if (prompt.isNullOrBlank() || rawFilename.isNullOrBlank()) {
            return ToolResult("\"prompt\" and \"filename\" are both required.", isError = true)
        }
        if ("/" in rawFilename || "\\" in rawFilename || ".." in rawFilename) {
            return ToolResult("\"filename\" must be a bare filename, no path separators.", isError = true)
        }
        val filename = "${rawFilename.substringBeforeLast('.')}.png"
        val directory = normalizedProjectDirectory(input["directory"] as? String)
        val aspect = when ((input["aspect"] as? String)?.lowercase()) {
            "portrait" -> ImageAspect.PORTRAIT
            "landscape" -> ImageAspect.LANDSCAPE
            else -> ImageAspect.SQUARE
        }

        val provider = ImageProviders.current()
            ?: return ToolResult("Image generation isn't configured yet — set an OpenAI or Gemini image API key in the admin panel's Image AI section.", isError = true)

        val workspace = Workspace(workspaceRoot.toPath())
        val relativePath = projectRelativePath(directory, "images", filename)
        val outputPath = workspace.resolve(relativePath)
            ?: return ToolResult("Invalid directory or filename: $directory/$filename", isError = true)

        return try {
            val image = provider.generate(prompt, aspect)
            Files.createDirectories(outputPath.parent)
            Files.write(outputPath, image.bytes)
            ToolResult(
                "Generated $relativePath.",
                imageUrl = projectAssetUrl(directory, "/api/v1/images", filename),
                imageAlt = filename.substringBeforeLast('.'),
            )
        } catch (e: ImageProviderException) {
            ToolResult(e.message ?: "Image generation failed.", isError = true)
        } catch (e: Exception) {
            ToolResult("Image generation failed: ${e.message}", isError = true)
        }
    }
}
