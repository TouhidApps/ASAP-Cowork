package bd.asap.cowork.contextstore

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/** Public-facing shape — deliberately excludes [EmailAccountsTable]'s token columns so they're never at risk of being serialized back to the web-ui. Fetch those separately via [EmailAccountRepository.getTokens]. */
@Serializable
data class EmailAccount(
    val id: String,
    val provider: String,
    val emailAddress: String,
    val displayLabel: String?,
    val isDefault: Boolean,
    val lastSeenMessageId: String?,
    val lastSeenAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** OAuth tokens for one connected account — never exposed outside [EmailAccountRepository]/the email agent's own API calls. */
data class EmailTokens(val accessToken: String, val refreshToken: String, val expiresAt: Long)

object EmailAccountsTable : Table("email_accounts") {
    val id = text("id")
    val provider = text("provider")
    val emailAddress = text("email_address")
    val displayLabel = text("display_label").nullable()
    val isDefault = bool("is_default").default(false)
    val lastSeenMessageId = text("last_seen_message_id").nullable()
    val lastSeenAt = long("last_seen_at").nullable()
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val tokenExpiresAt = long("token_expires_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Every Gmail (and, later, other-provider) account the user has connected
 * via OAuth ([bd.asap.cowork.toolintegrations.email.GmailOAuthClient]),
 * plus its tokens and the polling cursor
 * ([EmailAccount.lastSeenMessageId]/[lastSeenAt]) EmailAgent's background
 * check advances past on every tick so the same message is never
 * re-evaluated for a notification. A dedicated table rather than a
 * [SettingsRepository] JSON blob because rows are added/looked-up
 * individually and the cursor mutates on every poll, not just when the user
 * changes a setting.
 */
class EmailAccountRepository(private val db: Database) {
    suspend fun list(): List<EmailAccount> = newSuspendedTransaction(db = db) {
        EmailAccountsTable.selectAll().map { it.toEmailAccount() }
    }

    suspend fun find(id: String): EmailAccount? = newSuspendedTransaction(db = db) {
        EmailAccountsTable.selectAll().where { EmailAccountsTable.id eq id }.firstOrNull()?.toEmailAccount()
    }

    suspend fun getTokens(id: String): EmailTokens? = newSuspendedTransaction(db = db) {
        EmailAccountsTable.selectAll().where { EmailAccountsTable.id eq id }.firstOrNull()?.let { row ->
            val access = row[EmailAccountsTable.accessToken]
            val refresh = row[EmailAccountsTable.refreshToken]
            val expiresAt = row[EmailAccountsTable.tokenExpiresAt]
            if (access != null && refresh != null && expiresAt != null) EmailTokens(access, refresh, expiresAt) else null
        }
    }

    /** Inserts a newly-connected (provider, emailAddress) account with its tokens, or refreshes the tokens on an already-connected one — reconnecting an existing account never resets its cursor/default flag. */
    suspend fun upsertConnected(provider: String, emailAddress: String, tokens: EmailTokens): EmailAccount = newSuspendedTransaction(db = db) {
        val now = System.currentTimeMillis()
        val existing = EmailAccountsTable.selectAll()
            .where { (EmailAccountsTable.provider eq provider) and (EmailAccountsTable.emailAddress eq emailAddress) }
            .firstOrNull()

        if (existing != null) {
            val id = existing[EmailAccountsTable.id]
            EmailAccountsTable.update({ EmailAccountsTable.id eq id }) {
                it[accessToken] = tokens.accessToken
                it[refreshToken] = tokens.refreshToken
                it[tokenExpiresAt] = tokens.expiresAt
                it[updatedAt] = now
            }
            return@newSuspendedTransaction existing.toEmailAccount()
        }

        val id = UUID.randomUUID().toString()
        // The very first account ever connected becomes the default so a
        // single-account setup works with zero configuration — a second
        // account added later stays non-default until the user picks one.
        val makeDefault = EmailAccountsTable.selectAll().count() == 0L
        EmailAccountsTable.insert {
            it[EmailAccountsTable.id] = id
            it[EmailAccountsTable.provider] = provider
            it[EmailAccountsTable.emailAddress] = emailAddress
            it[isDefault] = makeDefault
            it[accessToken] = tokens.accessToken
            it[refreshToken] = tokens.refreshToken
            it[tokenExpiresAt] = tokens.expiresAt
            it[createdAt] = now
            it[updatedAt] = now
        }
        EmailAccount(id, provider, emailAddress, null, makeDefault, null, null, now, now)
    }

    suspend fun updateAccessToken(id: String, accessToken: String, expiresAt: Long): Unit = newSuspendedTransaction(db = db) {
        EmailAccountsTable.update({ EmailAccountsTable.id eq id }) {
            it[EmailAccountsTable.accessToken] = accessToken
            it[tokenExpiresAt] = expiresAt
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun setDefault(id: String): Unit = newSuspendedTransaction(db = db) {
        val now = System.currentTimeMillis()
        EmailAccountsTable.update({ EmailAccountsTable.id neq id }) { it[isDefault] = false; it[updatedAt] = now }
        EmailAccountsTable.update({ EmailAccountsTable.id eq id }) { it[isDefault] = true; it[updatedAt] = now }
    }

    suspend fun updateCursor(id: String, lastSeenMessageId: String, lastSeenAt: Long): Unit = newSuspendedTransaction(db = db) {
        EmailAccountsTable.update({ EmailAccountsTable.id eq id }) {
            it[EmailAccountsTable.lastSeenMessageId] = lastSeenMessageId
            it[EmailAccountsTable.lastSeenAt] = lastSeenAt
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    /** Disconnects an account — deletes its row (and tokens) entirely; there's no separate "revoke" call to Google, matching the scope of what this app manages. */
    suspend fun delete(id: String): Boolean = newSuspendedTransaction(db = db) {
        EmailAccountsTable.deleteWhere { EmailAccountsTable.id eq id } > 0
    }

    private fun ResultRow.toEmailAccount() = EmailAccount(
        id = this[EmailAccountsTable.id],
        provider = this[EmailAccountsTable.provider],
        emailAddress = this[EmailAccountsTable.emailAddress],
        displayLabel = this[EmailAccountsTable.displayLabel],
        isDefault = this[EmailAccountsTable.isDefault],
        lastSeenMessageId = this[EmailAccountsTable.lastSeenMessageId],
        lastSeenAt = this[EmailAccountsTable.lastSeenAt],
        createdAt = this[EmailAccountsTable.createdAt],
        updatedAt = this[EmailAccountsTable.updatedAt],
    )
}
