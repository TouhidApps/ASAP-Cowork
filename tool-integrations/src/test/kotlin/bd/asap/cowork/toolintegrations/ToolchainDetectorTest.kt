package bd.asap.cowork.toolintegrations

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolchainDetectorTest {
    private val root = createTempDirectory("toolchain-detector-test").toFile()

    @Test
    fun `detectXcode reports a fake full Xcode app as available and never installable`() {
        val xcodeApp = File(root, "Xcode.app")
        File(xcodeApp, "Contents/Developer/usr/bin").mkdirs()
        File(xcodeApp, "Contents/Developer/usr/bin/xcodebuild").apply { writeText("#!/bin/sh\n"); setExecutable(true) }

        val result = ToolchainDetector.detectXcode(xcodeApp.absolutePath)

        assertEquals(xcodeApp.absolutePath, result.detectedPath)
        assertTrue(result.available)
        assertFalse(result.installable, "Xcode has no Homebrew cask — must never be reported auto-installable")
    }

    @Test
    fun `detectXcode rejects a directory without a real xcodebuild binary, like a Command Line Tools-only install`() {
        val cltOnly = File(root, "CommandLineTools").apply { mkdirs() }

        val result = ToolchainDetector.detectXcode(cltOnly.absolutePath)

        assertNull(result.detectedPath)
        assertFalse(result.available)
    }

    @Test
    fun `detectXcodeGen finds a binary directly in the configured bin directory (no extra bin subpath)`() {
        val binDir = File(root, "homebrew-bin").apply { mkdirs() }
        File(binDir, "xcodegen").apply { writeText("#!/bin/sh\n"); setExecutable(true) }

        val result = ToolchainDetector.detectXcodeGen(binDir.absolutePath)

        assertEquals(binDir.absolutePath, result.detectedPath)
        assertTrue(result.available)
    }

    @Test
    fun `detectXcodeGen reports unavailable when the configured path has no xcodegen binary`() {
        val emptyDir = File(root, "empty-bin").apply { mkdirs() }

        val result = ToolchainDetector.detectXcodeGen(emptyDir.absolutePath)

        assertFalse(result.available)
    }
}
