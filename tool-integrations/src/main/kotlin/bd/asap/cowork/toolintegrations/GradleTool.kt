package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/** Always runs a project's own `./gradlew`, never the global `gradle` binary (that's `GradleWrapperGenerator`'s job, once, to create the wrapper in the first place). */
object GradleTool {
    const val NAME = "run_gradle"
    private const val TIMEOUT_SECONDS = 900L
    private const val MAX_OUTPUT_CHARS = 12_000

    val spec = ToolSpec(
        name = NAME,
        description = "Runs one or more Gradle tasks (via the project's own ./gradlew) against a project directory in the workspace, e.g. task=\"assembleDebug\" or task=\"test\".",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "directory" to mapOf("type" to "string", "description" to "Project directory, relative to the workspace root."),
                "task" to mapOf("type" to "string", "description" to "Space-separated Gradle task(s) to run, e.g. \"assembleDebug\"."),
            ),
            "required" to listOf("directory", "task"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val directory = input["directory"] as? String
        val task = (input["task"] as? String)?.trim()
        if (directory.isNullOrBlank() || task.isNullOrBlank()) {
            return ToolResult("Both \"directory\" and \"task\" are required.", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(directory)
            ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        val projectDir = projectDirPath.toFile()

        val gradlew = File(projectDir, "gradlew")
        if (!gradlew.exists()) {
            return ToolResult("No ./gradlew found in $directory — run create_android_project first.", isError = true)
        }
        gradlew.setExecutable(true)

        val (success, output) = ProcessRunner.run(
            command = listOf("./gradlew") + task.split(Regex("\\s+")),
            workDir = projectDir,
            timeoutSeconds = TIMEOUT_SECONDS,
            maxOutputChars = MAX_OUTPUT_CHARS,
            progressPrefix = "Gradle: $task",
            onProgress = onProgress,
            // Only matters for tasks that talk to a device (installDebug etc.) —
            // pins them to whichever device/emulator DeviceTargeting already
            // resolved, so an install doesn't fail/guess wrong when more than
            // one device is connected. Harmless (unused) for build-only tasks.
            extraEnv = DeviceTargeting.resolveSerial()?.let { mapOf("ANDROID_SERIAL" to it) } ?: emptyMap(),
        )
        return ToolResult(output, isError = !success)
    }
}
