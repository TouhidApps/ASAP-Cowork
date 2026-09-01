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
 * future work). Falls back to [Capability.GENERAL] (or, if that's not
 * registered, the first registered agent) if the model's reply doesn't
 * clearly name one, so routing never dead-ends. [history] lets
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
        // A recorded fact beats an inference: each stored assistant turn is
        // tagged with the capability that actually handled it (see
        // ConversationTurn.capability), so "what was this conversation just
        // doing" is a lookup here, not something the model has to guess from
        // prose — a guess that repeatedly failed for short/keyword-free
        // follow-ups (e.g. "add a footer: Thank you" after Sheets activity).
        val lastActiveCapability = history.lastOrNull { it.role == "assistant" && it.capability != null }?.capability
        val systemPrompt = buildString {
            appendLine("You route a user's chat message to exactly one capability id from this list:")
            appendLine(roster)
            if (lastActiveCapability != null) {
                appendLine("Fact: the capability that handled the most recent assistant turn in this conversation was '$lastActiveCapability'. Default to routing the new message there too, UNLESS it plainly and specifically asks for something a different capability in the list actually owns — a topic sounding generic or unrelated in isolation is not enough on its own to switch away, since a short follow-up (an edit, an addition, a correction, a bare value/phrase with no explicit object) naturally carries no keywords of its own and still belongs to the same capability as what it's following up on.")
            }
            appendLine("Only pick a generic/catch-all capability (if one exists in the list) when the message is genuinely a fresh, standalone request unrelated to whatever the conversation was just in the middle of — never as a default for a short or vague-sounding message that is actually a continuation.")
            appendLine("Reply with ONLY the capability id, nothing else — no punctuation, no explanation.")
        }

        val messages = history.map { it.toChatMessage() } + ChatMessage(ChatRole.USER, input)
        val reply = StringBuilder()
        // fast = true: picking one capability id from a list is a trivial
        // classification task that doesn't need the flagship model, and this
        // roster system prompt is identical on every call in the app (see
        // AnthropicLlmProvider's cache_control on system prompts), so the fast
        // model + cache combo is what keeps this cheap on every single message.
        providers.current().streamComplete(systemPrompt, messages, fast = true).collect { reply.append(it) }

        val known = candidates.flatMap { it.capabilities }
        // If the model didn't name a capability the roster actually has, route
        // to the general-purpose catch-all (when registered) rather than an
        // arbitrary first agent — an unclassifiable message is exactly what
        // that agent exists for, not a reason to guess.
        return known.firstOrNull { it.id in reply.toString() }
            ?: known.firstOrNull { it == Capability.GENERAL }
            ?: known.first()
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
