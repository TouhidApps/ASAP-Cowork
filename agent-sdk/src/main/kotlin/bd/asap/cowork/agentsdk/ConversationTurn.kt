package bd.asap.cowork.agentsdk

/**
 * One prior turn from the same conversation, threaded onto [Task.history] so
 * an agent's LLM call can see what was already said instead of treating
 * every message as the start of a new conversation. Plain (role, content)
 * rather than llm-gateway's `ChatMessage` — agent-sdk is the base module and
 * must not depend on llm-gateway, so each agent (which does depend on it)
 * converts this to a `ChatMessage` itself.
 */
data class ConversationTurn(
    val role: String,
    val content: String,
    /**
     * Which [Capability] handled this turn — set on assistant turns only,
     * null for user turns. Exists so `IntentClassifier` can look up "what
     * was this conversation just doing" as a fact instead of inferring
     * continuity purely from the reply text, which routing history showed
     * is unreliable for a short/vague follow-up with no domain keywords.
     */
    val capability: String? = null,
)
