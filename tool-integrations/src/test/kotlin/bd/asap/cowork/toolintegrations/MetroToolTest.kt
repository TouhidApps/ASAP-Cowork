package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetroToolTest {
    private val workspaceRoot = createTempDirectory("metro-tool-test").toFile()

    @Test
    fun `stop reports no-op when nothing is running`() = runBlocking {
        MetroSession.set(null)
        val result = MetroTool.execute(workspaceRoot, mapOf("action" to "stop"))
        assertFalse(result.isError)
        assertEquals("Metro isn't running.", result.summary)
    }

    @Test
    fun `start requires a directory`() = runBlocking {
        val result = MetroTool.execute(workspaceRoot, mapOf("action" to "start"))
        assertTrue(result.isError)
    }

    @Test
    fun `start rejects a directory without package json`() = runBlocking {
        val emptyDir = workspaceRoot.resolve("not-an-rn-project").apply { mkdirs() }
        val result = MetroTool.execute(workspaceRoot, mapOf("action" to "start", "directory" to emptyDir.name))
        assertTrue(result.isError)
    }

    @Test
    fun `unknown action is rejected`() = runBlocking {
        val result = MetroTool.execute(workspaceRoot, mapOf("action" to "restart"))
        assertTrue(result.isError)
    }
}
