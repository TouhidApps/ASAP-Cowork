package bd.asap.cowork.toolintegrations

/**
 * SDK/tool root directories tools shell out to, beyond what's already on
 * the server process's own PATH. All optional — null means "resolve from
 * PATH/ANDROID_HOME/JAVA_HOME the normal way".
 */
data class ToolchainPaths(
    val flutterSdkPath: String? = null,
    val androidSdkPath: String? = null,
    val javaHomePath: String? = null,
    /** Path to Xcode.app itself (e.g. "/Applications/Xcode.app"), not a "Contents/Developer" or "bin" subpath — see [ToolchainEnvironment], which derives DEVELOPER_DIR from it. */
    val xcodePath: String? = null,
    /** Directory containing the `xcodegen` binary (e.g. a Homebrew prefix's "bin") — unlike the other SDK paths this already *is* a bin directory, there's no "/bin" subpath to append. */
    val xcodeGenPath: String? = null,
)

/** One SDK's detection result, driving the admin panel's "use this path?" / install prompts. */
data class DetectedToolchainPath(
    val configuredPath: String?,
    val detectedPath: String?,
    val available: Boolean,
    val installable: Boolean,
)

/**
 * Live, in-memory view of the currently-configured SDK paths, read
 * directly (no DI) by [ToolchainEnvironment] — called synchronously from
 * every tool's process-launch path. chat-gateway's toolchain settings
 * store is the only writer: it seeds this at startup and updates it
 * whenever the admin panel saves new paths, so a running server picks up
 * the change on the very next tool call with no restart.
 */
object ToolchainPathsRegistry {
    @Volatile private var current: ToolchainPaths = ToolchainPaths()

    fun current(): ToolchainPaths = current

    fun set(paths: ToolchainPaths) {
        current = paths
    }
}
