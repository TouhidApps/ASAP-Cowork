package bd.asap.cowork.llmgateway

/** A tool the model may call, described as a plain JSON-Schema map so no provider SDK type leaks into agent-sdk. */
data class ToolSpec(val name: String, val description: String, val parametersSchema: Map<String, Any?>)

/**
 * What a tool invocation produced, fed back to the model as the next turn.
 * [imageUrl]/[videoUrl]/[fileUrl] let a tool like `capture_device_screenshot`,
 * `record_device_video`, or `write_brand_asset` hand back a URL the chat UI
 * should actually show, and [notice] a short highlighted callout (e.g.
 * "recording on an emulator is unaccelerated, connect a physical device for
 * smoother recordings") — see [emitMediaNotes], which turns these into
 * markdown lines the frontend renders specially, the same way every
 * provider's loop does today regardless of whether the model's own text
 * ever mentions them. Deliberately never relies on the model constructing a
 * URL itself in freeform reply text — it has no way to know the real one
 * and will otherwise just guess a plausible-looking but nonexistent one.
 */
data class ToolResult(
    val summary: String,
    val isError: Boolean = false,
    val imageUrl: String? = null,
    /** Alt text for [imageUrl] — defaults to "Screenshot" in [emitMediaNotes] when null, for tools that don't have anything more specific to say. */
    val imageAlt: String? = null,
    val videoUrl: String? = null,
    /** A non-image, non-video file worth surfacing as a clickable link (e.g. a written brand-guide.md) rather than inline media. */
    val fileUrl: String? = null,
    val notice: String? = null,
)

enum class ToolActivityStatus { STARTED, FINISHED, FAILED }

/** Events emitted while [LlmProvider.runAgenticLoop] runs — a superset of plain text streaming that also surfaces tool activity for the UI. */
sealed interface AgentStreamEvent {
    data class TextDelta(val text: String) : AgentStreamEvent
    data class ToolActivity(val tool: String, val label: String, val status: ToolActivityStatus) : AgentStreamEvent
}

/** Executes one tool call by name. [onProgress] lets a long-running tool (a Gradle build, an emulator boot) report interim status before it finishes. */
fun interface ToolExecutor {
    suspend fun execute(name: String, input: Map<String, Any?>, onProgress: suspend (String) -> Unit): ToolResult
}

/**
 * Called by every provider's agentic loop right after a tool finishes, so a
 * screenshot/video a tool produced is guaranteed to actually reach the chat
 * — as a markdown image/video line the frontend's MessageBubble recognizes
 * and renders inline — regardless of whether the model's own reply text
 * happens to mention it. Takes an explicit `emit` rather than being a
 * [FlowCollector] extension because callers emit via different receivers
 * (`FlowCollector.emit` for `flow { }`, `ProducerScope.send` for `channelFlow { }`).
 */
suspend fun emitMediaNotes(result: ToolResult, emit: suspend (AgentStreamEvent) -> Unit) {
    result.imageUrl?.let { emit(AgentStreamEvent.TextDelta("\n\n![${result.imageAlt ?: "Screenshot"}]($it)\n")) }
    result.videoUrl?.let { emit(AgentStreamEvent.TextDelta("\n\n[Video]($it)\n")) }
    result.fileUrl?.let { emit(AgentStreamEvent.TextDelta("\n\n[${it.substringAfterLast('/')}]($it)\n")) }
    result.notice?.let { emit(AgentStreamEvent.TextDelta("\n\n> $it\n")) }
}
