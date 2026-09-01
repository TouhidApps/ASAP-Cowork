package bd.asap.cowork.contextstore

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/** An image attached to a chat message — mirrors the response shape of chat-gateway's upload endpoint. */
@Serializable
data class StoredAttachment(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val mimeType: String,
)

@Serializable
data class StoredMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attachments: List<StoredAttachment> = emptyList(),
    /** Which [bd.asap.cowork.agentsdk.Capability] handled this turn — set on assistant messages only, null for user messages and anything stored before this column existed. Lets a later turn's routing look up "what was this conversation just doing" as a fact instead of inferring it from the reply text. */
    val capability: String? = null,
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

object ConversationsTable : Table("conversations") {
    val id = text("id")
    val title = text("title")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ChatMessagesTable : Table("chat_messages") {
    val id = text("id")
    val conversationId = text("conversation_id").references(ConversationsTable.id)
    val role = text("role")
    val content = text("content")
    val createdAt = long("created_at")
    val capability = text("capability").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ChatMessageAttachmentsTable : Table("chat_message_attachments") {
    val id = text("id")
    val messageId = text("message_id").references(ChatMessagesTable.id)
    val url = text("url")
    val mimeType = text("mime_type")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

private const val DEFAULT_TITLE = "New chat"
private const val TITLE_MAX_LENGTH = 60

/**
 * Every conversation and its messages, persisted so a "new chat" (one per
 * WebSocket connection) never loses the previous one — it just starts
 * another row. Each message is written as soon as it exists (user message
 * before the model call, assistant message once the reply finishes), not
 * batched at the end, so a crash mid-reply still leaves the user's message
 * saved.
 */
class ConversationRepository(private val db: Database) {
    suspend fun listConversations(): List<Conversation> = newSuspendedTransaction(db = db) {
        ConversationsTable.selectAll()
            .orderBy(ConversationsTable.updatedAt, SortOrder.DESC)
            .map { it.toConversation() }
    }

    suspend fun findConversation(id: String): Conversation? = newSuspendedTransaction(db = db) {
        ConversationsTable.selectAll().where { ConversationsTable.id eq id }.firstOrNull()?.toConversation()
    }

    suspend fun createConversation(): Conversation = newSuspendedTransaction(db = db) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        ConversationsTable.insert {
            it[ConversationsTable.id] = id
            it[title] = DEFAULT_TITLE
            it[createdAt] = now
            it[updatedAt] = now
        }
        Conversation(id, DEFAULT_TITLE, now, now)
    }

    suspend fun deleteConversation(id: String): Unit = newSuspendedTransaction(db = db) {
        ConversationsTable.deleteWhere { ConversationsTable.id eq id }
    }

    suspend fun getMessages(conversationId: String): List<StoredMessage> = newSuspendedTransaction(db = db) {
        val messages = ChatMessagesTable.selectAll()
            .where { ChatMessagesTable.conversationId eq conversationId }
            .orderBy(ChatMessagesTable.createdAt, SortOrder.ASC)
            .map { it.toStoredMessage() }

        val messageIds = messages.map { it.id }
        if (messageIds.isEmpty()) return@newSuspendedTransaction messages

        val attachmentsByMessageId = ChatMessageAttachmentsTable.selectAll()
            .where { ChatMessageAttachmentsTable.messageId inList messageIds }
            .map { it[ChatMessageAttachmentsTable.messageId] to it.toStoredAttachment() }
            .groupBy({ it.first }, { it.second })

        messages.map { message -> message.copy(attachments = attachmentsByMessageId[message.id] ?: emptyList()) }
    }

    suspend fun totalMessageCount(): Int = newSuspendedTransaction(db = db) {
        ChatMessagesTable.selectAll().count().toInt()
    }

    /** Appends the message (plus any attachments), bumps the conversation's updatedAt, and titles it from the first user message. */
    suspend fun appendMessage(conversationId: String, message: StoredMessage): Unit = newSuspendedTransaction(db = db) {
        ChatMessagesTable.insert {
            it[id] = message.id
            it[ChatMessagesTable.conversationId] = conversationId
            it[role] = message.role
            it[content] = message.content
            it[createdAt] = message.createdAt
            it[capability] = message.capability
        }

        message.attachments.forEach { attachment ->
            ChatMessageAttachmentsTable.insert {
                it[id] = attachment.id
                it[messageId] = message.id
                it[url] = attachment.url
                it[mimeType] = attachment.mimeType
                it[createdAt] = message.createdAt
            }
        }

        val current = ConversationsTable.selectAll().where { ConversationsTable.id eq conversationId }.firstOrNull()
        val needsTitle = message.role == "user" && current?.get(ConversationsTable.title) == DEFAULT_TITLE

        ConversationsTable.update({ ConversationsTable.id eq conversationId }) {
            it[updatedAt] = message.createdAt
            if (needsTitle) {
                it[title] = message.content.take(TITLE_MAX_LENGTH).let { text ->
                    if (message.content.length > TITLE_MAX_LENGTH) "$text…" else text
                }
            }
        }
    }

    private fun ResultRow.toConversation() = Conversation(
        id = this[ConversationsTable.id],
        title = this[ConversationsTable.title],
        createdAt = this[ConversationsTable.createdAt],
        updatedAt = this[ConversationsTable.updatedAt],
    )

    private fun ResultRow.toStoredMessage() = StoredMessage(
        id = this[ChatMessagesTable.id],
        role = this[ChatMessagesTable.role],
        content = this[ChatMessagesTable.content],
        createdAt = this[ChatMessagesTable.createdAt],
        capability = this[ChatMessagesTable.capability],
    )

    private fun ResultRow.toStoredAttachment() = StoredAttachment(
        id = this[ChatMessageAttachmentsTable.id],
        url = this[ChatMessageAttachmentsTable.url],
        mimeType = this[ChatMessageAttachmentsTable.mimeType],
    )
}
