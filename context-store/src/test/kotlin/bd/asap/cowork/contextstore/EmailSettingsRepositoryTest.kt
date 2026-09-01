package bd.asap.cowork.contextstore

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailSettingsRepositoryTest {
    private fun freshRepository(): EmailSettingsRepository {
        val dbPath = createTempDirectory("context-store-test").resolve("test.db").toString()
        return EmailSettingsRepository(SettingsRepository(ContextDatabase.connect(dbPath).database))
    }

    @Test
    fun `no stored settings returns defaults`() = runBlocking {
        val repo = freshRepository()

        val settings = repo.get()

        assertEquals(EmailNotificationMode.ALL, settings.mode)
        assertEquals(3, settings.pollIntervalMinutes)
        assertEquals(true, settings.inAppEnabled)
        assertEquals(true, settings.osEnabled)
    }

    @Test
    fun `update then get round-trips every field`() = runBlocking {
        val repo = freshRepository()
        val updated = EmailNotificationSettings(
            mode = EmailNotificationMode.IMPORTANT_ONLY,
            pollIntervalMinutes = 10,
            inAppEnabled = false,
            osEnabled = true,
            defaultAccountId = "acct-1",
            enabledAccountIds = setOf("acct-1", "acct-2"),
        )

        repo.update(updated)

        assertEquals(updated, repo.get())
    }
}
