package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unlike the scaffolding tools' tests, this exercises the real thing end
 * to end on every run — pure `java.awt`/`ImageIO`, no subprocess, no
 * network, so there's no cost to actually generating and reading back a
 * PNG. A real caption-clipping bug was caught this way during manual
 * testing (a long caption with a height-only-proportional font size
 * overflowed a narrow canvas) — [`clips a caption to the canvas width`]
 * guards against a regression of exactly that.
 */
class GenerateStoreImageToolTest {
    private val workspaceRoot = createTempDirectory("store-image-tool-test").toFile()

    private fun writeSourceImage(name: String, width: Int = 400, height: Int = 800): java.io.File {
        val file = workspaceRoot.resolve(name)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "png", file)
        return file
    }

    @Test
    fun `composites a screenshot onto a canvas at the exact requested dimensions`() = runBlocking {
        writeSourceImage("shot.png")

        val result = GenerateStoreImageTool.execute(
            workspaceRoot,
            mapOf("inputPath" to "shot.png", "outputPath" to "out.png", "width" to 1080, "height" to 1920),
        )

        assertFalse(result.isError, result.summary)
        val output = ImageIO.read(workspaceRoot.resolve("out.png"))
        assertEquals(1080, output.width)
        assertEquals(1920, output.height)
    }

    @Test
    fun `a long caption never overflows the canvas width`() = runBlocking {
        writeSourceImage("shot2.png")

        val result = GenerateStoreImageTool.execute(
            workspaceRoot,
            mapOf(
                "inputPath" to "shot2.png",
                "outputPath" to "out2.png",
                "width" to 600,
                "height" to 1000,
                "caption" to "This is a deliberately long caption that would overflow a narrow canvas",
            ),
        )

        assertFalse(result.isError, result.summary)
        // The real regression this guards: text drawn past the canvas edge
        // gets silently clipped by AWT, not an exception — so the only
        // reliable check is that non-background pixels never appear
        // outside a small margin from either edge in the caption's row band.
        val output = ImageIO.read(workspaceRoot.resolve("out2.png"))
        val captionRow = (output.height * 0.05).toInt().coerceIn(0, output.height - 1)
        val backgroundRgb = output.getRGB(0, captionRow)
        val marginPx = 4
        for (x in 0 until marginPx) {
            assertEquals(backgroundRgb, output.getRGB(x, captionRow), "caption bled into the left edge margin")
            assertEquals(backgroundRgb, output.getRGB(output.width - 1 - x, captionRow), "caption bled into the right edge margin")
        }
    }

    @Test
    fun `rejects a missing input file`() = runBlocking {
        val result = GenerateStoreImageTool.execute(
            workspaceRoot,
            mapOf("inputPath" to "does-not-exist.png", "outputPath" to "out.png", "width" to 100, "height" to 100),
        )
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an out-of-range dimension`() = runBlocking {
        writeSourceImage("shot3.png")
        val result = GenerateStoreImageTool.execute(
            workspaceRoot,
            mapOf("inputPath" to "shot3.png", "outputPath" to "out3.png", "width" to 999_999, "height" to 100),
        )
        assertTrue(result.isError)
    }
}
