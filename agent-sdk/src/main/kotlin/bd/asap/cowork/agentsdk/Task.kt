package bd.asap.cowork.agentsdk

import java.util.UUID

/**
 * A unit of work the orchestrator routes to whichever agent(s) declare
 * [capability]. [input] is the user-facing instruction; [metadata] carries
 * whatever structured context that specific capability needs (e.g. a target
 * platform, a file path) without agent-sdk needing to know every agent's shape.
 * [history] is every prior message in the same conversation (oldest first,
 * not including [input] itself) — an agent building an LLM call should send
 * this ahead of the new message so replies stay coherent turn to turn.
 * [attachments] are images the user attached to this turn specifically (e.g.
 * a UI design reference) — an agent whose provider supports vision should
 * send these alongside [input] so it can actually see what was attached,
 * instead of only getting told an image exists.
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val capability: Capability,
    val input: String,
    val metadata: Map<String, String> = emptyMap(),
    val history: List<ConversationTurn> = emptyList(),
    val attachments: List<TaskAttachment> = emptyList(),
)

/** One image attached to a [Task]'s turn. [path] is an absolute filesystem path — plain (not llm-gateway's provider-specific image type) for the same reason as [ConversationTurn]: agent-sdk must not depend on llm-gateway. */
data class TaskAttachment(val path: String, val mimeType: String)
