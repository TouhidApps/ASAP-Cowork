package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Only exercises validation here — a real `init` + `npm install` run is a
 * slow, network-dependent external process not worth paying for on every
 * test run (same reasoning as [FlutterProjectToolTest]); it was verified
 * manually against the real React Native CLI installed on this machine
 * (scaffolds a working project, npm install succeeds, and the generated
 * android/ project builds with a real `./gradlew assembleDebug`).
 */
class ReactNativeProjectToolTest {
    private val workspaceRoot = createTempDirectory("react-native-project-tool-test").toFile()

    @Test
    fun `rejects a project name that does not start with an uppercase letter`() = runBlocking {
        val result = ReactNativeProjectTool.execute(workspaceRoot, mapOf("name" to "myApp"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects a project name containing dashes or underscores`() = runBlocking {
        val result = ReactNativeProjectTool.execute(workspaceRoot, mapOf("name" to "My-App"))
        assertTrue(result.isError)
    }

    @Test
    fun `rejects an invalid explicit package name`() = runBlocking {
        val result = ReactNativeProjectTool.execute(workspaceRoot, mapOf("name" to "MyApp", "packageName" to "NotAPackage"))
        assertTrue(result.isError)
    }
}
