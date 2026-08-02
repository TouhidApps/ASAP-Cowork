package bd.asap.cowork.contextstore

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteRepositoryTest {
    private fun freshRepository(): NoteRepository {
        val dbPath = createTempDirectory("context-store-test").resolve("test.db").toString()
        return NoteRepository(ContextDatabase.connect(dbPath).database)
    }

    @Test
    fun `create then list returns the note`() = runBlocking {
        val repo = freshRepository()

        val created = repo.create("remember to buy milk")

        assertEquals("remember to buy milk", created.content)
        assertEquals(listOf(created), repo.list())
    }

    @Test
    fun `update changes content and bumps updatedAt without touching createdAt`() = runBlocking {
        val repo = freshRepository()
        val note = repo.create("draft")

        val updated = repo.update(note.id, "final")

        assertEquals("final", updated?.content)
        assertEquals(note.createdAt, updated?.createdAt)
        assertTrue((updated?.updatedAt ?: 0) >= note.updatedAt)
    }

    @Test
    fun `update on an unknown id returns null`() = runBlocking {
        val repo = freshRepository()

        assertNull(repo.update("does-not-exist", "content"))
    }

    @Test
    fun `delete removes the note`() = runBlocking {
        val repo = freshRepository()
        val note = repo.create("temporary")

        val deleted = repo.delete(note.id)

        assertTrue(deleted)
        assertEquals(emptyList(), repo.list())
    }
}
