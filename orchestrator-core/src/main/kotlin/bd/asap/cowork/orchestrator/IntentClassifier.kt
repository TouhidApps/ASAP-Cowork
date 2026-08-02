package bd.asap.cowork.orchestrator

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.llmgateway.ChatMessage
import bd.asap.cowork.llmgateway.ChatRole
import bd.asap.cowork.llmgateway.LlmProviderRegistry

/**
 * Decides which registered agent's capability a raw chat message belongs to,
 * so the client no longer names a `stage` up front (PLAN.md §2.3 task
 * routing — single-task version; multi-task DAG decomposition is still
 * future work). Falls back to the first registered agent if the model's
 * reply doesn't clearly name one, so routing never dead-ends. [history] lets
 * a message that only makes sense in context (e.g. "make it use dark mode",
 * with no other noun to route on) still classify correctly.
 */
class IntentClassifier(private val providers: LlmProviderRegistry) {
    suspend fun classify(input: String, candidates: List<Agent>, history: List<ConversationTurn> = emptyList()): Capability {
        require(candidates.isNotEmpty()) { "No agents registered to classify against" }
        if (candidates.size == 1) return candidates.single().capabilities.first()

        val roster = candidates.joinToString("\n") { agent ->
            "- ${agent.capabilities.joinToString(",") { it.id }}: ${agent.description}"
        }
        val systemPrompt = buildString {
            appendLine("You route a user's chat message to exactly one capability id from this list:")
            appendLine(roster)
            appendLine("Reply with ONLY the capability id, nothing else — no punctuation, no explanation.")
        }

        val messages = history.map { it.toChatMessage() } + ChatMessage(ChatRole.USER, input)
        val reply = StringBuilder()
        providers.current().streamComplete(systemPrompt, messages).collect { reply.append(it) }

        val known = candidates.flatMap { it.capabilities }
        return known.firstOrNull { it.id in reply.toString() } ?: known.first()
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
