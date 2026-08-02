package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Scaffolds a new React Native project via the real `@react-native-community/cli`
 * — same "let the real tool own the format" reasoning as [FlutterProjectTool]:
 * a hand-written template would drift from whatever the CLI's own
 * `android`/`ios` native subprojects actually look like.
 *
 * Runs `init --skip-install` (verified live: the CLI's *own* internal
 * dependency-installation step is flaky in a sandboxed/no-CocoaPods
 * environment and can abort the whole init with no clear error) and then
 * runs `npm install` as its own separate, explicit step — decoupled, so a
 * failure there is reported on its own rather than silently taking the
 * scaffold down with it. Deliberately does not attempt `pod install` for
 * the `ios/` project — CocoaPods is treated as optional here exactly like
 * [XcodeBuildTool]/[IosSimulatorTool] already treat a full Xcode install;
 * the user (or a future tool) runs that separately once CocoaPods is
 * available.
 */
object ReactNativeProjectTool {
    const val NAME = "create_react_native_project"
    private const val INIT_TIMEOUT_SECONDS = 300L
    private const val INSTALL_TIMEOUT_SECONDS = 300L
    private val NAME_PATTERN = Regex("^[A-Z][A-Za-z0-9]*$")
    private val PACKAGE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    val spec = ToolSpec(
        name = NAME,
        description = "Scaffolds a new React Native project (via the real React Native CLI) as a subdirectory of the workspace, including its android/ and ios/ native subprojects, then runs npm install. Does not run CocoaPods' pod install for ios/ — that needs a separate run_terminal_command once CocoaPods is available.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf(
                    "type" to "string",
                    "description" to "Project directory name — must start with an uppercase letter, letters and digits only (it's also used as the native app class name), e.g. \"MyApp\".",
                ),
                "packageName" to mapOf("type" to "string", "description" to "Android package name / iOS bundle ID, e.g. com.example.app. Defaults to com.asap.<name, lowercased>."),
            ),
            "required" to listOf("name"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val name = (input["name"] as? String)?.trim().orEmpty()
        if (!NAME_PATTERN.matches(name)) {
            return ToolResult(
                "\"name\" must start with an uppercase letter and contain only letters and digits, e.g. \"MyApp\".",
                isError = true,
            )
        }

        val packageName = (input["packageName"] as? String)?.trim()?.ifBlank { null }
            ?: "com.asap.${name.lowercase()}"
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) {
            return ToolResult("\"packageName\" must be a valid reverse-DNS identifier, e.g. com.example.app.", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(name)
            ?: return ToolResult("\"$name\" isn't a valid project directory name.", isError = true)
        val projectDir = projectDirPath.toFile()
        if (projectDir.exists()) {
            return ToolResult("A project named \"$name\" already exists in the workspace.", isError = true)
        }

        val (initSuccess, initOutput) = ProcessRunner.run(
            command = listOf(
                "npx", "@react-native-community/cli", "init", name,
                "--package-name", packageName,
                "--pm", "npm",
                "--skip-install",
                "--skip-git-init",
                "--install-pods", "false",
            ),
            workDir = workspaceRoot,
            timeoutSeconds = INIT_TIMEOUT_SECONDS,
            maxOutputChars = 4_000,
            progressPrefix = "Scaffolding React Native project",
            onProgress = onProgress,
        )
        if (!initSuccess) return ToolResult("Failed to scaffold the project:\n$initOutput", isError = true)
        if (!projectDir.exists()) return ToolResult("react-native init reported success but $name wasn't created:\n$initOutput", isError = true)

        val (installSuccess, installOutput) = ProcessRunner.run(
            command = listOf("npm", "install"),
            workDir = projectDir,
            timeoutSeconds = INSTALL_TIMEOUT_SECONDS,
            maxOutputChars = 4_000,
            progressPrefix = "npm install",
            onProgress = onProgress,
        )
        if (!installSuccess) {
            return ToolResult(
                "Created the project, but \"npm install\" failed — run it yourself in $name/:\n$installOutput",
            )
        }

        return ToolResult(
            "Created React Native project \"$name\" at ${projectDir.absolutePath} (package/bundle ID $packageName), " +
                "dependencies installed. iOS still needs \"pod install\" run inside $name/ios (requires CocoaPods) " +
                "before it will build.",
        )
    }
}
