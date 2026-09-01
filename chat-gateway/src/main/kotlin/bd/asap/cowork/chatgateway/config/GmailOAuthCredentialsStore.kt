package bd.asap.cowork.chatgateway.config

import bd.asap.cowork.contextstore.SettingsRepository
import bd.asap.cowork.toolintegrations.email.GmailOAuthCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredGmailOAuthCredentials(val clientId: String, val clientSecret: String)

/**
 * The Google OAuth client (Client ID/Secret) saved from the admin panel's
 * Email tools page, backed by the SQLite `settings` table (JSON-encoded,
 * one row) — same technique as [ToolchainSettingsStore]. Every write also
 * updates `GmailOAuthCredentialsRegistry` (what
 * `bd.asap.cowork.toolintegrations.email.GmailOAuthClient` actually reads),
 * so a save takes effect immediately, no restart needed.
 */
class GmailOAuthCredentialsStore(private val settings: SettingsRepository) {
    suspend fun read(): GmailOAuthCredentials? {
        val stored = settings.get(KEY) ?: return null
        val decoded = runCatching { Json.decodeFromString<StoredGmailOAuthCredentials>(stored) }.getOrNull() ?: return null
        return GmailOAuthCredentials(decoded.clientId, decoded.clientSecret)
    }

    suspend fun write(credentials: GmailOAuthCredentials) {
        settings.set(KEY, Json.encodeToString(StoredGmailOAuthCredentials(credentials.clientId, credentials.clientSecret)))
    }

    private companion object {
        const val KEY = "email.gmail_oauth_credentials"
    }
}
