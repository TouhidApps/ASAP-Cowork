package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class UsageEntry(
    val id: String,
    val provider: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val costUsd: Double,
    val createdAt: Long,
)

@Serializable
data class ProviderUsageTotal(
    val provider: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val costUsd: Double,
    val requestCount: Int,
)

/** One provider's totals for one calendar day (in the server's local time zone), the unit the usage tab's trend chart plots. */
@Serializable
data class DailyProviderUsage(
    val date: String,
    val provider: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val costUsd: Double,
    val requestCount: Int,
)

@Serializable
data class UsageSummary(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val totalCostUsd: Double,
    val requestCount: Int,
    val byProvider: List<ProviderUsageTotal>,
    val byDay: List<DailyProviderUsage>,
    /** Newest-first, capped so the response stays small — the detail table paginates over this. */
    val recent: List<UsageEntry>,
)
