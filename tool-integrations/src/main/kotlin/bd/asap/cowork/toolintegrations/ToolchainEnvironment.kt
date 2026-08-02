package bd.asap.cowork.toolintegrations

import java.io.File

/**
 * Applies the currently-configured SDK paths (see [ToolchainPathsRegistry])
 * to a `ProcessBuilder` before it starts: prepends each SDK's bin
 * directory to PATH (so a bare command name like "flutter" or "adb"
 * resolves even if the server process itself wasn't launched with them on
 * PATH) and sets ANDROID_HOME/ANDROID_SDK_ROOT/JAVA_HOME so build tools
 * that read those vars directly (Gradle, AGP) see them too. A no-op for
 * any path that isn't configured — that command falls back to the
 * server's own inherited PATH/env, same as before this existed.
 *
 * [ProcessRunner] calls this for every command it runs; [EmulatorTool] and
 * [DeviceScreenshotTool] call it directly since their boot-polling and
 * `adb` calls don't go through ProcessRunner.
 */
object ToolchainEnvironment {
    fun configure(builder: ProcessBuilder) {
        val paths = ToolchainPathsRegistry.current()
        val env = builder.environment()

        val prependedPathDirs = listOfNotNull(
            paths.flutterSdkPath?.let { "$it/bin" },
            paths.androidSdkPath?.let { "$it/platform-tools" },
            paths.androidSdkPath?.let { "$it/emulator" },
            paths.androidSdkPath?.let { "$it/cmdline-tools/latest/bin" },
            paths.javaHomePath?.let { "$it/bin" },
            // Already a bin directory, unlike the SDK-root paths above — see ToolchainPaths.xcodeGenPath.
            paths.xcodeGenPath,
        )
        if (prependedPathDirs.isNotEmpty()) {
            env["PATH"] = (prependedPathDirs + env["PATH"].orEmpty()).joinToString(File.pathSeparator)
        }
        paths.androidSdkPath?.let {
            env["ANDROID_HOME"] = it
            env["ANDROID_SDK_ROOT"] = it
        }
        paths.javaHomePath?.let { env["JAVA_HOME"] = it }
        // Points xcodebuild/xcrun at a specific Xcode.app for this one process,
        // without needing `sudo xcode-select -s` to change it system-wide —
        // handy when more than one Xcode version is installed.
        paths.xcodePath?.let { env["DEVELOPER_DIR"] = "$it/Contents/Developer" }
    }
}
