package bd.asap.cowork.contextstore

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

/** One completed LLM API call — a single row per request, cheap enough to aggregate (by day/provider) at read time rather than pre-rolling it up. */
data class ApiUsageRecord(
    val id: String = UUID.randomUUID().toString(),
    val provider: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

object ApiUsageTable : Table("api_usage") {
    val id = text("id")
    val provider = text("provider")
    val model = text("model")
    val inputTokens = long("input_tokens")
    val outputTokens = long("output_tokens")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Every LLM provider call, recorded by [bd.asap.cowork.llmgateway.UsageListener] wiring in
 * chat-gateway's DI as soon as a request's token usage is known. The admin usage tab reads this
 * back via [list], grouping/summing in Kotlin rather than SQL — this table stays small enough
 * (one row per API call, not per token) that in-memory aggregation is simpler than date-bucketed
 * SQL and works the same on SQLite regardless of how [from]/[to] land relative to day boundaries.
 */
class ApiUsageRepository(private val db: Database) {
    suspend fun record(usage: ApiUsageRecord): Unit = newSuspendedTransaction(db = db) {
        ApiUsageTable.insert {
            it[id] = usage.id
            it[provider] = usage.provider
            it[model] = usage.model
            it[inputTokens] = usage.inputTokens
            it[outputTokens] = usage.outputTokens
            it[createdAt] = usage.createdAt
        }
    }

    /** [from]/[to] are epoch millis, inclusive/exclusive respectively — either may be omitted to leave that end open. */
    suspend fun list(from: Long? = null, to: Long? = null, provider: String? = null): List<ApiUsageRecord> =
        newSuspendedTransaction(db = db) {
            ApiUsageTable.selectAll()
                .apply {
                    if (from != null) andWhere { ApiUsageTable.createdAt greaterEq from }
                    if (to != null) andWhere { ApiUsageTable.createdAt less to }
                    if (provider != null) andWhere { ApiUsageTable.provider eq provider }
                }
                .orderBy(ApiUsageTable.createdAt, SortOrder.DESC)
                .map { it.toRecord() }
        }

    private fun ResultRow.toRecord() = ApiUsageRecord(
        id = this[ApiUsageTable.id],
        provider = this[ApiUsageTable.provider],
        model = this[ApiUsageTable.model],
        inputTokens = this[ApiUsageTable.inputTokens],
        outputTokens = this[ApiUsageTable.outputTokens],
        createdAt = this[ApiUsageTable.createdAt],
    )
}
