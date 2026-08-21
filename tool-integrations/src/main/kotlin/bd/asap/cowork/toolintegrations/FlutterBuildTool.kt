package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Generic `flutter` subcommand runner — the Flutter counterpart of
 * [GradleTool], except Flutter's CLI already covers build, test, and
 * dependency management under one binary rather than one per concern, so
 * one tool wrapping arbitrary subcommands ("build apk --debug", "test",
 * "pub get", "analyze") is the natural shape rather than a tool per
 * subcommand.
 */
object FlutterBuildTool {
    const val NAME = "run_flutter"
    private const val TIMEOUT_SECONDS = 900L
    private const val MAX_OUTPUT_CHARS = 12_000

    val spec = ToolSpec(
        name = NAME,
        description = "Runs a `flutter` subcommand against a project directory in the workspace, e.g. command=\"build apk --debug\", \"build ios --debug --simulator\", \"test\", \"pub get\", or \"analyze\".",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "directory" to mapOf("type" to "string", "description" to "Project directory, relative to the workspace root."),
                "command" to mapOf("type" to "string", "description" to "Everything after \"flutter\", e.g. \"build apk --debug\"."),
            ),
            "required" to listOf("directory", "command"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val directory = input["directory"] as? String
        val command = (input["command"] as? String)?.trim()
        if (directory.isNullOrBlank() || command.isNullOrBlank()) {
            return ToolResult("Both \"directory\" and \"command\" are required.", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(directory)
            ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        val projectDir = projectDirPath.toFile()
        if (!File(projectDir, "pubspec.yaml").exists()) {
            return ToolResult("No pubspec.yaml found in $directory — run create_flutter_project first.", isError = true)
        }

        clearQuarantineOnEngineArtifacts()

        val (success, output) = ProcessRunner.run(
            command = listOf("flutter") + command.split(Regex("\\s+")),
            workDir = projectDir,
            timeoutSeconds = TIMEOUT_SECONDS,
            maxOutputChars = MAX_OUTPUT_CHARS,
            progressPrefix = "flutter $command",
            onProgress = onProgress,
            // Same reasoning as GradleTool's installDebug tasks — pins device-
            // targeted subcommands (install, run) to whichever device/emulator
            // DeviceTargeting already resolved.
            extraEnv = DeviceTargeting.resolveSerial()?.let { mapOf("ANDROID_SERIAL" to it) } ?: emptyMap(),
        )
        return ToolResult(output, isError = !success)
    }

    /**
     * macOS-only. Flutter's engine artifacts (impellerc, gen_snapshot, ...)
     * are fetched over HTTPS, so they land on disk tagged
     * com.apple.quarantine. Gatekeeper pops its "downloaded from the
     * Internet" dialog the first time each one actually runs post-
     * (re)extraction — e.g. `flutter clean` wipes the cached shader output
     * in `.dart_tool/`, forcing impellerc to run again. That's a modal GUI
     * dialog nothing in this headless pipeline can dismiss, so the flag is
     * stripped proactively before every flutter invocation rather than
     * risking a build that hangs waiting for someone to click "Open".
     */
    private fun clearQuarantineOnEngineArtifacts() {
        val flutterSdkPath = ToolchainPathsRegistry.current().flutterSdkPath ?: return
        if (!System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)) return
        runCatching {
            ProcessBuilder("xattr", "-dr", "com.apple.quarantine", "$flutterSdkPath/bin/cache")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }
}
