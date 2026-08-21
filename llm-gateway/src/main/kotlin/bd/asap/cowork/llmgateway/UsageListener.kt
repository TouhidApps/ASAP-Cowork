package bd.asap.cowork.llmgateway

/** Token usage from one completed provider request — reported after the response (or, inside an agentic loop, each iteration's response) finishes streaming. */
data class LlmUsage(
    val providerId: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
)

/**
 * Notified by a provider as soon as a request's token usage is known. Providers take this as a
 * constructor parameter (default a no-op) rather than depending on any persistence layer
 * themselves — llm-gateway has no business knowing how usage gets stored; chat-gateway's DI wires
 * a listener that writes to `ApiUsageRepository` (context-store) so the admin panel's usage
 * analytics tab has real data.
 */
fun interface UsageListener {
    suspend fun onUsage(usage: LlmUsage)
}
