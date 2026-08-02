package bd.asap.cowork.firebase

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.GradleTool
import bd.asap.cowork.toolintegrations.ProcessRunner
import java.io.File

/**
 * Builds (if needed) and uploads an APK to Firebase App Distribution —
 * what runs when the user says "upload/share/distribute the app" in
 * chat. Reuses [GradleTool] for the build rather than shelling to
 * `./gradlew` a second way, and shells to the `firebase` CLI for the
 * upload itself (must be on PATH — `npm install -g firebase-tools`, then
 * `firebase login:ci` to get a CI token for the admin panel).
 *
 * Only Gradle/Android projects are supported — there's no Flutter build
 * tooling in this codebase yet to build a Flutter APK in the first place.
 */
object FirebaseDistributeTool {
    const val NAME = "distribute_apk"
    private const val UPLOAD_TIMEOUT_SECONDS = 300L
    private const val MAX_OUTPUT_CHARS = 4_000
    private val VALID_VARIANTS = setOf("debug", "release")

    val spec = ToolSpec(
        name = NAME,
        description = "Builds (if needed) and uploads an APK to Firebase App Distribution so testers can install it. Requires Firebase credentials configured in the admin panel's Settings tab.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "directory" to mapOf("type" to "string", "description" to "Android project directory, relative to the workspace root."),
                "variant" to mapOf("type" to "string", "enum" to VALID_VARIANTS.toList(), "description" to "Build variant. Defaults to debug."),
            ),
            "required" to listOf("directory"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val credentials = FirebaseCredentialsRegistry.current()
            ?: return ToolResult(
                "Firebase isn't configured yet — set an App ID and CI token in the admin panel's Settings tab first.",
                isError = true,
            )

        val directory = input["directory"] as? String
            ?: return ToolResult("\"directory\" is required.", isError = true)
        val variant = (input["variant"] as? String)?.takeIf { it in VALID_VARIANTS } ?: "debug"

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(directory)
            ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        val projectDir = projectDirPath.toFile()

        if (!File(projectDir, "gradlew").exists()) {
            return ToolResult(
                "No ./gradlew found in $directory — only Gradle/Android projects can be distributed right now (Flutter isn't wired up yet).",
                isError = true,
            )
        }

        onProgress("Building $variant APK")
        val assembleTask = "assemble${variant.replaceFirstChar { it.uppercase() }}"
        val buildResult = GradleTool.execute(workspaceRoot, mapOf("directory" to directory, "task" to assembleTask), onProgress)
        if (buildResult.isError) return buildResult

        val apk = findNewestApk(projectDir, variant)
            ?: return ToolResult("Build succeeded but no APK was found under $directory/*/build/outputs/apk/$variant/.", isError = true)

        val builtApk = copyToBuildsDir(workspaceRoot, projectDir, variant, apk)

        onProgress("Uploading to Firebase App Distribution")
        return uploadToFirebase(builtApk, credentials)
    }

    /** Walks every top-level module directory (not hardcoded "app") looking for the newest APK under build/outputs/apk/$variant/. */
    private fun findNewestApk(projectDir: File, variant: String): File? =
        projectDir.listFiles { file -> file.isDirectory }
            ?.mapNotNull { module -> File(module, "build/outputs/apk/$variant").listFiles { f -> f.extension == "apk" }?.toList() }
            ?.flatten()
            ?.maxByOrNull { it.lastModified() }

    private fun copyToBuildsDir(workspaceRoot: File, projectDir: File, variant: String, apk: File): File {
        val buildsDir = File(workspaceRoot, ".asap-builds").apply { mkdirs() }
        val target = File(buildsDir, "${projectDir.name}-$variant.apk")
        apk.copyTo(target, overwrite = true)
        return target
    }

    private suspend fun uploadToFirebase(apkFile: File, credentials: FirebaseCredentials): ToolResult {
        val command = mutableListOf(
            "firebase", "appdistribution:distribute", apkFile.absolutePath,
            "--app", credentials.appId,
            "--token", credentials.ciToken,
        )
        credentials.testerGroups?.takeIf { it.isNotBlank() }?.let { command += listOf("--groups", it) }
        credentials.releaseNotes?.takeIf { it.isNotBlank() }?.let { command += listOf("--release-notes", it) }

        val (success, output) = ProcessRunner.run(
            command = command,
            workDir = apkFile.parentFile,
            timeoutSeconds = UPLOAD_TIMEOUT_SECONDS,
            maxOutputChars = MAX_OUTPUT_CHARS,
            progressPrefix = "Uploading to Firebase",
        )
        return ToolResult(output, isError = !success)
    }
}
