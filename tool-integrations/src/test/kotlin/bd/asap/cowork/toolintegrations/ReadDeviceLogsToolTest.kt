package bd.asap.cowork.toolintegrations

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Only exercises validation here — actually reading logs needs a real
 * connected device/emulator or booted simulator, neither guaranteed in a
 * test environment. Manually verified live against a real physical
 * Android device: correct output for scope="all", a clean error for
 * scope="app" with nothing launched, and — the bug this tool's current
 * shape exists to avoid — no deadlock reading a 200k+ line logcat buffer
 * (see [ReadDeviceLogsTool]'s doc comment).
 */
class ReadDeviceLogsToolTest {
    @Test
    fun `rejects an unknown platform`() = runBlocking {
        val result = ReadDeviceLogsTool.execute(mapOf("platform" to "windows"))
        assertTrue(result.isError)
    }

    @Test
    fun `missing platform is rejected`() = runBlocking {
        val result = ReadDeviceLogsTool.execute(emptyMap())
        assertTrue(result.isError)
    }
}
