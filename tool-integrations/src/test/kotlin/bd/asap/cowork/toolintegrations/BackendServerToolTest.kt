package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendServerToolTest {
    private val workspaceRoot = createTempDirectory("backend-server-tool-test").toFile()

    @Test
    fun `stop reports no-op when nothing is running`() = runBlocking {
        BackendServerSession.set(null)
        val result = BackendServerTool.execute(workspaceRoot, mapOf("action" to "stop"))
        assertFalse(result.isError)
        assertEquals("No backend server is running.", result.summary)
    }

    @Test
    fun `start requires a directory`() = runBlocking {
        val result = BackendServerTool.execute(workspaceRoot, mapOf("action" to "start", "stack" to "php"))
        assertTrue(result.isError)
    }

    @Test
    fun `start requires a stack`() = runBlocking {
        val result = BackendServerTool.execute(workspaceRoot, mapOf("action" to "start", "directory" to "app"))
        assertTrue(result.isError)
    }

    @Test
    fun `start rejects an unscaffolded spring-boot project`() = runBlocking {
        val emptyDir = workspaceRoot.resolve("not-scaffolded").apply { mkdirs() }
        val result = BackendServerTool.execute(workspaceRoot, mapOf("action" to "start", "stack" to "spring-boot", "directory" to emptyDir.name))
        assertTrue(result.isError)
    }

    @Test
    fun `unknown action is rejected`() = runBlocking {
        val result = BackendServerTool.execute(workspaceRoot, mapOf("action" to "restart"))
        assertTrue(result.isError)
    }
}
