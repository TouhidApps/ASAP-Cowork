package bd.asap.cowork.orchestrator

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.agentsdk.TaskAttachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The brain: routes a task to the agent(s) that declare its capability and
 * forwards their event stream. Phase 1 routes to a single matching agent;
 * multi-agent fan-out/aggregation lands when a second real agent exists.
 *
 * Any exception an agent's flow throws (a failed LLM call, a bad tool
 * invocation) is converted into a terminal [AgentEvent.Error] here rather
 * than propagating — agents don't need to remember to catch their own
 * infrastructure failures, and callers (chat-gateway's WebSocket handler)
 * never see an uncaught exception tear down the connection.
 */
class Orchestrator(
    private val registry: AgentRegistry,
    private val context: ProjectContext,
    private val classifier: IntentClassifier,
) {
    fun handle(task: Task): Flow<AgentEvent> {
        val candidates = registry.findFor(task)
        val agent = candidates.firstOrNull()
            ?: return flow { emit(AgentEvent.Error("No agent registered for capability '${task.capability.id}'")) }

        return agent.execute(task, context)
            .catch { throwable -> emit(AgentEvent.Error(throwable.message ?: throwable.toString())) }
    }

    /**
     * Entry point for a raw chat message with no capability attached yet —
     * classifies [input] against the registered roster, emits which agent
     * got picked, then hands off to [handle]. This is what makes routing
     * automatic instead of requiring the caller to name a `stage`.
     * [history] is the conversation so far (oldest first, not including
     * [input]) — passed through to both the classifier and the picked
     * agent's [Task] so replies stay coherent turn to turn instead of each
     * message being answered as if it were the start of a new conversation.
     */
    fun route(
        input: String,
        metadata: Map<String, String> = emptyMap(),
        history: List<ConversationTurn> = emptyList(),
        attachments: List<TaskAttachment> = emptyList(),
    ): Flow<AgentEvent> = flow {
        val agents = registry.all()
        if (agents.isEmpty()) {
            emit(AgentEvent.Error("No agents registered"))
            return@flow
        }

        val capability = classifier.classify(input, agents, history)
        val resolvedInput = withNotesContext(input, capability, agents, history) { emit(it) }
        val task = Task(capability = capability, input = resolvedInput, metadata = metadata, history = history, attachments = attachments)
        registry.findFor(task).firstOrNull()?.let { agent ->
            emit(AgentEvent.AgentActivated(agentId = agent.id, capability = capability.id))
        }
        emitAll(handle(task))
    }.catch { throwable -> emit(AgentEvent.Error(throwable.message ?: throwable.toString())) }

    /**
     * If [input] plausibly references something saved in the user's notes —
     * a credential, but just as easily a paragraph of copy or any other
     * saved text — and the classifier didn't already route straight to
     * [Capability.NOTES], runs the notes agent as a hidden pre-step and folds
     * whatever it finds into the message the real target agent sees. E.g.
     * "upload using the Play Console password from my notes" resolves the
     * password before PublishingAgent sees the request; "use that paragraph
     * from my notes for the landing page" resolves the paragraph before
     * LandingPageAgent does — no changes needed to either agent (or any
     * other) itself. Only fires when [input] actually contains one of
     * [NOTES_TRIGGER_WORDS], so an unrelated message never touches the notes
     * table — this is what makes the lookup "only if the user asks" rather
     * than running on every turn.
     *
     * The pre-step's tool activity is forwarded via [emit] so the UI still
     * shows the search happening, but its text reply is deliberately not
     * forwarded — only the target agent's own reply should appear as the
     * visible chat message. When a match is actually folded in, an
     * [AgentEvent.NoteUsed] is also emitted so the UI can badge the reply as
     * having used a saved note.
     */
    private suspend fun withNotesContext(
        input: String,
        capability: Capability,
        agents: List<Agent>,
        history: List<ConversationTurn>,
        emit: suspend (AgentEvent) -> Unit,
    ): String {
        if (capability == Capability.NOTES) return input
        if (NOTES_TRIGGER_WORDS.none { input.contains(it, ignoreCase = true) }) return input
        val notesAgent = agents.firstOrNull { Capability.NOTES in it.capabilities } ?: return input

        val found = StringBuilder()
        // readOnly=true withholds notes-agent's add_note tool entirely — this
        // pre-step only ever folds a *search* result into another task's
        // input, and must never create a note as a side effect of unrelated
        // wording that happens to contain a trigger word like "note".
        notesAgent.execute(Task(capability = Capability.NOTES, input = input, history = history, metadata = mapOf("readOnly" to "true")), context)
            .catch { /* a failed notes lookup shouldn't block the actual task */ }
            .collect { event ->
                if (event is AgentEvent.ToolActivity) emit(event)
                if (event is AgentEvent.Result) found.append(event.summary)
            }

        return if (found.isBlank() || found.contains(NO_NOTE_MATCH, ignoreCase = true)) {
            input
        } else {
            emit(AgentEvent.NoteUsed(snippet = found.toString().trim().take(SNIPPET_LENGTH)))
            "$input\n\n[Found in your saved notes]\n$found"
        }
    }

    private companion object {
        val NOTES_TRIGGER_WORDS = listOf(
            "note", "notes", "password", "credential", "secret", "api key", "apikey", "token", "keystore",
        )
        const val NO_NOTE_MATCH = "no matching note"
        const val SNIPPET_LENGTH = 80
    }
}
