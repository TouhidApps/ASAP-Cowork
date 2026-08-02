package bd.asap.cowork.llmgateway

/**
 * Holds every registered [LlmProvider] plus whichever one is currently
 * active. Agents ask this for [current] on every call rather than holding a
 * fixed provider instance, so switching providers (from the admin panel, or
 * later from `ChatModule`-style startup config) takes effect immediately —
 * mirrors the prior prototype's `LlmProviderRegistry` (see PLAN.md §9).
 */
class LlmProviderRegistry(providers: List<LlmProvider>, currentId: String = providers.first().id) {
    private val byId: Map<String, LlmProvider> = providers.associateBy { it.id }

    var currentId: String = currentId
        private set

    fun current(): LlmProvider =
        byId[currentId] ?: error("No LLM provider registered with id '$currentId'")

    fun available(): List<LlmProvider> = byId.values.toList()

    fun switchTo(id: String) {
        require(id in byId) { "Unknown LLM provider '$id'. Available: ${byId.keys}" }
        currentId = id
    }
}
