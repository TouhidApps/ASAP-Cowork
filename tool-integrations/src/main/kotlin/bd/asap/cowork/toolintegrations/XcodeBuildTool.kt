package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File
import java.util.concurrent.TimeUnit

/** `xcodebuild` counterpart of [GradleTool] — always targets the Simulator destination (no physical-device/signing support in v1, mirroring how [EmulatorTool] is Android's only supported target), builds or tests a scheme, and reports back the built .app path for [IosLaunchAppTool] to install. */
object XcodeBuildTool {
    const val NAME = "run_xcodebuild"
    private const val BUILD_TIMEOUT_SECONDS = 900L
    private const val SETTINGS_TIMEOUT_SECONDS = 60L
    private const val MAX_OUTPUT_CHARS = 12_000

    val spec = ToolSpec(
        name = NAME,
        description = "Runs xcodebuild against an Xcode project directory in the workspace, targeting the iOS Simulator (never a physical device or a signed archive). action=\"build\" (default) or action=\"test\".",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "directory" to mapOf("type" to "string", "description" to "Project directory (containing the .xcodeproj), relative to the workspace root."),
                "scheme" to mapOf("type" to "string", "description" to "The Xcode scheme to build/test, usually the project/app name."),
                "action" to mapOf("type" to "string", "enum" to listOf("build", "test"), "description" to "Defaults to \"build\"."),
            ),
            "required" to listOf("directory", "scheme"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val directory = input["directory"] as? String
        val scheme = (input["scheme"] as? String)?.trim()
        val action = (input["action"] as? String)?.trim()?.ifBlank { null } ?: "build"
        if (directory.isNullOrBlank() || scheme.isNullOrBlank()) {
            return ToolResult("Both \"directory\" and \"scheme\" are required.", isError = true)
        }
        if (action !in setOf("build", "test")) {
            return ToolResult("\"action\" must be \"build\" or \"test\".", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(directory)
            ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        val projectDir = projectDirPath.toFile()

        val xcodeproj = projectDir.listFiles { file -> file.name.endsWith(".xcodeproj") }?.firstOrNull()
            ?: return ToolResult("No .xcodeproj found in $directory — run create_ios_project first.", isError = true)

        val destination = IosSimulatorTargeting.resolveUdid()?.let { "platform=iOS Simulator,id=$it" }
            ?: "generic/platform=iOS Simulator"

        val (success, output) = ProcessRunner.run(
            command = listOf(
                "xcodebuild", "-project", xcodeproj.name, "-scheme", scheme, "-destination", destination, action,
            ),
            workDir = projectDir,
            timeoutSeconds = BUILD_TIMEOUT_SECONDS,
            maxOutputChars = MAX_OUTPUT_CHARS,
            progressPrefix = "xcodebuild $action: $scheme",
            onProgress = onProgress,
        )
        if (!success || action != "build") return ToolResult(output, isError = !success)

        val appPath = resolveBuiltAppPath(projectDir, xcodeproj.name, scheme, destination)
        val appNote = appPath?.let { "\n\nApp bundle: $it" } ?: ""
        return ToolResult(output + appNote)
    }

    /** Asks `xcodebuild` itself for the build's actual output location rather than guessing DerivedData's path — same reasoning as [RecordDeviceVideoTool] deriving `--size` from `adb shell wm size` instead of assuming one. */
    private fun resolveBuiltAppPath(projectDir: File, xcodeprojName: String, scheme: String, destination: String): String? = try {
        val builder = ProcessBuilder(
            "xcodebuild", "-project", xcodeprojName, "-scheme", scheme, "-destination", destination, "-showBuildSettings",
        ).directory(projectDir).redirectErrorStream(true)
        ToolchainEnvironment.configure(builder)
        val process = builder.start()
        val finished = process.waitFor(SETTINGS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return null
        }
        val output = process.inputStream.bufferedReader().readLines()
        val builtProductsDir = output.firstNotNullOfOrNull { line -> settingValue(line, "BUILT_PRODUCTS_DIR") }
        val fullProductName = output.firstNotNullOfOrNull { line -> settingValue(line, "FULL_PRODUCT_NAME") }
        if (builtProductsDir == null || fullProductName == null) return null
        File(builtProductsDir, fullProductName).absolutePath
    } catch (e: Exception) {
        null
    }

    private fun settingValue(line: String, key: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("$key =")) return null
        return trimmed.substringAfter("=").trim().takeIf { it.isNotBlank() }
    }
}
