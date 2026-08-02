package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Deterministically scaffolds a minimal single-view SwiftUI iOS project —
 * the iOS counterpart of [AndroidProjectTool]. Rather than hand-writing a
 * `project.pbxproj` (a gnarly, easy-to-get-subtly-wrong format even Apple's
 * own tooling never expects a human to author), this writes a small
 * XcodeGen (`project.yml`) spec plus the Swift sources, then shells out to
 * the real `xcodegen generate` to produce the actual `.xcodeproj` — same
 * "let the real tool own the format" reasoning as [GradleWrapperGenerator]
 * shelling out to `gradle wrapper` instead of hand-writing wrapper jar
 * bytes.
 */
object IosProjectTool {
    const val NAME = "create_ios_project"
    private const val XCODEGEN_TIMEOUT_SECONDS = 120L
    private val FOLDER_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    private val BUNDLE_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    val spec = ToolSpec(
        name = NAME,
        description = "Scaffolds a new single-view SwiftUI iOS project (via XcodeGen) as a subdirectory of the workspace, including the .xcodeproj.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf("type" to "string", "description" to "Project directory name (letters, digits, dash, underscore only)."),
                "bundleId" to mapOf("type" to "string", "description" to "Bundle identifier, e.g. com.example.app. Derived from name if omitted."),
            ),
            "required" to listOf("name"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val name = (input["name"] as? String)?.trim().orEmpty()
        if (!FOLDER_NAME_PATTERN.matches(name)) {
            return ToolResult("\"name\" must contain only letters, digits, dashes, and underscores.", isError = true)
        }

        val bundleId = (input["bundleId"] as? String)?.trim()?.ifBlank { null }
            ?: "com.asap.${name.lowercase().filter { it.isLetterOrDigit() }}"
        if (!BUNDLE_ID_PATTERN.matches(bundleId)) {
            return ToolResult("\"bundleId\" must be a valid reverse-DNS identifier, e.g. com.example.app.", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(name)
            ?: return ToolResult("\"$name\" isn't a valid project directory name.", isError = true)
        val projectDir = projectDirPath.toFile()
        if (projectDir.exists()) {
            return ToolResult("A project named \"$name\" already exists in the workspace.", isError = true)
        }

        val swiftTypeName = swiftTypeName(name)
        writeProjectFiles(workspace, name, bundleId, swiftTypeName)

        val (success, output) = ProcessRunner.run(
            command = listOf("xcodegen", "generate"),
            workDir = projectDir,
            timeoutSeconds = XCODEGEN_TIMEOUT_SECONDS,
            maxOutputChars = 4_000,
            progressPrefix = "Generating Xcode project",
        )
        if (!success) {
            return ToolResult(
                "Wrote the project sources, but \"xcodegen generate\" failed — install it with \"brew install xcodegen\" and run it yourself in $name/, or install and re-run this tool:\n$output",
                isError = true,
            )
        }

        return ToolResult("Created iOS project \"$name\" at ${projectDir.absolutePath} (bundle id $bundleId).\n$output")
    }

    /** A valid Swift type identifier derived from the (possibly dash/underscore-containing) folder name — Swift type names can't contain dashes, and must start with a letter. */
    private fun swiftTypeName(name: String): String {
        val alnum = name.filter { it.isLetterOrDigit() }
        return if (alnum.isNotEmpty() && alnum.first().isLetter()) alnum else "App$alnum"
    }

    private fun writeProjectFiles(workspace: Workspace, name: String, bundleId: String, swiftTypeName: String) {
        workspace.write(
            "$name/project.yml",
            """
            |name: $name
            |options:
            |  bundleIdPrefix: $bundleId
            |targets:
            |  $name:
            |    type: application
            |    platform: iOS
            |    deploymentTarget: "${IosBuildVersions.DEPLOYMENT_TARGET}"
            |    sources:
            |      - $name
            |    settings:
            |      base:
            |        PRODUCT_BUNDLE_IDENTIFIER: $bundleId
            |        SWIFT_VERSION: "${IosBuildVersions.SWIFT_VERSION}"
            |        TARGETED_DEVICE_FAMILY: "1,2"
            |        CODE_SIGN_STYLE: Manual
            |        CODE_SIGNING_REQUIRED: "NO"
            |        CODE_SIGNING_ALLOWED: "NO"
            |        CODE_SIGN_IDENTITY: ""
            |    info:
            |      path: $name/Info.plist
            |      properties:
            |        UILaunchScreen: {}
            |        CFBundleDisplayName: $name
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/.gitignore",
            """
            |.DS_Store
            |/build
            |*.xcodeproj
            |*.xcuserstate
            |xcuserdata/
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/$name/${swiftTypeName}App.swift",
            """
            |import SwiftUI
            |
            |@main
            |struct ${swiftTypeName}App: App {
            |    var body: some Scene {
            |        WindowGroup {
            |            ContentView()
            |        }
            |    }
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/$name/ContentView.swift",
            """
            |import SwiftUI
            |
            |struct ContentView: View {
            |    var body: some View {
            |        Text("Hello, $name!")
            |            .padding()
            |    }
            |}
            |
            |#Preview {
            |    ContentView()
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/$name/Assets.xcassets/Contents.json",
            """
            |{
            |  "info" : {
            |    "author" : "xcode",
            |    "version" : 1
            |  }
            |}
            |
            """.trimMargin(),
        )
    }
}
