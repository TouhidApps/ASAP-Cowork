package bd.asap.cowork.contextstore

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {
    private fun freshRepository(): SettingsRepository {
        val dbPath = createTempDirectory("context-store-test").resolve("test.db").toString()
        return SettingsRepository(ContextDatabase.connect(dbPath).database)
    }

    @Test
    fun `unknown key returns null`() = runBlocking {
        val repo = freshRepository()

        assertNull(repo.get("workspace.root"))
    }

    @Test
    fun `set then get returns the value`() = runBlocking {
        val repo = freshRepository()

        repo.set("workspace.root", "/Users/touhid/projects/app")

        assertEquals("/Users/touhid/projects/app", repo.get("workspace.root"))
    }

    @Test
    fun `set on an existing key overwrites the value`() = runBlocking {
        val repo = freshRepository()
        repo.set("llm.currentProvider", "anthropic")

        repo.set("llm.currentProvider", "openai")

        assertEquals("openai", repo.get("llm.currentProvider"))
    }

    @Test
    fun `set with a null value clears the key`() = runBlocking {
        val repo = freshRepository()
        repo.set("firebase.credentials", "{}")

        repo.set("firebase.credentials", null)

        assertNull(repo.get("firebase.credentials"))
    }

    @Test
    fun `clear removes the key`() = runBlocking {
        val repo = freshRepository()
        repo.set("toolchain.paths", "{}")

        repo.clear("toolchain.paths")

        assertNull(repo.get("toolchain.paths"))
    }
}
