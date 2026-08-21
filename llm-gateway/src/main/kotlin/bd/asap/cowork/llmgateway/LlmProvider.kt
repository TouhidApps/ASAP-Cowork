package bd.asap.cowork.llmgateway

import kotlinx.coroutines.flow.Flow

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val content: String)

/** An image attached to the current turn (e.g. a UI design reference), sent to the model alongside the text so a vision-capable provider can actually see it. [base64Data] is the raw file bytes, base64-encoded — the one encoding every provider's SDK accepts (as-is for Anthropic/a data URI for OpenAI-compatible/decoded back to bytes for Gemini). */
data class ImageAttachment(val mimeType: String, val base64Data: String)

/**
 * Provider-agnostic contract every LLM implementation (Claude, OpenAI, Gemini,
 * Ollama) satisfies. Agents and the orchestrator depend only on this — never
 * on a specific SDK — so a provider/model can be swapped per agent without
 * touching agent code (see PLAN.md §4, LLM provider abstraction).
 */
interface LlmProvider {
    val id: String

    /** Whether this provider needs an API key at all — false only for Ollama (local, unauthenticated). */
    val requiresApiKey: Boolean get() = true

    /**
     * Streams the model's reply as it's generated. [fast] routes to a
     * cheaper/smaller model instead of the provider's main one — for
     * short, low-stakes calls like intent classification, where the main
     * model's quality is unnecessary overhead. Defaults to false so every
     * existing call site keeps using the main model unless it opts in.
     */
    fun streamComplete(systemPrompt: String?, messages: List<ChatMessage>, fast: Boolean = false): Flow<String>

    /**
     * Runs a full agentic tool-use loop: sends [userMessage] with [tools]
     * available, executes any tool the model calls via [executor], feeds
     * the result back, and repeats until the model produces a final text
     * reply or the iteration cap is hit. [describe] turns a tool call's raw
     * name/input into the human-readable label (e.g. "Running Gradle:
     * assembleDebug") reported on the first `ToolActivity.STARTED` event for
     * that call — defaults to the bare tool name for callers that don't
     * need friendlier wording. [images] are attached to [userMessage] as
     * additional content so a vision-capable model actually sees them
     * (e.g. a UI design reference), not just a text mention that one exists.
     * [history] is every prior turn in the same conversation (oldest
     * first, not including [userMessage] itself) — sent ahead of it so a
     * follow-up like "why did that build fail" or "now add a settings
     * screen" resolves against what was actually said/done earlier instead
     * of starting fresh each turn.
     */
    fun runAgenticLoop(
        systemPrompt: String,
        userMessage: String,
        tools: List<ToolSpec>,
        executor: ToolExecutor,
        describe: (String, Map<String, Any?>) -> String = { name, _ -> name },
        images: List<ImageAttachment> = emptyList(),
        history: List<ChatMessage> = emptyList(),
    ): Flow<AgentStreamEvent>
}
