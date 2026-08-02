package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/** Deterministically scaffolds a minimal single-Activity Jetpack Compose Android project — faster and more reliable than asking the model to hand-write Gradle boilerplate every time. */
object AndroidProjectTool {
    const val NAME = "create_android_project"
    private val FOLDER_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    private val PACKAGE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    val spec = ToolSpec(
        name = NAME,
        description = "Scaffolds a new single-Activity Jetpack Compose Android project (Gradle Kotlin DSL) as a subdirectory of the workspace, including the Gradle wrapper.",
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

        writeProjectFiles(workspace, name, packageName)
        val wrapperMessage = GradleWrapperGenerator.generate(projectDir)

        return ToolResult("Created Android project \"$name\" at ${projectDir.absolutePath} (package $packageName).\n$wrapperMessage")
    }

    private fun writeProjectFiles(workspace: Workspace, name: String, packageName: String) {
        val packagePath = packageName.replace('.', '/')

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
            |    repositories {
            |        google()
            |        mavenCentral()
            |    }
            |}
            |rootProject.name = "$name"
            |include(":app")
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/build.gradle.kts",
            """
            |plugins {
            |    id("com.android.application") version "${AndroidBuildVersions.AGP_VERSION}" apply false
            |    id("org.jetbrains.kotlin.android") version "${AndroidBuildVersions.KOTLIN_VERSION}" apply false
            |    id("org.jetbrains.kotlin.plugin.compose") version "${AndroidBuildVersions.KOTLIN_VERSION}" apply false
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/gradle.properties",
            """
            |org.gradle.jvmargs=-Xmx2048m
            |android.useAndroidX=true
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
            |/app/build
            |/captures
            |.externalNativeBuild
            |.cxx
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/app/build.gradle.kts",
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
            |    implementation(platform("androidx.compose:compose-bom:${AndroidBuildVersions.COMPOSE_BOM_VERSION}"))
            |    implementation("androidx.compose.ui:ui")
            |    implementation("androidx.compose.ui:ui-graphics")
            |    implementation("androidx.compose.material3:material3")
            |    implementation("androidx.activity:activity-compose:1.9.0")
            |    implementation("androidx.core:core-ktx:1.13.1")
            |    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/app/src/main/AndroidManifest.xml",
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |    <application
            |        android:label="$name"
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
            "$name/app/src/main/java/$packagePath/MainActivity.kt",
            """
            |package $packageName
            |
            |import android.os.Bundle
            |import androidx.activity.ComponentActivity
            |import androidx.activity.compose.setContent
            |import androidx.compose.foundation.layout.fillMaxSize
            |import androidx.compose.material3.MaterialTheme
            |import androidx.compose.material3.Surface
            |import androidx.compose.material3.Text
            |import androidx.compose.runtime.Composable
            |import androidx.compose.ui.Modifier
            |
            |class MainActivity : ComponentActivity() {
            |    override fun onCreate(savedInstanceState: Bundle?) {
            |        super.onCreate(savedInstanceState)
            |        setContent {
            |            MaterialTheme {
            |                Surface(modifier = Modifier.fillMaxSize()) {
            |                    Greeting("$name")
            |                }
            |            }
            |        }
            |    }
            |}
            |
            |@Composable
            |fun Greeting(name: String) {
            |    Text(text = "Hello, ${'$'}name!")
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/app/src/main/res/values/strings.xml",
            """
            |<resources>
            |    <string name="app_name">$name</string>
            |</resources>
            |
            """.trimMargin(),
        )

        workspace.write(
            "$name/app/src/main/res/values/themes.xml",
            """
            |<resources>
            |    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" />
            |</resources>
            |
            """.trimMargin(),
        )
    }
}
