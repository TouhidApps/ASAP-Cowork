package bd.asap.cowork.chatgateway

import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.contextstore.StoredAttachment
import kotlinx.serialization.Serializable

@Serializable
data class IncomingMessage(val content: String, val attachments: List<StoredAttachment> = emptyList())

/**
 * Wire representation of [AgentEvent] sent to the chat UI over the WebSocket.
 * Kept in chat-gateway (not agent-sdk) so agent-sdk stays free of a
 * serialization dependency — only the transport layer needs to know the wire
 * shape. `conversation_started` isn't an [AgentEvent] at all — Routing.kt
 * emits it directly once a connection's conversation id is known (resumed
 * from the `conversationId` query param, or just lazily created on the
 * first message), so the client can learn a freshly-created id and keep its
 * history drawer in sync.
 */
@Serializable
data class ChatEvent(
    val type: String,
    val message: String? = null,
    val text: String? = null,
    val path: String? = null,
    val agentId: String? = null,
    val capability: String? = null,
    val conversationId: String? = null,
    val tool: String? = null,
    val status: String? = null,
)

fun AgentEvent.toWire(): ChatEvent = when (this) {
    is AgentEvent.AgentActivated -> ChatEvent(type = "agent_activated", agentId = agentId, capability = capability)
    is AgentEvent.Progress -> ChatEvent(type = "progress", message = message)
    is AgentEvent.TextDelta -> ChatEvent(type = "text_delta", text = text)
    is AgentEvent.FileChanged -> ChatEvent(type = "file_changed", path = path, message = summary)
    is AgentEvent.ToolActivity ->
        ChatEvent(type = "tool_activity", tool = tool, message = label, status = status.name.lowercase())
    is AgentEvent.NoteUsed -> ChatEvent(type = "note_used", message = snippet)
    is AgentEvent.Result -> ChatEvent(type = "result", message = summary)
    is AgentEvent.Error -> ChatEvent(type = "error", message = message)
}
