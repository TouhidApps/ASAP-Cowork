package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpProjectToolTest {
    private val workspaceRoot = createTempDirectory("kmp-project-tool-test").toFile()

    @Test
    fun `scaffolds a project with shared, androidApp, and iosApp, plus a real gradlew`() = runBlocking {
        val result = KmpProjectTool.execute(workspaceRoot, mapOf("name" to "MyKmp"))

        // Never a hard failure even if xcodegen isn't installed on this
        // machine — see KmpProjectTool's own reasoning at its `isError`
        // call site. The summary still records what happened either way.
        assertFalse(result.isError, result.summary)
        val projectDir = workspaceRoot.resolve("MyKmp")
        assertTrue(projectDir.resolve("settings.gradle.kts").exists())
        assertTrue(projectDir.resolve("shared/build.gradle.kts").exists())
        assertTrue(projectDir.resolve("shared/src/commonMain/kotlin/com/asap/mykmp/shared/Platform.kt").exists())
        assertTrue(projectDir.resolve("shared/src/commonMain/kotlin/com/asap/mykmp/shared/App.kt").exists())
        assertTrue(projectDir.resolve("shared/src/androidMain/kotlin/com/asap/mykmp/shared/Platform.android.kt").exists())
        assertTrue(projectDir.resolve("shared/src/iosMain/kotlin/com/asap/mykmp/shared/Platform.ios.kt").exists())
        assertTrue(projectDir.resolve("shared/src/iosMain/kotlin/com/asap/mykmp/shared/MainViewController.kt").exists())
        assertTrue(projectDir.resolve("androidApp/build.gradle.kts").exists())
        assertTrue(projectDir.resolve("androidApp/src/main/java/com/asap/mykmp/MainActivity.kt").exists())
        assertTrue(projectDir.resolve("iosApp/project.yml").exists())
        assertTrue(projectDir.resolve("iosApp/iosApp/MyKmpApp.swift").exists())
        assertTrue(projectDir.resolve("iosApp/iosApp/ContentView.swift").exists())
        // GradleWrapperGenerator's own file-copy based generation (no longer
        // shelling to a global `gradle` binary) — deterministic and fast, so
        // worth actually asserting on here, unlike a real `flutter create`.
        assertTrue(projectDir.resolve("gradlew").canExecute())
        assertTrue(projectDir.resolve("gradle/wrapper/gradle-wrapper.jar").exists())
    }

    @Test
    fun `settings uses PREFER_SETTINGS, not FAIL_ON_PROJECT_REPOS`() = runBlocking {
        // Regression guard for a real bug found via live testing: Kotlin
        // Multiplatform's own ivy repository (for the Kotlin/Native compiler
        // toolchain) gets rejected outright under FAIL_ON_PROJECT_REPOS.
        KmpProjectTool.execute(workspaceRoot, mapOf("name" to "RepoModeCheck"))
        val settingsContent = workspaceRoot.resolve("RepoModeCheck/settings.gradle.kts").readText()
        assertTrue(settingsContent.contains("repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)"))
    }

    @Test
    fun `rejects an invalid project name`() = runBlocking {
        val result = KmpProjectTool.execute(workspaceRoot, mapOf("name" to "../escape"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects a duplicate project name`() = runBlocking {
        KmpProjectTool.execute(workspaceRoot, mapOf("name" to "Dup"))
        val result = KmpProjectTool.execute(workspaceRoot, mapOf("name" to "Dup"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an invalid explicit package name`() = runBlocking {
        val result = KmpProjectTool.execute(workspaceRoot, mapOf("name" to "Bad", "packageName" to "NotAPackage"))
        assertTrue(result.isError)
    }
}
