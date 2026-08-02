package bd.asap.cowork.chatgateway.features.notes

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.contextstore.Note
import bd.asap.cowork.contextstore.NoteRepository

/**
 * A personal scratchpad the user writes to directly — like the chat, but
 * nothing ever replies, and each entry can be edited or deleted
 * afterwards. Unlike the admin panel's features, this isn't gated by
 * ADMIN_TOKEN — it's a primary-user feature, not an operator one.
 */
class NoteService(private val repository: NoteRepository) {
    suspend fun list(): List<Note> = repository.list()

    suspend fun create(content: String): Note {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) throw AppException.BadRequest("Note content must not be empty")
        return repository.create(trimmed)
    }

    suspend fun update(id: String, content: String): Note {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) throw AppException.BadRequest("Note content must not be empty")
        return repository.update(id, trimmed) ?: throw AppException.NotFound("Note not found: $id")
    }

    suspend fun delete(id: String) {
        if (!repository.delete(id)) throw AppException.NotFound("Note not found: $id")
    }
}
