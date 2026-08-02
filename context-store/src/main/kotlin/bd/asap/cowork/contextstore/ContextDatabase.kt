package bd.asap.cowork.contextstore

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.io.File

/**
 * Owns the SQLite connection pool + Flyway migrations + the Exposed
 * [database] handle every repository in this module queries through.
 * [close] must be called on server shutdown to release the pool — the
 * caller (chat-gateway's `configureDatabase()`) ties it to Ktor's
 * ApplicationStopped event.
 */
class ContextDatabase private constructor(
    val database: Database,
    private val dataSource: HikariDataSource,
    private val path: String,
) {
    fun close() {
        dataSource.close()
    }

    /**
     * Total bytes on disk for this database, including the `-wal`/`-shm`
     * sidecar files — in WAL mode (see [connect]'s `journal_mode=WAL`),
     * recent writes live there until SQLite checkpoints them back into the
     * main file, so the main file alone would under-report actual usage.
     */
    fun sizeBytes(): Long =
        listOf(File(path), File("$path-wal"), File("$path-shm")).sumOf { if (it.exists()) it.length() else 0L }

    companion object {
        fun connect(path: String = "data/app.db", maximumPoolSize: Int = 5): ContextDatabase {
            File(path).parentFile?.mkdirs()

            // SQLite pragmas go on the JDBC URL (Xerial's driver applies them
            // once per physical connection) rather than Hikari's
            // connectionInitSql, which only reliably supports one statement.
            val jdbcUrl = "jdbc:sqlite:$path?foreign_keys=on&busy_timeout=5000&journal_mode=WAL"

            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    driverClassName = "org.sqlite.JDBC"
                    this.jdbcUrl = jdbcUrl
                    this.maximumPoolSize = maximumPoolSize
                },
            )

            Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate()

            return ContextDatabase(Database.connect(dataSource), dataSource, path)
        }
    }
}
