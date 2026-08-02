package bd.asap.cowork.agentsdk

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceTest {
    @Test
    fun `writes a file under the workspace root`() {
        val workspace = Workspace(createTempDirectory("workspace-test"))

        val written = workspace.write("app/src/main/MainActivity.kt", "class MainActivity")

        assertTrue(written.startsWith(workspace.rootPath))
        assertEquals("class MainActivity", written.readText())
    }

    @Test
    fun `rejects a relative path that escapes the workspace root`() {
        val workspace = Workspace(createTempDirectory("workspace-test"))

        assertFailsWith<IllegalArgumentException> {
            workspace.write("../../etc/passwd", "pwned")
        }
    }

    @Test
    fun `rejects an absolute path`() {
        val workspace = Workspace(createTempDirectory("workspace-test"))

        assertFailsWith<IllegalArgumentException> {
            workspace.write("//etc/passwd", "pwned")
        }
    }
}

class ParseGeneratedFilesTest {
    @Test
    fun `parses a single file block`() {
        val reply = """
            Here's your app.
            ===FILE: app/build.gradle.kts===
            plugins { id("com.android.application") }
            ===END FILE===
        """.trimIndent()

        val files = parseGeneratedFiles(reply)

        assertEquals(1, files.size)
        assertEquals("app/build.gradle.kts", files[0].path)
        assertEquals("""plugins { id("com.android.application") }""", files[0].content)
    }

    @Test
    fun `parses multiple file blocks in order`() {
        val reply = """
            ===FILE: a.kt===
            content a
            ===END FILE===
            ===FILE: b.kt===
            content b
            ===END FILE===
        """.trimIndent()

        val files = parseGeneratedFiles(reply)

        assertEquals(listOf("a.kt", "b.kt"), files.map { it.path })
        assertEquals(listOf("content a", "content b"), files.map { it.content })
    }

    @Test
    fun `returns nothing when no file blocks are present`() {
        assertEquals(emptyList(), parseGeneratedFiles("just a plain text reply, no files here"))
    }
}
