package bd.asap.cowork.chatgateway.features.admin

/**
 * Illustrative per-million-token USD rates, one blended rate per provider plus overrides for a
 * handful of known cheap/fast models (e.g. the classifier's Haiku calls) — the provider classes
 * in llm-gateway each point at whichever model string is configured/current, so pinning cost math
 * to one literal model id here would silently go stale the moment that default changes; the
 * per-provider blended rate stays the fallback for anything not explicitly listed. Tune these to
 * match your actual contracted rates. Ollama is self-hosted, so it's always free regardless of
 * what's in this table.
 */
object UsagePricing {
    private data class Rate(val inputPer1M: Double, val outputPer1M: Double)

    private val providerRates = mapOf(
        "anthropic" to Rate(inputPer1M = 15.0, outputPer1M = 75.0),
        "openai" to Rate(inputPer1M = 5.0, outputPer1M = 15.0),
        "gemini" to Rate(inputPer1M = 3.5, outputPer1M = 10.5),
        "ollama" to Rate(inputPer1M = 0.0, outputPer1M = 0.0),
    )

    /** Cheap/fast models (see each provider's `fastModel`, used for `fast = true` calls like intent classification) priced well below their provider's flagship rate. */
    private val modelRates = mapOf(
        "claude-haiku-4-5-20251001" to Rate(inputPer1M = 1.0, outputPer1M = 5.0),
        "gemini-3.1-flash-preview" to Rate(inputPer1M = 0.15, outputPer1M = 0.6),
        "gpt-5.4-mini" to Rate(inputPer1M = 0.4, outputPer1M = 1.6),
    )

    fun costUsd(providerId: String, model: String, inputTokens: Long, outputTokens: Long): Double {
        val rate = modelRates[model] ?: providerRates[providerId] ?: return 0.0
        return (inputTokens / 1_000_000.0) * rate.inputPer1M + (outputTokens / 1_000_000.0) * rate.outputPer1M
    }
}
