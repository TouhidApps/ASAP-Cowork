package bd.asap.cowork.chatgateway.features.email

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.GmailOAuthCredentialsStore
import bd.asap.cowork.contextstore.EmailAccount
import bd.asap.cowork.contextstore.EmailAccountRepository
import bd.asap.cowork.contextstore.EmailNotificationSettings
import bd.asap.cowork.contextstore.EmailSettingsRepository
import bd.asap.cowork.contextstore.EmailTokens
import bd.asap.cowork.toolintegrations.email.GmailApiClient
import bd.asap.cowork.toolintegrations.email.GmailOAuthClient
import bd.asap.cowork.toolintegrations.email.GmailOAuthCredentials
import bd.asap.cowork.toolintegrations.email.GmailOAuthCredentialsRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Backs the Email tools page under the admin panel: the Gmail OAuth client
 * (Client ID/Secret) and the "Connect Gmail account" flow, which accounts
 * are connected, and the notification preferences. Reached through
 * [bd.asap.cowork.chatgateway.features.email.emailToolsRoutes], mounted
 * inside `adminRoutes()`'s ADMIN_TOKEN-gated `/api/v1/admin` block — except
 * the OAuth callback itself ([handleOAuthCallback]'s caller), which Google
 * reaches via a plain browser redirect that can't carry that bearer token,
 * so it's registered separately (see Routing.kt) and CSRF-protected by
 * [pendingState] instead.
 */
class EmailService(
    private val accounts: EmailAccountRepository,
    private val settings: EmailSettingsRepository,
    private val oauthCredentialsStore: GmailOAuthCredentialsStore,
) {
    private val pendingState = AtomicReference<String?>(null)

    suspend fun listAccounts(): List<EmailAccount> = accounts.list()

    suspend fun setDefaultAccount(id: String) {
        accounts.find(id) ?: throw AppException.NotFound("Email account not found: $id")
        accounts.setDefault(id)
    }

    suspend fun disconnectAccount(id: String) {
        if (!accounts.delete(id)) throw AppException.NotFound("Email account not found: $id")
    }

    suspend fun getSettings(): EmailNotificationSettings = settings.get()

    suspend fun updateSettings(value: EmailNotificationSettings) {
        if (value.pollIntervalMinutes < 1) throw AppException.BadRequest("pollIntervalMinutes must be at least 1")
        settings.update(value)
    }

    suspend fun oauthStatus(): GmailOAuthStatus {
        val credentials = oauthCredentialsStore.read()
        return GmailOAuthStatus(
            configured = credentials != null,
            clientId = credentials?.clientId,
            clientSecret = credentials?.clientSecret,
            redirectUri = REDIRECT_URI,
        )
    }

    suspend fun updateOAuthCredentials(clientId: String, clientSecret: String): GmailOAuthStatus {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw AppException.BadRequest("clientId and clientSecret must not be blank")
        }
        val credentials = GmailOAuthCredentials(clientId.trim(), clientSecret.trim())
        oauthCredentialsStore.write(credentials)
        GmailOAuthCredentialsRegistry.set(credentials)
        return oauthStatus()
    }

    /** [state] is stashed for [handleOAuthCallback] to verify — this app has exactly one admin, so a single in-flight value is enough CSRF protection without a database table. */
    fun buildAuthorizeUrl(): GmailOAuthAuthorizeUrl {
        val credentials = GmailOAuthCredentialsRegistry.current()
            ?: throw AppException.BadRequest("Set the Gmail OAuth client id/secret first.")
        val state = UUID.randomUUID().toString()
        pendingState.set(state)
        return GmailOAuthAuthorizeUrl(GmailOAuthClient.authorizationUrl(credentials, REDIRECT_URI, state))
    }

    /** Returns the newly-connected account's email address, or throws with a message safe to show the user. */
    suspend fun handleOAuthCallback(code: String?, state: String?, error: String?): String {
        if (error != null) throw AppException.BadRequest("Google sign-in didn't complete: $error")
        val authorizationCode = code ?: throw AppException.BadRequest("Google's redirect was missing an authorization code.")

        val expectedState = pendingState.getAndSet(null)
        if (expectedState == null || expectedState != state) {
            throw AppException.BadRequest("This sign-in link has expired or was already used — click Connect Gmail Account again.")
        }

        val credentials = GmailOAuthCredentialsRegistry.current()
            ?: throw AppException.BadRequest("Gmail OAuth client id/secret isn't configured.")

        val token = GmailOAuthClient.exchangeCode(credentials, authorizationCode, REDIRECT_URI)
            .getOrElse { throw AppException.BadRequest("Couldn't exchange the authorization code: ${it.message}") }
        val refreshToken = token.refreshToken
            ?: throw AppException.BadRequest("Google didn't return a refresh token — disconnect (if already connected) and try again.")

        val emailAddress = GmailApiClient.getProfile(token.accessToken)
            .getOrElse { throw AppException.BadRequest("Signed in, but couldn't read the account's address: ${it.message}") }

        val expiresAt = System.currentTimeMillis() + token.expiresInSeconds * 1000
        val account = accounts.upsertConnected("gmail", emailAddress, EmailTokens(token.accessToken, refreshToken, expiresAt))
        enableForPollingIfOnlyAccount(account.id)
        return emailAddress
    }

    /** A lone connected account is enabled for the background poll automatically — there's nothing to choose between yet. A second (or later) account still needs an explicit opt-in, since which of several accounts to poll is a real choice. */
    private suspend fun enableForPollingIfOnlyAccount(accountId: String) {
        if (accounts.list().size != 1) return
        val current = settings.get()
        if (accountId in current.enabledAccountIds) return
        settings.update(current.copy(enabledAccountIds = current.enabledAccountIds + accountId))
    }

    companion object {
        /** Must exactly match an authorized redirect URI on the Google Cloud OAuth client — chat-gateway always listens on 8081 (see Application.kt). */
        const val REDIRECT_URI = "http://localhost:8081/api/v1/oauth/gmail/callback"
    }
}
