package bd.asap.cowork.toolintegrations

import java.io.File

/**
 * Best-effort, macOS+Homebrew-only installers backing the admin panel's
 * toolchain "Install" buttons. Reuses [ProcessRunner] rather than a
 * separate process-running helper — these are just long-running,
 * infrequent commands like any other tool call.
 */
object ToolchainInstaller {
    private const val INSTALL_TIMEOUT_SECONDS = 900L
    private const val MAX_OUTPUT_CHARS = 4_000
    private val HOME = File(System.getProperty("user.home"))

    suspend fun installFlutter(): Pair<Boolean, String> = run(listOf("brew", "install", "--cask", "flutter"))

    suspend fun installJava(): Pair<Boolean, String> = run(listOf("brew", "install", "openjdk@21"))

    /** No `installXcode()` — there's no Homebrew cask for it (unlike Flutter/Android's cmdline-tools), only the App Store or Apple's developer download page, neither of which is scriptable here. [ToolchainDetector.detectXcode] always reports `installable = false` for exactly this reason. */
    suspend fun installXcodeGen(): Pair<Boolean, String> = run(listOf("brew", "install", "xcodegen"))

    suspend fun installAndroidSdk(): Pair<Boolean, String> {
        val (caskOk, caskOutput) = run(listOf("brew", "install", "--cask", "android-commandlinetools"))
        if (!caskOk) return false to caskOutput

        val (prefixOk, prefixOutput) = run(listOf("brew", "--prefix"))
        if (!prefixOk) return false to "Installed cmdline-tools, but couldn't resolve Homebrew's prefix:\n$prefixOutput"
        val brewPrefix = prefixOutput.trim().lineSequence().last()
        val sdkmanager = File(brewPrefix, "share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager")
        if (!sdkmanager.canExecute()) {
            return false to "Installed cmdline-tools, but sdkmanager wasn't found at ${sdkmanager.absolutePath}."
        }

        val androidHome = File(HOME, "Library/Android/sdk").apply { mkdirs() }.absolutePath
        run(listOf("sh", "-c", "yes | '${sdkmanager.absolutePath}' --sdk_root='$androidHome' --licenses"))
        val (installOk, installOutput) = run(
            listOf("sh", "-c", "yes | '${sdkmanager.absolutePath}' --sdk_root='$androidHome' --install platform-tools emulator"),
        )
        if (!installOk) return false to "Installed cmdline-tools, but failed to install platform-tools/emulator:\n$installOutput"

        return true to "Installed Android cmdline-tools, platform-tools (adb), and the emulator binary at $androidHome."
    }

    private suspend fun run(command: List<String>): Pair<Boolean, String> = ProcessRunner.run(
        command = command,
        workDir = HOME,
        timeoutSeconds = INSTALL_TIMEOUT_SECONDS,
        maxOutputChars = MAX_OUTPUT_CHARS,
        progressPrefix = "Installing",
    )
}
