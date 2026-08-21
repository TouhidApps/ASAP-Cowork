package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.contextstore.ApiUsageRecord
import bd.asap.cowork.contextstore.ApiUsageRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())
private const val RECENT_LIMIT = 200

/**
 * Aggregates raw [ApiUsageRecord] rows (one per LLM call, see [ApiUsageRepository]) into the
 * admin usage tab's day/provider breakdown and cost totals. Aggregation happens here in Kotlin,
 * not in SQL, since the underlying table is one row per request — small enough that grouping a
 * date-filtered slice in memory is simpler than date-bucketed SQL and gives identical results
 * regardless of SQLite's date-function quirks.
 */
class UsageService(private val repository: ApiUsageRepository) {
    suspend fun summary(from: Long?, to: Long?, provider: String?): UsageSummary {
        val records = repository.list(from = from, to = to, provider = provider)

        val byProvider = records.groupBy { it.provider }
            .map { (id, rows) -> id.toProviderTotal(rows) }
            .sortedByDescending { it.totalTokens }

        val byDay = records.groupBy { DAY_FORMATTER.format(Instant.ofEpochMilli(it.createdAt)) to it.provider }
            .map { (key, rows) -> key.toDailyUsage(rows) }
            .sortedWith(compareBy({ it.date }, { it.provider }))

        return UsageSummary(
            totalInputTokens = records.sumOf { it.inputTokens },
            totalOutputTokens = records.sumOf { it.outputTokens },
            totalTokens = records.sumOf { it.inputTokens + it.outputTokens },
            totalCostUsd = records.sumOf { UsagePricing.costUsd(it.provider, it.model, it.inputTokens, it.outputTokens) },
            requestCount = records.size,
            byProvider = byProvider,
            byDay = byDay,
            recent = records.sortedByDescending { it.createdAt }.take(RECENT_LIMIT).map { it.toEntry() },
        )
    }

    private fun String.toProviderTotal(rows: List<ApiUsageRecord>) = ProviderUsageTotal(
        provider = this,
        inputTokens = rows.sumOf { it.inputTokens },
        outputTokens = rows.sumOf { it.outputTokens },
        totalTokens = rows.sumOf { it.inputTokens + it.outputTokens },
        costUsd = rows.sumOf { UsagePricing.costUsd(it.provider, it.model, it.inputTokens, it.outputTokens) },
        requestCount = rows.size,
    )

    private fun Pair<String, String>.toDailyUsage(rows: List<ApiUsageRecord>) = DailyProviderUsage(
        date = first,
        provider = second,
        inputTokens = rows.sumOf { it.inputTokens },
        outputTokens = rows.sumOf { it.outputTokens },
        totalTokens = rows.sumOf { it.inputTokens + it.outputTokens },
        costUsd = rows.sumOf { UsagePricing.costUsd(it.provider, it.model, it.inputTokens, it.outputTokens) },
        requestCount = rows.size,
    )

    private fun ApiUsageRecord.toEntry() = UsageEntry(
        id = id,
        provider = provider,
        model = model,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = inputTokens + outputTokens,
        costUsd = UsagePricing.costUsd(provider, model, inputTokens, outputTokens),
        createdAt = createdAt,
    )
}
