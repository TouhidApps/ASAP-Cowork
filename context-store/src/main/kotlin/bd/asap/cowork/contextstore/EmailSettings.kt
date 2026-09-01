package bd.asap.cowork.contextstore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class EmailNotificationMode { ALL, IMPORTANT_ONLY }

@Serializable
data class EmailNotificationSettings(
    val mode: EmailNotificationMode = EmailNotificationMode.ALL,
    val pollIntervalMinutes: Int = 3,
    val inAppEnabled: Boolean = true,
    val osEnabled: Boolean = true,
    val defaultAccountId: String? = null,
    val enabledAccountIds: Set<String> = emptySet(),
)

/**
 * Notification preferences for the email agent's background inbox poll —
 * mode (every new email vs. important-only), how often to check, which
 * channels to notify through, and which accounts to watch. Stored as one
 * JSON blob under [SettingsRepository] (same technique as
 * `ToolchainSettingsStore` in chat-gateway), and placed here rather than in
 * chat-gateway's config package because both `email-agent` (to know the
 * default account/mode while polling) and chat-gateway's scheduler need to
 * read it, and agent modules depend on `context-store` but never on
 * `chat-gateway`.
 */
class EmailSettingsRepository(private val settings: SettingsRepository) {
    suspend fun get(): EmailNotificationSettings {
        val stored = settings.get(KEY) ?: return EmailNotificationSettings()
        return runCatching { Json.decodeFromString<EmailNotificationSettings>(stored) }.getOrDefault(EmailNotificationSettings())
    }

    suspend fun update(settingsValue: EmailNotificationSettings) {
        settings.set(KEY, Json.encodeToString(settingsValue))
    }

    private companion object {
        const val KEY = "email.notifications"
    }
}
