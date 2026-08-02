package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessRunnerTest {
    private val workDir = createTempDirectory("process-runner-test").toFile()

    @Test
    fun `succeeds and captures output for a normal command`() = runBlocking {
        val (success, output) = ProcessRunner.run(
            command = listOf("sh", "-c", "echo hello"),
            workDir = workDir,
            timeoutSeconds = 5,
            maxOutputChars = 1000,
            progressPrefix = "test",
        )

        assertTrue(success)
        assertTrue(output.contains("Exit code: 0"))
        assertTrue(output.contains("hello"))
    }

    @Test
    fun `reports failure for a non-zero exit code`() = runBlocking {
        val (success, output) = ProcessRunner.run(
            command = listOf("sh", "-c", "exit 3"),
            workDir = workDir,
            timeoutSeconds = 5,
            maxOutputChars = 1000,
            progressPrefix = "test",
        )

        assertFalse(success)
        assertTrue(output.contains("Exit code: 3"))
    }

    @Test
    fun `kills the process and reports a timeout`() = runBlocking {
        val (success, output) = ProcessRunner.run(
            command = listOf("sh", "-c", "sleep 5"),
            workDir = workDir,
            timeoutSeconds = 1,
            maxOutputChars = 1000,
            progressPrefix = "test",
        )

        assertFalse(success)
        assertTrue(output.contains("Timed out"))
    }

    @Test
    fun `truncates output from the tail`() = runBlocking {
        val (_, output) = ProcessRunner.run(
            command = listOf("sh", "-c", "for i in \$(seq 1 200); do echo line-\$i; done"),
            workDir = workDir,
            timeoutSeconds = 5,
            maxOutputChars = 50,
            progressPrefix = "test",
        )

        assertTrue(output.contains("output truncated"))
        assertTrue(output.contains("line-200"))
        assertFalse(output.contains("line-1\n"))
    }
}
