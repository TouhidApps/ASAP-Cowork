package bd.asap.cowork.firebase

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FirebaseDistributeToolTest {
    private val workspaceRoot = createTempDirectory("firebase-distribute-tool-test").toFile()

    @AfterTest
    fun tearDown() {
        FirebaseCredentialsRegistry.set(null)
    }

    @Test
    fun `fails when firebase credentials are not configured`() = runBlocking {
        val result = FirebaseDistributeTool.execute(workspaceRoot, mapOf("directory" to "app"))

        assertTrue(result.isError)
        assertTrue(result.summary.contains("Firebase isn't configured"))
    }

    @Test
    fun `fails when the directory has no gradlew`() = runBlocking {
        FirebaseCredentialsRegistry.set(FirebaseCredentials(appId = "1:123:android:abc", ciToken = "token"))

        val result = FirebaseDistributeTool.execute(workspaceRoot, mapOf("directory" to "app"))

        assertTrue(result.isError)
        assertTrue(result.summary.contains("gradlew"))
    }

    @Test
    fun `fails on a directory that escapes the workspace`() = runBlocking {
        FirebaseCredentialsRegistry.set(FirebaseCredentials(appId = "1:123:android:abc", ciToken = "token"))

        val result = FirebaseDistributeTool.execute(workspaceRoot, mapOf("directory" to "../../etc"))

        assertTrue(result.isError)
    }
}
