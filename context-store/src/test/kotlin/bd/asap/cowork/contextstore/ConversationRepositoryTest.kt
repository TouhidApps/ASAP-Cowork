package bd.asap.cowork.contextstore

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationRepositoryTest {
    private fun freshRepository(): ConversationRepository {
        val dbPath = createTempDirectory("context-store-test").resolve("test.db").toString()
        return ConversationRepository(ContextDatabase.connect(dbPath).database)
    }

    @Test
    fun `new conversation defaults to New chat`() = runBlocking {
        val repo = freshRepository()

        val conversation = repo.createConversation()

        assertEquals("New chat", conversation.title)
    }

    @Test
    fun `first user message titles the conversation, truncated to 60 chars`() = runBlocking {
        val repo = freshRepository()
        val conversation = repo.createConversation()
        val longMessage = "a".repeat(80)

        repo.appendMessage(conversation.id, StoredMessage(role = "user", content = longMessage))

        val updated = repo.listConversations().single { it.id == conversation.id }
        assertEquals("a".repeat(60) + "…", updated.title)
    }

    @Test
    fun `assistant messages never retitle the conversation`() = runBlocking {
        val repo = freshRepository()
        val conversation = repo.createConversation()

        repo.appendMessage(conversation.id, StoredMessage(role = "assistant", content = "a reply with no prior user turn"))

        val updated = repo.listConversations().single { it.id == conversation.id }
        assertEquals("New chat", updated.title)
    }

    @Test
    fun `messages come back in chronological order`() = runBlocking {
        val repo = freshRepository()
        val conversation = repo.createConversation()

        repo.appendMessage(conversation.id, StoredMessage(role = "user", content = "first"))
        repo.appendMessage(conversation.id, StoredMessage(role = "assistant", content = "second"))

        assertEquals(listOf("first", "second"), repo.getMessages(conversation.id).map { it.content })
    }

    @Test
    fun `deleting a conversation cascades to its messages`() = runBlocking {
        val repo = freshRepository()
        val conversation = repo.createConversation()
        repo.appendMessage(conversation.id, StoredMessage(role = "user", content = "hello"))

        repo.deleteConversation(conversation.id)

        assertTrue(repo.listConversations().none { it.id == conversation.id })
        assertEquals(emptyList(), repo.getMessages(conversation.id))
    }

    @Test
    fun `attachments round-trip with the message they belong to`() = runBlocking {
        val repo = freshRepository()
        val conversation = repo.createConversation()
        val attachment = StoredAttachment(url = "/api/v1/chat/uploads/upload-1.png", mimeType = "image/png")

        repo.appendMessage(conversation.id, StoredMessage(role = "user", content = "check this out", attachments = listOf(attachment)))

        val stored = repo.getMessages(conversation.id).single()
        assertEquals(listOf(attachment), stored.attachments)
    }

    @Test
    fun `deleting a conversation cascades to attachments too`() = runBlocking {
        val repo = freshRepository()
        val conversation = repo.createConversation()
        val attachment = StoredAttachment(url = "/api/v1/chat/uploads/upload-1.png", mimeType = "image/png")
        repo.appendMessage(conversation.id, StoredMessage(role = "user", content = "check this out", attachments = listOf(attachment)))

        repo.deleteConversation(conversation.id)

        assertEquals(emptyList(), repo.getMessages(conversation.id))
    }

    @Test
    fun `totalMessageCount counts across conversations`() = runBlocking {
        val repo = freshRepository()
        val a = repo.createConversation()
        val b = repo.createConversation()

        repo.appendMessage(a.id, StoredMessage(role = "user", content = "1"))
        repo.appendMessage(a.id, StoredMessage(role = "assistant", content = "2"))
        repo.appendMessage(b.id, StoredMessage(role = "user", content = "3"))

        assertEquals(3, repo.totalMessageCount())
    }
}
