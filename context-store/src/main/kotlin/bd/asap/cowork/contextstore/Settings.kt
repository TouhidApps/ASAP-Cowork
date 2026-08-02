package bd.asap.cowork.contextstore

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

object SettingsTable : Table("settings") {
    val key = text("key")
    val value = text("value").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(key)
}

/**
 * A generic string key/value store backing every small settings blob this
 * app needs to remember across restarts (workspace root, toolchain SDK
 * paths, provider API keys, the currently-selected LLM provider, Firebase
 * credentials, ...) — one `settings` table instead of a bespoke Exposed
 * table per setting, since none of them need relational features. Each
 * chat-gateway settings store wraps this with its own typed
 * read/write methods and (for multi-field settings) JSON encoding.
 */
class SettingsRepository(private val db: Database) {
    suspend fun get(key: String): String? = newSuspendedTransaction(db = db) {
        SettingsTable.selectAll().where { SettingsTable.key eq key }.firstOrNull()?.get(SettingsTable.value)
    }

    suspend fun set(key: String, value: String?) {
        if (value == null) {
            clear(key)
            return
        }
        newSuspendedTransaction(db = db) {
            val now = System.currentTimeMillis()
            val updated = SettingsTable.update({ SettingsTable.key eq key }) {
                it[SettingsTable.value] = value
                it[updatedAt] = now
            }
            if (updated == 0) {
                SettingsTable.insert {
                    it[SettingsTable.key] = key
                    it[SettingsTable.value] = value
                    it[updatedAt] = now
                }
            }
        }
    }

    suspend fun clear(key: String): Unit = newSuspendedTransaction(db = db) {
        SettingsTable.deleteWhere { SettingsTable.key eq key }
    }
}
