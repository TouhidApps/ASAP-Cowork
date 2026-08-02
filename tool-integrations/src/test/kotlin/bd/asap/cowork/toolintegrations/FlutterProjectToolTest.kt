package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Only exercises validation here — unlike [AndroidProjectToolTest],
 * a real `flutter create` run is a slow, network-dependent external
 * process (dependency resolution, `pub get`) not worth paying for on
 * every test run; it was verified manually against a real Flutter SDK
 * instead (scaffolds a working project, `flutter test` passes against it).
 */
class FlutterProjectToolTest {
    private val workspaceRoot = createTempDirectory("flutter-project-tool-test").toFile()

    @Test
    fun `rejects a project name that is not a valid Dart package name`() = runBlocking {
        val result = FlutterProjectTool.execute(workspaceRoot, mapOf("name" to "Not-A-Valid-Name"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an invalid org identifier`() = runBlocking {
        val result = FlutterProjectTool.execute(workspaceRoot, mapOf("name" to "my_app", "org" to "NotAnOrg"))
        assertTrue(result.isError)
    }
}
