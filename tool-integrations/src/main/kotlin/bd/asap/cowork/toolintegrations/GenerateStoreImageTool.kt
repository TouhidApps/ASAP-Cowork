package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Composites an existing screenshot into a fixed-size store-ready image —
 * the mechanical half of PLAN.md's Store Asset Agent ("converts raw
 * screenshots into Play Console/App Store–ready images"). Pure
 * `java.awt`/`ImageIO` rather than shelling out to ImageMagick/vips —
 * neither is installed in this sandbox, and the JDK's own imaging APIs
 * already do everything this needs (scale, composite, rounded-rect clip,
 * text) with no external dependency at all.
 *
 * Deliberately takes a bare `width`/`height` rather than a
 * `platform`/`deviceProfile` enum — the exact set of required dimensions
 * for Play Console and App Store changes over time and isn't this tool's
 * job to hardcode; that knowledge belongs in the agent's system prompt,
 * same as [XcodeBuildTool] not hardcoding scheme names.
 */
object GenerateStoreImageTool {
    const val NAME = "generate_store_image"
    private const val DEFAULT_PADDING_FRACTION = 0.08
    private const val CORNER_RADIUS_FRACTION = 0.04
    private const val MIN_DIMENSION = 16
    private const val MAX_DIMENSION = 8_000
    private const val MIN_CAPTION_FONT_SIZE = 12

    val spec = ToolSpec(
        name = NAME,
        description = "Composites an existing screenshot into a store-ready image: scaled and centered (with rounded corners) on a solid or gradient background canvas of the exact target dimensions, with an optional text caption. Use for Play Store/App Store screenshots, feature graphics, or any other fixed-size store asset.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "inputPath" to mapOf("type" to "string", "description" to "Path to the source screenshot, relative to the workspace root."),
                "outputPath" to mapOf("type" to "string", "description" to "Path to write the PNG result, relative to the workspace root."),
                "width" to mapOf("type" to "integer", "description" to "Target canvas width in pixels."),
                "height" to mapOf("type" to "integer", "description" to "Target canvas height in pixels."),
                "backgroundColor" to mapOf("type" to "string", "description" to "Hex color, e.g. \"#1A1A2E\". Defaults to white."),
                "backgroundColor2" to mapOf("type" to "string", "description" to "A second hex color — if given, the background is a diagonal gradient from backgroundColor to this."),
                "caption" to mapOf("type" to "string", "description" to "Optional headline text drawn above the screenshot."),
                "captionColor" to mapOf("type" to "string", "description" to "Hex color for the caption text. Defaults to a color contrasting the background."),
            ),
            "required" to listOf("inputPath", "outputPath", "width", "height"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult = withContext(Dispatchers.Default) {
        val inputPath = input["inputPath"] as? String
        val outputPath = input["outputPath"] as? String
        val width = (input["width"] as? Number)?.toInt()
        val height = (input["height"] as? Number)?.toInt()
        if (inputPath.isNullOrBlank() || outputPath.isNullOrBlank() || width == null || height == null) {
            return@withContext ToolResult("\"inputPath\", \"outputPath\", \"width\", and \"height\" are all required.", isError = true)
        }
        if (width !in MIN_DIMENSION..MAX_DIMENSION || height !in MIN_DIMENSION..MAX_DIMENSION) {
            return@withContext ToolResult("\"width\"/\"height\" must be between $MIN_DIMENSION and $MAX_DIMENSION.", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val inputFile = workspace.resolve(inputPath)?.toFile()
            ?: return@withContext ToolResult("Invalid or out-of-workspace inputPath: $inputPath", isError = true)
        if (!inputFile.exists()) return@withContext ToolResult("No file found at $inputPath.", isError = true)
        val outputFilePath = workspace.resolve(outputPath)
            ?: return@withContext ToolResult("Invalid or out-of-workspace outputPath: $outputPath", isError = true)

        val source = try {
            ImageIO.read(inputFile) ?: return@withContext ToolResult("$inputPath isn't a readable image format.", isError = true)
        } catch (e: Exception) {
            return@withContext ToolResult("Failed to read $inputPath: ${e.message}", isError = true)
        }

        val backgroundColor = parseHexColor(input["backgroundColor"] as? String) ?: Color.WHITE
        val backgroundColor2 = parseHexColor(input["backgroundColor2"] as? String)
        val caption = (input["caption"] as? String)?.trim()?.ifBlank { null }
        val captionColor = parseHexColor(input["captionColor"] as? String) ?: contrastingTextColor(backgroundColor)

        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            g.paint = if (backgroundColor2 != null) {
                GradientPaint(0f, 0f, backgroundColor, width.toFloat(), height.toFloat(), backgroundColor2)
            } else {
                backgroundColor
            }
            g.fillRect(0, 0, width, height)

            val captionHeight = if (caption != null) (height * 0.10).toInt().coerceAtLeast(32) else 0
            val padding = (minOf(width, height) * DEFAULT_PADDING_FRACTION).toInt()
            val availableWidth = width - 2 * padding
            val availableHeight = height - 2 * padding - captionHeight

            if (caption != null) {
                // Verified live: a fixed font size (proportional to canvas
                // height only) clips long captions off the sides of narrow
                // canvases — shrink-to-fit against the available width too.
                var fontSize = (captionHeight * 0.5).toInt().coerceAtLeast(12)
                g.font = Font("SansSerif", Font.BOLD, fontSize)
                while (fontSize > MIN_CAPTION_FONT_SIZE && g.fontMetrics.stringWidth(caption) > availableWidth) {
                    fontSize -= 2
                    g.font = Font("SansSerif", Font.BOLD, fontSize)
                }
                g.color = captionColor
                val metrics = g.fontMetrics
                val textWidth = metrics.stringWidth(caption).coerceAtMost(availableWidth)
                g.drawString(caption, (width - textWidth) / 2, padding + metrics.ascent)
            }

            val scale = minOf(availableWidth.toDouble() / source.width, availableHeight.toDouble() / source.height, 1.0)
            val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
            val imageX = (width - scaledWidth) / 2
            val imageY = padding + captionHeight + (availableHeight - scaledHeight) / 2

            val cornerRadius = (minOf(scaledWidth, scaledHeight) * CORNER_RADIUS_FRACTION).toFloat()
            val previousClip = g.clip
            g.clip = RoundRectangle2D.Float(imageX.toFloat(), imageY.toFloat(), scaledWidth.toFloat(), scaledHeight.toFloat(), cornerRadius, cornerRadius)
            g.drawImage(source, imageX, imageY, scaledWidth, scaledHeight, null)
            g.clip = previousClip
        } finally {
            g.dispose()
        }

        try {
            outputFilePath.parent?.let { Files.createDirectories(it) }
            ImageIO.write(canvas, "png", outputFilePath.toFile())
            ToolResult("Wrote ${width}x${height} image to $outputPath.")
        } catch (e: Exception) {
            ToolResult("Failed to write $outputPath: ${e.message}", isError = true)
        }
    }

    private fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        return try {
            Color.decode(if (hex.startsWith("#")) hex else "#$hex")
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Simple relative-luminance heuristic — light backgrounds get near-black text, dark backgrounds get white, so a caption is always legible without the caller having to pick. */
    private fun contrastingTextColor(background: Color): Color {
        val luminance = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255
        return if (luminance > 0.6) Color(0x1A, 0x1A, 0x1A) else Color.WHITE
    }
}
