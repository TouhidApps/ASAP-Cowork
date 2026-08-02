package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Only exercises validation and the dependency-free PHP branch here — real
 * scaffolds for spring-boot/node-express/python-fastapi are slow,
 * network-dependent external processes not worth paying for on every test
 * run (same reasoning as [FlutterProjectToolTest]/[ReactNativeProjectToolTest]).
 * All four stacks were verified manually end-to-end: real project
 * generation, a real running dev server, a working CRUD API, and a working
 * admin UI (Spring Data REST's HAL Explorer, AdminJS, SQLAdmin, and the
 * hand-rolled PHP admin page respectively).
 */
class BackendProjectToolTest {
    private val workspaceRoot = createTempDirectory("backend-project-tool-test").toFile()

    @Test
    fun `rejects an invalid project name`() = runBlocking {
        val result = BackendProjectTool.execute(workspaceRoot, mapOf("name" to "not valid", "stack" to "php", "database" to "sqlite"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an unknown stack`() = runBlocking {
        val result = BackendProjectTool.execute(workspaceRoot, mapOf("name" to "MyBackend", "stack" to "rails", "database" to "sqlite"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an unknown database`() = runBlocking {
        val result = BackendProjectTool.execute(workspaceRoot, mapOf("name" to "MyBackend", "stack" to "php", "database" to "oracle"))
        assertTrue(result.isError)
    }

    @Test
    fun `php stack rejects postgres`() = runBlocking {
        val result = BackendProjectTool.execute(workspaceRoot, mapOf("name" to "MyBackend", "stack" to "php", "database" to "postgres"))
        assertTrue(result.isError)
    }

    @Test
    fun `php with sqlite scaffolds a working project with an initialized database`() = runBlocking {
        val result = BackendProjectTool.execute(workspaceRoot, mapOf("name" to "PhpApp", "stack" to "php", "database" to "sqlite"))

        assertTrue(!result.isError, result.summary)
        val projectDir = workspaceRoot.resolve("PhpApp")
        assertTrue(projectDir.resolve("index.html").exists())
        assertTrue(projectDir.resolve("config.php").exists())
        assertTrue(projectDir.resolve("api/items.php").exists())
        assertTrue(projectDir.resolve("admin/index.php").exists())
        assertTrue(projectDir.resolve("data.sqlite").exists())
    }

    @Test
    fun `rejects a duplicate project name`() = runBlocking {
        BackendProjectTool.execute(workspaceRoot, mapOf("name" to "DupBackend", "stack" to "php", "database" to "sqlite"))
        val result = BackendProjectTool.execute(workspaceRoot, mapOf("name" to "DupBackend", "stack" to "php", "database" to "sqlite"))
        assertTrue(result.isError)
    }
}
