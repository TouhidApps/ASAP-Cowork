package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Shrinks (and optionally center-crops to square) an existing images/ file
 * before it's used as an actual app asset (icon/button/small image) — a
 * generic size-cap, not per-platform density sets (Android mipmap densities,
 * iOS 2x/3x scales are an explicit fast-follow, out of V1 scope). Pure
 * java.awt/ImageIO, same approach as GenerateStoreImageTool — never upscales.
 */
object OptimizeImageTool {
    const val NAME = "optimize_image"
    private const val DEFAULT_MAX_DIMENSION = 512
    private const val MIN_MAX_DIMENSION = 16
    private const val MAX_MAX_DIMENSION = 4096

    val spec = ToolSpec(
        name = NAME,
        description = "Shrinks (and optionally center-crops to a square) an existing image under images/ so it's a reasonable size before it's used as an actual app asset (icon/button/small image) — call this before telling the user an image is ready to drop into their project. Never upscales.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "filename" to mapOf("type" to "string", "description" to "Existing filename under images/, e.g. \"app-icon-concept.png\"."),
                "maxDimension" to mapOf("type" to "integer", "description" to "Longest side is scaled down to at most this many pixels. Defaults to 512 — appropriate for an icon/button/small in-app image."),
                "cropToSquare" to mapOf("type" to "boolean", "description" to "Center-crop to a 1:1 square before scaling. Use for app icons."),
                "destinationPath" to mapOf("type" to "string", "description" to "Where to write the optimized PNG, relative to the workspace root — e.g. straight into the actual project, \"MyApp/app/src/main/res/drawable/icon.png\". Defaults to images/<name>-optimized.png (nested under directory, if given)."),
                "directory" to mapOf(
                    "type" to "string",
                    "description" to "The project directory this image's images/ folder is nested under (same value used to generate/edit it). Ignored if destinationPath is given, since that already specifies a full path.",
                ),
            ),
            "required" to listOf("filename"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult = withContext(Dispatchers.Default) {
        val rawFilename = (input["filename"] as? String)?.trim()
        if (rawFilename.isNullOrBlank() || "/" in rawFilename || "\\" in rawFilename || ".." in rawFilename) {
            return@withContext ToolResult("\"filename\" must be a bare existing name under images/.", isError = true)
        }
        val filename = if ('.' in rawFilename) rawFilename else "$rawFilename.png"
        val maxDimension = (input["maxDimension"] as? Number)?.toInt() ?: DEFAULT_MAX_DIMENSION
        if (maxDimension !in MIN_MAX_DIMENSION..MAX_MAX_DIMENSION) {
            return@withContext ToolResult("\"maxDimension\" must be between $MIN_MAX_DIMENSION and $MAX_MAX_DIMENSION.", isError = true)
        }
        val cropToSquare = input["cropToSquare"] as? Boolean ?: false
        val destinationPath = (input["destinationPath"] as? String)?.trim()?.ifBlank { null }
        val directory = normalizedProjectDirectory(input["directory"] as? String)

        val workspace = Workspace(workspaceRoot.toPath())
        val sourcePath = workspace.resolve(projectRelativePath(directory, "images", filename))
            ?: return@withContext ToolResult("Invalid directory or filename: $directory/$filename", isError = true)
        val sourceFile = sourcePath.toFile()
        if (!sourceFile.exists()) {
            return@withContext ToolResult("No image named \"$filename\" found in images/.", isError = true)
        }

        val source = try {
            ImageIO.read(sourceFile) ?: return@withContext ToolResult("$filename isn't a readable image format.", isError = true)
        } catch (e: Exception) {
            return@withContext ToolResult("Failed to read $filename: ${e.message}", isError = true)
        }

        val cropped = if (cropToSquare) {
            val side = minOf(source.width, source.height)
            source.getSubimage((source.width - side) / 2, (source.height - side) / 2, side, side)
        } else {
            source
        }

        val scale = minOf(maxDimension.toDouble() / maxOf(cropped.width, cropped.height), 1.0)
        val targetWidth = (cropped.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (cropped.height * scale).toInt().coerceAtLeast(1)

        val canvas = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(cropped, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g.dispose()
        }

        val optimizedFilename = "${filename.substringBeforeLast('.')}-optimized.png"
        val relativeDest = destinationPath ?: projectRelativePath(directory, "images", optimizedFilename)
        val destPath = workspace.resolve(relativeDest)
            ?: return@withContext ToolResult("Invalid destinationPath: $relativeDest", isError = true)

        try {
            destPath.parent?.let { Files.createDirectories(it) }
            ImageIO.write(canvas, "png", destPath.toFile())
        } catch (e: Exception) {
            return@withContext ToolResult("Failed to write $relativeDest: ${e.message}", isError = true)
        }

        val summary = "Optimized $filename to ${targetWidth}x${targetHeight}px${if (cropToSquare) " (cropped to square)" else ""}, written to $relativeDest."
        // Only servable if it landed under images/ — a path written straight
        // into the actual coding project has no dedicated serving route
        // (same reasoning GenerateStoreImageTool follows for its outputPath).
        if (destinationPath == null) {
            ToolResult(
                summary,
                imageUrl = projectAssetUrl(directory, "/api/v1/images", optimizedFilename),
                imageAlt = optimizedFilename.substringBeforeLast('.'),
            )
        } else {
            ToolResult(summary)
        }
    }
}
