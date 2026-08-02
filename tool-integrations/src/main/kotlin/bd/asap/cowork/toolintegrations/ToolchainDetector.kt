package bd.asap.cowork.toolintegrations

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Best-effort discovery of each SDK's root directory, independent of
 * whatever is (or isn't) configured in [ToolchainPathsRegistry]. Pure
 * filesystem/env checks, no subprocess spawning — cheap enough to run on
 * every toolchain status request.
 */
object ToolchainDetector {
    fun detectFlutter(configuredPath: String?): DetectedToolchainPath {
        val configured = configuredPath?.takeIf { hasBinary(it, "flutter") }
        val detected = configured
            ?: resolveSdkRootFromPath("flutter")
            ?: wellKnownFlutterRoots().firstOrNull { hasBinary(it, "flutter") }
        return DetectedToolchainPath(
            configuredPath = configuredPath,
            detectedPath = detected,
            available = detected != null,
            installable = canAutoInstall(),
        )
    }

    fun detectAndroidSdk(configuredPath: String?): DetectedToolchainPath {
        val configured = configuredPath?.takeIf { hasAdb(it) }
        val detected = configured
            ?: System.getenv("ANDROID_HOME")?.takeIf { hasAdb(it) }
            ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { hasAdb(it) }
            ?: defaultAndroidSdkPath().takeIf { hasAdb(it) }
        return DetectedToolchainPath(
            configuredPath = configuredPath,
            detectedPath = detected,
            available = detected != null,
            installable = canAutoInstall(),
        )
    }

    fun detectJava(configuredPath: String?): DetectedToolchainPath {
        val configured = configuredPath?.takeIf { hasBinary(it, "java") }
        val detected = configured
            ?: System.getenv("JAVA_HOME")?.takeIf { hasBinary(it, "java") }
            // The JVM currently running this very server always has a valid
            // installation — the one fallback that's never actually missing.
            ?: System.getProperty("java.home")?.takeIf { hasBinary(it, "java") }
        return DetectedToolchainPath(
            configuredPath = configuredPath,
            detectedPath = detected,
            available = detected != null,
            installable = canAutoInstall(),
        )
    }

    /**
     * A full Xcode.app, not just the Command Line Tools — `xcodebuild`,
     * `xcrun simctl` etc. resolve to a real binary either way (both ship
     * `/usr/bin/xcodebuild`), but CLT's copy refuses to do anything useful
     * ("xcodebuild requires Xcode, but active developer directory ... is a
     * command line tools instance"), which is exactly the failure mode
     * [XcodeBuildTool]/[IosSimulatorTool] hit without a real Xcode
     * installed. Never auto-installable — there's no Homebrew cask for it,
     * only the App Store or Apple's developer download page.
     */
    fun detectXcode(configuredPath: String?): DetectedToolchainPath {
        val configured = configuredPath?.takeIf { isFullXcode(it) }
        val detected = configured ?: activeDeveloperDirXcode() ?: wellKnownXcodeRoots().firstOrNull { isFullXcode(it) }
        return DetectedToolchainPath(
            configuredPath = configuredPath,
            detectedPath = detected,
            available = detected != null,
            installable = false,
        )
    }

    fun detectXcodeGen(configuredPath: String?): DetectedToolchainPath {
        val configured = configuredPath?.takeIf { hasBinaryDirect(it, "xcodegen") }
        val detected = configured
            ?: resolveBinDirFromPath("xcodegen")
            ?: wellKnownXcodeGenBinDirs().firstOrNull { hasBinaryDirect(it, "xcodegen") }
        return DetectedToolchainPath(
            configuredPath = configuredPath,
            detectedPath = detected,
            available = detected != null,
            installable = canAutoInstall(),
        )
    }

    /** Unlike [hasBinary], [binDir] here already *is* the directory the binary lives in — no "bin" subpath to append (see [ToolchainPaths.xcodeGenPath]). */
    private fun hasBinaryDirect(binDir: String, command: String): Boolean = File(binDir, command).canExecute()

    /** Same PATH-resolution idea as [resolveSdkRootFromPath], but returns the bin directory itself rather than its parent — right for a bare-binary tool like xcodegen, which has no SDK root above its bin dir. */
    private fun resolveBinDirFromPath(command: String): String? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparator)) {
            val candidate = File(dir, command)
            if (candidate.canExecute()) return candidate.canonicalFile.parentFile?.absolutePath
        }
        return null
    }

    /** True for an actual Xcode.app bundle (has Contents/Developer/usr/bin/xcodebuild), false for a bare Command Line Tools install or a nonexistent path. */
    private fun isFullXcode(path: String): Boolean = File(path, "Contents/Developer/usr/bin/xcodebuild").canExecute()

    /** `xcode-select -p` reports the active developer directory — only useful here if it's inside a real Xcode.app (a CLT-only setup points at /Library/Developer/CommandLineTools instead). */
    private fun activeDeveloperDirXcode(): String? = try {
        val process = ProcessBuilder("xcode-select", "-p").redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        val developerDir = process.inputStream.bufferedReader().readText().trim()
        // ".../Xcode.app/Contents/Developer" -> ".../Xcode.app"
        developerDir.substringBefore("/Contents/Developer").takeIf { it.endsWith(".app") && isFullXcode(it) }
    } catch (e: IOException) {
        null
    }

    private fun wellKnownXcodeRoots(): List<String> = listOf("/Applications/Xcode.app")

    /** Homebrew's default formula bin dirs on Apple Silicon and Intel. */
    private fun wellKnownXcodeGenBinDirs(): List<String> = listOf("/opt/homebrew/bin", "/usr/local/bin")

    fun canAutoInstall(): Boolean = isMac() && commandOnPath("brew")

    private fun isMac(): Boolean = System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true

    private fun hasBinary(sdkRoot: String, command: String): Boolean = File(File(sdkRoot, "bin"), command).canExecute()

    private fun hasAdb(sdkRoot: String): Boolean = File(sdkRoot, "platform-tools/adb").canExecute()

    private fun defaultAndroidSdkPath(): String = File(System.getProperty("user.home"), "Library/Android/sdk").absolutePath

    /** Homebrew's default cask install locations on Apple Silicon and Intel, plus the manual-download convention of dropping it straight in $HOME. */
    private fun wellKnownFlutterRoots(): List<String> {
        val caskVersionDirs = listOf("/opt/homebrew/Caskroom/flutter", "/usr/local/Caskroom/flutter")
            .flatMap { File(it).listFiles { file -> file.isDirectory }?.toList().orEmpty() }
        val home = System.getProperty("user.home")
        return caskVersionDirs.map { File(it, "flutter").absolutePath } + File(home, "flutter").absolutePath
    }

    private fun commandOnPath(command: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparator).any { File(it, command).canExecute() }
    }

    /** Resolves a bare command found on PATH to its SDK root — the parent of the containing "bin" directory, following symlinks (Homebrew casks symlink just the binary itself into $(brew --prefix)/bin, pointing at the real Caskroom install). */
    private fun resolveSdkRootFromPath(command: String): String? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparator)) {
            val candidate = File(dir, command)
            if (candidate.canExecute()) {
                val bin = candidate.canonicalFile.parentFile
                if (bin?.name == "bin") return bin.parentFile?.absolutePath
            }
        }
        return null
    }
}
