package bd.asap.cowork.agentsdk

/**
 * Stream of events an agent emits while executing a [Task]. The orchestrator
 * forwards these to chat-gateway as they occur so the UI shows live progress
 * instead of waiting for a final blob.
 */
sealed interface AgentEvent {
    /** Emitted once routing decides which agent will handle the task, before that agent's first event — lets the UI show who's working on it (PLAN.md §6). */
    data class AgentActivated(val agentId: String, val capability: String) : AgentEvent

    /** A short, human-readable status update ("Reading project structure...") . */
    data class Progress(val message: String) : AgentEvent

    /** A tool call (e.g. run_gradle, manage_emulator) starting, progressing, finishing, or failing mid-reply — lets the UI show a live "Building APK...", "Starting emulator..." activity line instead of going quiet until the next chunk of text. */
    data class ToolActivity(val tool: String, val label: String, val status: ToolActivityStatus) : AgentEvent

    /** One chunk of streamed model output. Concatenate in order for the full text. */
    data class TextDelta(val text: String) : AgentEvent

    /** A file the agent created or modified. */
    data class FileChanged(val path: String, val summary: String) : AgentEvent

    /** Terminal success event — exactly one per task, always last on success. */
    data class Result(val summary: String) : AgentEvent

    /** Terminal failure event — exactly one per task, always last on failure. */
    data class Error(val message: String) : AgentEvent
}

enum class ToolActivityStatus { STARTED, FINISHED, FAILED }
