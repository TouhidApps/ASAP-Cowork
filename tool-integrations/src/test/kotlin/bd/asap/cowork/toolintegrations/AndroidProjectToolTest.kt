package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidProjectToolTest {
    private val workspaceRoot = createTempDirectory("android-project-tool-test").toFile()

    @Test
    fun `scaffolds a project with the expected files`() = runBlocking {
        val result = AndroidProjectTool.execute(workspaceRoot, mapOf("name" to "MyApp"))

        assertFalse(result.isError, result.summary)
        val projectDir = workspaceRoot.resolve("MyApp")
        assertTrue(projectDir.resolve("settings.gradle.kts").exists())
        assertTrue(projectDir.resolve("app/build.gradle.kts").exists())
        assertTrue(projectDir.resolve("app/src/main/java/com/asap/myapp/MainActivity.kt").exists())
        assertTrue(
            projectDir.resolve("app/src/main/java/com/asap/myapp/MainActivity.kt").readText().contains("Hello, \$name!"),
        )
    }

    @Test
    fun `rejects an invalid project name`() = runBlocking {
        val result = AndroidProjectTool.execute(workspaceRoot, mapOf("name" to "../escape"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects a duplicate project name`() = runBlocking {
        AndroidProjectTool.execute(workspaceRoot, mapOf("name" to "Dup"))
        val result = AndroidProjectTool.execute(workspaceRoot, mapOf("name" to "Dup"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an invalid explicit package name`() = runBlocking {
        val result = AndroidProjectTool.execute(workspaceRoot, mapOf("name" to "Bad", "packageName" to "NotAPackage"))
        assertTrue(result.isError)
    }
}
