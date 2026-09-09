package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.imagegen.ImageProviderException
import bd.asap.cowork.imagegen.ImageProviders
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File
import java.nio.file.Files

/**
 * Edits an existing images/ file (found by the exact name a prior
 * generate_image call gave it) via a text instruction, e.g. "make the
 * background blue". Always writes a new, versioned filename rather than
 * overwriting — both the original and every revision stay individually
 * nameable/viewable/referenceable. [directory] must match whatever
 * generate_image used for this same image, so the edit lands next to the
 * original instead of in the shared workspace root.
 */
object EditImageTool {
    const val NAME = "edit_image"

    val spec = ToolSpec(
        name = NAME,
        description = "Edits an existing image (referenced by the exact name it was generated/edited under) via a text instruction, e.g. \"make the background blue\" or \"add a shadow\". Use this instead of generate_image whenever the user is revising something that already exists.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "filename" to mapOf("type" to "string", "description" to "The exact existing filename under images/, e.g. \"login-screen-mockup.png\" (extension optional)."),
                "instruction" to mapOf("type" to "string", "description" to "What to change, e.g. \"make the background blue\"."),
                "directory" to mapOf(
                    "type" to "string",
                    "description" to "The same project directory name passed to the generate_image call that created this image — leave blank only if that call left it blank too.",
                ),
            ),
            "required" to listOf("filename", "instruction"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val rawFilename = (input["filename"] as? String)?.trim()
        val instruction = (input["instruction"] as? String)?.trim()
        if (rawFilename.isNullOrBlank() || instruction.isNullOrBlank()) {
            return ToolResult("\"filename\" and \"instruction\" are both required.", isError = true)
        }
        if ("/" in rawFilename || "\\" in rawFilename || ".." in rawFilename) {
            return ToolResult("\"filename\" must be a bare filename, no path separators.", isError = true)
        }
        val filename = if ('.' in rawFilename) rawFilename else "$rawFilename.png"
        val directory = normalizedProjectDirectory(input["directory"] as? String)

        val provider = ImageProviders.current()
            ?: return ToolResult("Image generation isn't configured yet — set an OpenAI or Gemini image API key in the admin panel's Image AI section.", isError = true)

        val workspace = Workspace(workspaceRoot.toPath())
        val sourcePath = workspace.resolve(projectRelativePath(directory, "images", filename))
            ?: return ToolResult("Invalid directory or filename: $directory/$filename", isError = true)
        val sourceFile = sourcePath.toFile()
        if (!sourceFile.exists()) {
            return ToolResult("No image named \"$filename\" found in images/. Use generate_image to create it first, or double-check the name and directory.", isError = true)
        }

        val mimeType = when (sourceFile.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            else -> "image/png"
        }

        return try {
            val edited = provider.edit(instruction, sourceFile.readBytes(), mimeType)
            val newFilename = nextVersionedName(sourcePath.parent.toFile(), filename)
            val relativePath = projectRelativePath(directory, "images", newFilename)
            val outputPath = workspace.resolve(relativePath)!!
            Files.createDirectories(outputPath.parent)
            Files.write(outputPath, edited.bytes)
            ToolResult(
                "Edited $filename -> $relativePath.",
                imageUrl = projectAssetUrl(directory, "/api/v1/images", newFilename),
                imageAlt = newFilename.substringBeforeLast('.'),
            )
        } catch (e: ImageProviderException) {
            ToolResult(e.message ?: "Image edit failed.", isError = true)
        } catch (e: Exception) {
            ToolResult("Image edit failed: ${e.message}", isError = true)
        }
    }

    /** "logo.png" -> "logo-v2.png" -> "logo-v3.png", skipping any name already taken. */
    private fun nextVersionedName(imagesDir: File, filename: String): String {
        val base = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.', "png")
        val stripped = Regex("-v\\d+$").replace(base, "")
        var version = 2
        while (File(imagesDir, "$stripped-v$version.$ext").exists()) version++
        return "$stripped-v$version.$ext"
    }
}
