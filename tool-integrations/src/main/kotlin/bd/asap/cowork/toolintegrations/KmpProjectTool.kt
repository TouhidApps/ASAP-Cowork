package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Scaffolds a real Kotlin Multiplatform + Compose Multiplatform project: a
 * `shared` module (commonMain/androidMain/iosMain via Kotlin's default
 * hierarchy template — no manual source-set wiring needed since Kotlin
 * 1.9.20) with an actual shared `@Composable App()` UI (not just shared
 * business logic), an `androidApp` module that renders it directly, and an
 * `iosApp` Xcode project (via XcodeGen, same "let the real tool own the
 * project format" reasoning as [IosProjectTool]) whose `ContentView` embeds
 * that same shared UI through `ComposeUIViewController`. Then, exactly like
 * [AndroidProjectTool], uses [GradleWrapperGenerator] for a real
 * project-local `gradlew`. There's no single official "create KMP project"
 * CLI the way `flutter create` exists for Flutter, so this hand-rolls the
 * template, sharing [AndroidBuildVersions] with [AndroidProjectTool] so the
 * two don't drift apart.
 *
 * The iOS side is wired via the standard `embedAndSignAppleFrameworkForXcode`
 * Gradle task (auto-registered once `binaries.framework {}` is declared on
 * the iOS targets below) invoked from an Xcode Run Script build phase — the
 * same recipe the official Kotlin Multiplatform iOS integration guide
 * documents, not a hand-rolled substitute.
 */
object KmpProjectTool {
    const val NAME = "create_kmp_project"
    private const val XCODEGEN_TIMEOUT_SECONDS = 120L
    private val FOLDER_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    private val PACKAGE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    val spec = ToolSpec(
        name = NAME,
        description = "Scaffolds a new Kotlin Multiplatform + Compose Multiplatform project as a subdirectory of the workspace: a `shared` module with a real shared @Composable UI (not just shared logic), an `androidApp` module that renders it, and an `iosApp` Xcode project (via XcodeGen, .xcodeproj included) that renders the exact same shared UI via ComposeUIViewController — plus the Gradle wrapper. Both the Android and iOS apps are immediately runnable.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf("type" to "string", "description" to "Project directory name (letters, digits, dash, underscore only)."),
                "packageName" to mapOf("type" to "string", "description" to "Application package name, e.g. com.example.app. Derived from name if omitted."),
            ),
            "required" to listOf("name"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val name = (input["name"] as? String)?.trim().orEmpty()
        if (!FOLDER_NAME_PATTERN.matches(name)) {
            return ToolResult("\"name\" must contain only letters, digits, dashes, and underscores.", isError = true)
        }

        val packageName = (input["packageName"] as? String)?.trim()?.ifBlank { null }
            ?: "com.asap.${name.lowercase().filter { it.isLetterOrDigit() }}"
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) {
            return ToolResult("\"packageName\" must be a valid Java-style package name, e.g. com.example.app.", isError = true)
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(name)
            ?: return ToolResult("\"$name\" isn't a valid project directory name.", isError = true)
        val projectDir = projectDirPath.toFile()
        if (projectDir.exists()) {
            return ToolResult("A project named \"$name\" already exists in the workspace.", isError = true)
        }

        writeGradleProjectFiles(workspace, name, packageName)
        val wrapperMessage = GradleWrapperGenerator.generate(projectDir)

        val swiftTypeName = swiftTypeName(name)
        writeIosAppFiles(workspace, name, packageName, swiftTypeName)
        val iosAppDir = File(projectDir, "iosApp")
        val (xcodegenOk, xcodegenOutput) = ProcessRunner.run(
            command = listOf("xcodegen", "generate"),
            workDir = iosAppDir,
            timeoutSeconds = XCODEGEN_TIMEOUT_SECONDS,
            maxOutputChars = 4_000,
            progressPrefix = "Generating Xcode project",
        )
        // xcodegen not being installed (e.g. this agent running on Linux/Windows,
        // or a Mac without it) doesn't make project creation itself a failure —
        // the shared/androidApp modules are already a complete, buildable-anywhere
        // KMP project on their own; iosApp is the one part of it that needs a Mac.
        val iosNote = if (xcodegenOk) {
            "iosApp/ is a real Xcode project (run_xcodebuild, directory \"$name/iosApp\", scheme \"iosApp\") — its first build runs a Gradle script phase to embed the shared framework, so it can take a while the first time."
        } else {
            "Wrote iosApp/ sources, but \"xcodegen generate\" failed — install it with \"brew install xcodegen\" and run it yourself in $name/iosApp/, or install and re-run this tool:\n$xcodegenOutput"
        }

        return ToolResult(
            "Created KMP project \"$name\" at ${projectDir.absolutePath} (package $packageName, modules \"shared\" + \"androidApp\" + \"iosApp\").\n$wrapperMessage\n$iosNote",
        )
    }

    /** A valid Swift type identifier derived from the (possibly dash/underscore-containing) folder name — Swift type names can't contain dashes, and must start with a letter. Same rule as [IosProjectTool]. */
    private fun swiftTypeName(name: String): String {
        val alnum = name.filter { it.isLetterOrDigit() }
        return if (alnum.isNotEmpty() && alnum.first().isLetter()) alnum else "App$alnum"
    }

    private fun writeGradleProjectFiles(workspace: Workspace, name: String, packageName: String) {
        val sharedPackage = "$packageName.shared"
        val sharedPackagePath = sharedPackage.replace('.', '/')
        val appPackagePath = packageName.replace('.', '/')

        workspace.write(
            "$name/settings.gradle.kts",
            """
            |pluginManagement {
            |    repositories {
            |        google()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |dependencyResolutionManagement {
            |    // PREFER_SETTINGS, not FAIL_ON_PROJECT_REPOS — the Kotlin
            |    // Multiplatform plugin adds its own ivy repository for the
            |    // Kotlin/Native compiler toolchain once iOS targets are
            |    // declared, and FAIL_ON_PROJECT_REPOS forbids that outright.
            |    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
            |    repositories {
            |        google()
            |        mavenCentral()
            |    }
            |}
            |rootProject.name = "$name"
            |include(":shared")
            |include(":androidApp")
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/build.gradle.kts",
            """
            |plugins {
            |    id("com.android.application") version "${AndroidBuildVersions.AGP_VERSION}" apply false
            |    id("com.android.library") version "${AndroidBuildVersions.AGP_VERSION}" apply false
            |    id("org.jetbrains.kotlin.android") version "${AndroidBuildVersions.KOTLIN_VERSION}" apply false
            |    id("org.jetbrains.kotlin.multiplatform") version "${AndroidBuildVersions.KOTLIN_VERSION}" apply false
            |    id("org.jetbrains.kotlin.plugin.compose") version "${AndroidBuildVersions.KOTLIN_VERSION}" apply false
            |    id("org.jetbrains.compose") version "${AndroidBuildVersions.COMPOSE_MULTIPLATFORM_VERSION}" apply false
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/gradle.properties",
            """
            |org.gradle.jvmargs=-Xmx2048m
            |android.useAndroidX=true
            |android.nonTransitiveRClass=true
            |kotlin.code.style=official
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/.gitignore",
            """
            |*.iml
            |.gradle
            |/local.properties
            |/.idea
            |.DS_Store
            |/build
            |/*/build
            |/captures
            |.externalNativeBuild
            |.cxx
            |iosApp/*.xcodeproj
            |iosApp/*.xcuserstate
            |iosApp/xcuserdata/
            |
            """.trimMargin(),
        )

        writeSharedModule(workspace, name, sharedPackage, sharedPackagePath)
        writeAndroidAppModule(workspace, name, packageName, appPackagePath, sharedPackage)
    }

    private fun writeSharedModule(workspace: Workspace, name: String, sharedPackage: String, sharedPackagePath: String) {
        workspace.write(
            "$name/shared/build.gradle.kts",
            """
            |plugins {
            |    id("org.jetbrains.kotlin.multiplatform")
            |    id("org.jetbrains.kotlin.plugin.compose")
            |    id("org.jetbrains.compose")
            |    id("com.android.library")
            |}
            |
            |kotlin {
            |    androidTarget {
            |        // Without this, the Android target's Kotlin compilation
            |        // defaults to whatever JDK is running Gradle (21 here),
            |        // while javac stays pinned at 17 below — Gradle rejects
            |        // that combination outright.
            |        compilations.all {
            |            kotlinOptions {
            |                jvmTarget = "17"
            |            }
            |        }
            |    }
            |
            |    listOf(
            |        iosArm64(),
            |        iosSimulatorArm64(),
            |    ).forEach { iosTarget ->
            |        iosTarget.binaries.framework {
            |            baseName = "Shared"
            |            isStatic = true
            |        }
            |    }
            |
            |    // commonMain/androidMain/iosMain and their dependsOn wiring are
            |    // all created automatically by Kotlin's default hierarchy
            |    // template (since 1.9.20) from the targets declared above — no
            |    // manual "val iosMain by creating { dependsOn(commonMain) }"
            |    // needed anymore.
            |    sourceSets {
            |        commonMain.dependencies {
            |            implementation(compose.runtime)
            |            implementation(compose.foundation)
            |            implementation(compose.material3)
            |            implementation(compose.ui)
            |        }
            |    }
            |}
            |
            |android {
            |    namespace = "$sharedPackage"
            |    compileSdk = 34
            |    defaultConfig {
            |        minSdk = 24
            |    }
            |    compileOptions {
            |        sourceCompatibility = JavaVersion.VERSION_17
            |        targetCompatibility = JavaVersion.VERSION_17
            |    }
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/shared/src/commonMain/kotlin/$sharedPackagePath/Platform.kt",
            """
            |package $sharedPackage
            |
            |expect fun platformName(): String
            |
            |fun greet(): String = "Hello from Kotlin Multiplatform, running on " + platformName() + "!"
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/shared/src/commonMain/kotlin/$sharedPackagePath/App.kt",
            """
            |package $sharedPackage
            |
            |import androidx.compose.foundation.layout.Box
            |import androidx.compose.foundation.layout.fillMaxSize
            |import androidx.compose.material3.MaterialTheme
            |import androidx.compose.material3.Surface
            |import androidx.compose.material3.Text
            |import androidx.compose.runtime.Composable
            |import androidx.compose.ui.Alignment
            |import androidx.compose.ui.Modifier
            |
            |/**
            | * The one shared UI entry point both androidApp's MainActivity and
            | * iosApp's ContentView (via iosMain's MainViewController /
            | * ComposeUIViewController) render — this is the actual shared UI, not
            | * just shared logic underneath separately-written platform UIs.
            | */
            |@Composable
            |fun App() {
            |    MaterialTheme {
            |        Surface(modifier = Modifier.fillMaxSize()) {
            |            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            |                Text(greet())
            |            }
            |        }
            |    }
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/shared/src/androidMain/kotlin/$sharedPackagePath/Platform.android.kt",
            """
            |package $sharedPackage
            |
            |actual fun platformName(): String = "Android"
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/shared/src/iosMain/kotlin/$sharedPackagePath/Platform.ios.kt",
            """
            |package $sharedPackage
            |
            |actual fun platformName(): String = "iOS"
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/shared/src/iosMain/kotlin/$sharedPackagePath/MainViewController.kt",
            """
            |package $sharedPackage
            |
            |import androidx.compose.ui.window.ComposeUIViewController
            |import platform.UIKit.UIViewController
            |
            |/** Entry point iosApp's ContentView.swift calls into — wraps the exact same [App] Android renders in a UIViewController Compose Multiplatform can host inside SwiftUI. */
            |fun MainViewController(): UIViewController = ComposeUIViewController { App() }
            |
            """.trimMargin(),
        )
    }

    private fun writeAndroidAppModule(
        workspace: Workspace,
        name: String,
        packageName: String,
        appPackagePath: String,
        sharedPackage: String,
    ) {
        workspace.write(
            "$name/androidApp/build.gradle.kts",
            """
            |plugins {
            |    id("com.android.application")
            |    id("org.jetbrains.kotlin.android")
            |    id("org.jetbrains.kotlin.plugin.compose")
            |}
            |
            |android {
            |    namespace = "$packageName"
            |    compileSdk = 34
            |
            |    defaultConfig {
            |        applicationId = "$packageName"
            |        minSdk = 24
            |        targetSdk = 34
            |        versionCode = 1
            |        versionName = "1.0"
            |    }
            |
            |    buildTypes {
            |        release {
            |            isMinifyEnabled = false
            |        }
            |    }
            |
            |    compileOptions {
            |        sourceCompatibility = JavaVersion.VERSION_17
            |        targetCompatibility = JavaVersion.VERSION_17
            |    }
            |
            |    kotlinOptions {
            |        jvmTarget = "17"
            |    }
            |
            |    buildFeatures {
            |        compose = true
            |    }
            |}
            |
            |dependencies {
            |    // ui/material3/etc. all come transitively via :shared's Compose
            |    // Multiplatform dependencies — redeclaring them here (e.g. from a
            |    // separate androidx compose-bom) risks pulling a second, possibly
            |    // conflicting version of the same artifacts.
            |    implementation(project(":shared"))
            |    implementation("androidx.core:core-ktx:1.13.1")
            |    implementation("androidx.activity:activity-compose:1.9.0")
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/androidApp/src/main/AndroidManifest.xml",
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |    <application
            |        android:allowBackup="true"
            |        android:label="@string/app_name"
            |        android:theme="@style/Theme.App">
            |        <activity
            |            android:name=".MainActivity"
            |            android:exported="true">
            |            <intent-filter>
            |                <action android:name="android.intent.action.MAIN" />
            |                <category android:name="android.intent.category.LAUNCHER" />
            |            </intent-filter>
            |        </activity>
            |    </application>
            |</manifest>
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/androidApp/src/main/java/$appPackagePath/MainActivity.kt",
            """
            |package $packageName
            |
            |import android.os.Bundle
            |import androidx.activity.ComponentActivity
            |import androidx.activity.compose.setContent
            |import $sharedPackage.App
            |
            |class MainActivity : ComponentActivity() {
            |    override fun onCreate(savedInstanceState: Bundle?) {
            |        super.onCreate(savedInstanceState)
            |        setContent {
            |            App()
            |        }
            |    }
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/androidApp/src/main/res/values/strings.xml",
            """
            |<resources>
            |    <string name="app_name">$name</string>
            |</resources>
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/androidApp/src/main/res/values/themes.xml",
            """
            |<resources>
            |    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" />
            |</resources>
            |
            """.trimMargin(),
        )
    }

    /**
     * `iosApp` is deliberately not a Gradle module (no `include(":iosApp")`)
     * — it's a plain Xcode project living alongside `shared`/`androidApp`,
     * generated via XcodeGen exactly like [IosProjectTool], whose one Run
     * Script build phase shells back out to this same project's own
     * `./gradlew` to build and embed the `shared` framework before Swift
     * compiles — see `embedAndSignAppleFrameworkForXcode` in project.yml.
     */
    private fun writeIosAppFiles(workspace: Workspace, name: String, packageName: String, swiftTypeName: String) {
        workspace.write(
            "$name/iosApp/project.yml",
            """
            |name: iosApp
            |options:
            |  bundleIdPrefix: $packageName
            |targets:
            |  iosApp:
            |    type: application
            |    platform: iOS
            |    deploymentTarget: "${IosBuildVersions.DEPLOYMENT_TARGET}"
            |    sources:
            |      - iosApp
            |    settings:
            |      base:
            |        PRODUCT_BUNDLE_IDENTIFIER: $packageName.iosApp
            |        SWIFT_VERSION: "${IosBuildVersions.SWIFT_VERSION}"
            |        TARGETED_DEVICE_FAMILY: "1,2"
            |        CODE_SIGN_STYLE: Manual
            |        CODE_SIGNING_REQUIRED: "NO"
            |        CODE_SIGNING_ALLOWED: "NO"
            |        CODE_SIGN_IDENTITY: ""
            |    info:
            |      path: iosApp/Info.plist
            |      properties:
            |        UILaunchScreen: {}
            |        CFBundleDisplayName: $name
            |        # Compose Multiplatform refuses to boot without this — it
            |        # asserts on it at runtime (not just build time) and crashes
            |        # immediately on launch otherwise, since without it iPhones
            |        # with a high refresh rate silently get capped to 60Hz.
            |        CADisableMinimumFrameDurationOnPhone: true
            |    preBuildScripts:
            |      - name: Compile Kotlin Framework
            |        script: |
            |                cd "${'$'}SRCROOT/.."
            |                ./gradlew :shared:embedAndSignAppleFrameworkForXcode --no-daemon
            |        basedOnDependencyAnalysis: false
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/iosApp/iosApp/${swiftTypeName}App.swift",
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
            "$name/iosApp/iosApp/ContentView.swift",
            """
            |import SwiftUI
            |import Shared
            |
            |/** Hosts the exact same @Composable App() androidApp renders, via the shared module's ComposeUIViewController-based MainViewController(). */
            |struct ComposeView: UIViewControllerRepresentable {
            |    func makeUIViewController(context: Context) -> UIViewController {
            |        MainViewControllerKt.MainViewController()
            |    }
            |
            |    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
            |}
            |
            |struct ContentView: View {
            |    var body: some View {
            |        ComposeView()
            |            .ignoresSafeArea(.keyboard)
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
            "$name/iosApp/iosApp/Assets.xcassets/Contents.json",
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

        // Xcode's asset catalog compiler hard-fails the build if no set
        // named "AppIcon" exists at all (not just a warning for a missing
        // image within one) — an empty slot is enough to satisfy it.
        workspace.write(
            "$name/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json",
            """
            |{
            |  "images" : [
            |    {
            |      "idiom" : "universal",
            |      "platform" : "ios",
            |      "size" : "1024x1024"
            |    }
            |  ],
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
