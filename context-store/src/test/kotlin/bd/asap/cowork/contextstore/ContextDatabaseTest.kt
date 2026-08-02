package bd.asap.cowork.contextstore

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class ContextDatabaseTest {
    @Test
    fun `sizeBytes reflects real data written to disk`() = runBlocking {
        val dbPath = createTempDirectory("context-store-test").resolve("test.db").toString()
        val store = ContextDatabase.connect(dbPath)
        val sizeAfterMigrations = store.sizeBytes()
        assertTrue(sizeAfterMigrations > 0, "expected the migrated schema to already take up some space")

        val notes = NoteRepository(store.database)
        repeat(50) { notes.create("note number $it with some padding text to add up bytes") }

        assertTrue(store.sizeBytes() > sizeAfterMigrations, "writing 50 notes should grow the file (or its WAL)")
    }
}
