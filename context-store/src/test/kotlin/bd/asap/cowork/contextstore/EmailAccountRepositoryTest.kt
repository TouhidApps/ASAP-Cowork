package bd.asap.cowork.contextstore

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailAccountRepositoryTest {
    private fun freshRepository(): EmailAccountRepository {
        val dbPath = createTempDirectory("context-store-test").resolve("test.db").toString()
        return EmailAccountRepository(ContextDatabase.connect(dbPath).database)
    }

    private val sampleTokens = EmailTokens("access-1", "refresh-1", 1_000L)

    @Test
    fun `first connected account becomes the default`() = runBlocking {
        val repo = freshRepository()

        val account = repo.upsertConnected("gmail", "jane@example.com", sampleTokens)

        assertTrue(account.isDefault)
    }

    @Test
    fun `second connected account is not default`() = runBlocking {
        val repo = freshRepository()
        repo.upsertConnected("gmail", "jane@example.com", sampleTokens)

        val second = repo.upsertConnected("gmail", "work@example.com", sampleTokens)

        assertEquals(false, second.isDefault)
    }

    @Test
    fun `reconnecting an already-connected account refreshes tokens without resetting its cursor`() = runBlocking {
        val repo = freshRepository()
        val first = repo.upsertConnected("gmail", "jane@example.com", sampleTokens)
        repo.updateCursor(first.id, "msg-1", 1000L)

        repo.upsertConnected("gmail", "jane@example.com", EmailTokens("access-2", "refresh-2", 2_000L))

        val updated = repo.find(first.id)
        assertEquals("msg-1", updated?.lastSeenMessageId)
        assertEquals(EmailTokens("access-2", "refresh-2", 2_000L), repo.getTokens(first.id))
    }

    @Test
    fun `setDefault moves the default flag`() = runBlocking {
        val repo = freshRepository()
        val first = repo.upsertConnected("gmail", "jane@example.com", sampleTokens)
        val second = repo.upsertConnected("gmail", "work@example.com", sampleTokens)

        repo.setDefault(second.id)

        assertEquals(false, repo.find(first.id)?.isDefault)
        assertEquals(true, repo.find(second.id)?.isDefault)
    }

    @Test
    fun `updateCursor persists the last seen message`() = runBlocking {
        val repo = freshRepository()
        val account = repo.upsertConnected("gmail", "jane@example.com", sampleTokens)

        repo.updateCursor(account.id, "msg-42", 5000L)

        val updated = repo.find(account.id)
        assertEquals("msg-42", updated?.lastSeenMessageId)
        assertEquals(5000L, updated?.lastSeenAt)
    }

    @Test
    fun `updateAccessToken replaces only the access token and expiry`() = runBlocking {
        val repo = freshRepository()
        val account = repo.upsertConnected("gmail", "jane@example.com", sampleTokens)

        repo.updateAccessToken(account.id, "access-refreshed", 9_999L)

        assertEquals(EmailTokens("access-refreshed", "refresh-1", 9_999L), repo.getTokens(account.id))
    }

    @Test
    fun `delete removes the account`() = runBlocking {
        val repo = freshRepository()
        val account = repo.upsertConnected("gmail", "jane@example.com", sampleTokens)

        assertTrue(repo.delete(account.id))

        assertNull(repo.find(account.id))
    }

    @Test
    fun `find on an unknown id returns null`() = runBlocking {
        val repo = freshRepository()

        assertNull(repo.find("does-not-exist"))
    }
}
