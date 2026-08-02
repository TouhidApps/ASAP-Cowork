package bd.asap.cowork.contextstore

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

@Serializable
data class Note(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

object NotesTable : Table("notes") {
    val id = text("id")
    val content = text("content")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * A personal scratchpad the user writes to directly — like the chat, but
 * nothing ever replies, and (unlike chat messages) each entry can be
 * edited or deleted afterwards.
 */
class NoteRepository(private val db: Database) {
    suspend fun list(): List<Note> = newSuspendedTransaction(db = db) {
        NotesTable.selectAll().orderBy(NotesTable.createdAt, SortOrder.ASC).map { it.toNote() }
    }

    suspend fun create(content: String): Note = newSuspendedTransaction(db = db) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        NotesTable.insert {
            it[NotesTable.id] = id
            it[NotesTable.content] = content
            it[createdAt] = now
            it[updatedAt] = now
        }
        Note(id, content, now, now)
    }

    /** Returns null if no note with this id exists, so the route can 404 instead of silently no-op-ing. */
    suspend fun update(id: String, content: String): Note? = newSuspendedTransaction(db = db) {
        val now = System.currentTimeMillis()
        val updated = NotesTable.update({ NotesTable.id eq id }) {
            it[NotesTable.content] = content
            it[updatedAt] = now
        }
        if (updated == 0) return@newSuspendedTransaction null
        NotesTable.selectAll().where { NotesTable.id eq id }.firstOrNull()?.toNote()
    }

    suspend fun delete(id: String): Boolean = newSuspendedTransaction(db = db) {
        NotesTable.deleteWhere { NotesTable.id eq id } > 0
    }

    private fun ResultRow.toNote() = Note(
        id = this[NotesTable.id],
        content = this[NotesTable.content],
        createdAt = this[NotesTable.createdAt],
        updatedAt = this[NotesTable.updatedAt],
    )
}
