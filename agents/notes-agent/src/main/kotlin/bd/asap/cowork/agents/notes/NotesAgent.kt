package bd.asap.cowork.agents.notes

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.ProjectContextView
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.agentsdk.ToolActivityStatus as AgentToolActivityStatus
import bd.asap.cowork.contextstore.NoteRepository
import bd.asap.cowork.llmgateway.AgentStreamEvent
import bd.asap.cowork.llmgateway.ChatMessage
import bd.asap.cowork.llmgateway.ChatRole
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import bd.asap.cowork.llmgateway.ToolActivityStatus
import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reads and writes the user's personal notes scratchpad (context-store's
 * [NoteRepository], same data behind the Notes tab) for whatever a request
 * references from it — a note's content is arbitrary text, so this could be
 * a password or API key, but just as easily a paragraph of copy, a
 * requirements list, or any other text the user jotted down. Only ever runs
 * when a message actually references notes: either the intent classifier
 * routes straight here for a standalone "what does my note say"/"add a note"
 * request, or Orchestrator.route runs it as a read-only pre-step (see its
 * `withNotesContext`) when another task's wording implies something saved is
 * needed — a completely unrelated message never touches the notes table.
 *
 * Direct calls (task.metadata["readOnly"] unset) get both tools and reply
 * either with just the matched content (search) or a short confirmation
 * (add), matching how the request phrased itself. Orchestrator's pre-step
 * passes `metadata = mapOf("readOnly" to "true")`, which withholds add_note
 * entirely — that call folds a *search* result into another task's input, so
 * it must never be able to create a note as a side effect of unrelated
 * wording that happens to contain "note".
 */
class NotesAgent(
    private val providers: LlmProviderRegistry,
    private val notes: NoteRepository,
) : Agent {
    override val id: String = "notes-agent"
    override val capabilities: Set<Capability> = setOf(Capability.NOTES)
    override val description: String =
        "Reads and writes the user's saved notes/scratchpad — this can be anything they wrote there: a password or API key, but equally a paragraph of text, requirements, copy, or any other saved note. Use for 'check/find/read/use what's in my notes' requests, or 'add/save/remember/jot down/write down ... as a note' requests, or whenever a request references something the user says is saved in their notes."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        val readOnly = task.metadata["readOnly"] == "true"
        val executor = ToolExecutor { name, input, _ ->
            when (name) {
                SEARCH_TOOL -> {
                    val query = (input["query"] as? String).orEmpty()
                    val matches = notes.list().filter { it.content.contains(query, ignoreCase = true) }
                    if (matches.isEmpty()) {
                        ToolResult("No notes match \"$query\".")
                    } else {
                        ToolResult(matches.joinToString("\n---\n") { it.content })
                    }
                }
                ADD_TOOL -> {
                    if (readOnly) {
                        ToolResult("add_note isn't available for this lookup.", isError = true)
                    } else {
                        val content = (input["content"] as? String).orEmpty()
                        if (content.isBlank()) {
                            ToolResult("content can't be empty.", isError = true)
                        } else {
                            notes.create(content)
                            ToolResult("Saved.")
                        }
                    }
                }
                else -> ToolResult("Unknown tool: $name", isError = true)
            }
        }

        val tools = if (readOnly) listOf(searchNotesSpec) else listOf(searchNotesSpec, addNoteSpec)
        val history = task.history.map { it.toChatMessage() }
        val reply = StringBuilder()

        providers.current()
            .runAgenticLoop(SYSTEM_PROMPT, task.input, tools, executor, { _, _ -> "Working with your notes" }, history = history)
            .collect { event ->
                when (event) {
                    is AgentStreamEvent.TextDelta -> {
                        reply.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is AgentStreamEvent.ToolActivity ->
                        emit(AgentEvent.ToolActivity(event.tool, event.label, event.status.toAgentEventStatus()))
                }
            }

        // Unlike other agents' generic "Done.", the Result here carries the
        // actual matched text — Orchestrator's pre-step reads it back out of
        // this event to fold into the next agent's task. Harmless when this
        // agent is the direct/only responder since Result.summary isn't
        // rendered in chat (the reply was already shown via TextDelta above).
        emit(AgentEvent.Result(reply.toString().trim()))
    }

    private fun ToolActivityStatus.toAgentEventStatus(): AgentToolActivityStatus = when (this) {
        ToolActivityStatus.STARTED -> AgentToolActivityStatus.STARTED
        ToolActivityStatus.FINISHED -> AgentToolActivityStatus.FINISHED
        ToolActivityStatus.FAILED -> AgentToolActivityStatus.FAILED
    }

    private companion object {
        const val SEARCH_TOOL = "search_notes"
        const val ADD_TOOL = "add_note"

        val SYSTEM_PROMPT = buildString {
            appendLine("You are the Notes agent inside ASAP-Cowork, managing the user's personal notes scratchpad, which can hold anything they've saved — credentials, paragraphs of text, requirements, copy, config values, or anything else.")
            appendLine("search_notes searches existing notes for text containing a query. Call it with keywords drawn from what the request is actually asking for — a credential name, a topic, distinctive words from a remembered paragraph, whatever points at the right note.")
            appendLine("If the request is vague (e.g. \"use that paragraph from my notes\"), try a broad or empty query first to see everything, then judge from the results which note it means.")
            appendLine("add_note (when offered) saves a new note. Call it when the request asks to add/save/remember/jot down/write down/note something — pass the exact text to save, not a paraphrase of the request itself (e.g. for \"note down the wifi password is hunter2\", save \"wifi password is hunter2\", not the whole sentence).")
            appendLine("After a search: reply with ONLY the exact matched note content, verbatim as stored, and nothing else — don't summarize or paraphrase it. If nothing matches, reply exactly: No matching note found.")
            appendLine("After an add: reply with a short confirmation only (e.g. \"Saved.\") — don't repeat the saved content back.")
        }

        val searchNotesSpec = ToolSpec(
            name = SEARCH_TOOL,
            description = "Search the user's saved notes for text containing the given query (case-insensitive substring match). Pass an empty query to list every note. Returns matching note contents verbatim.",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "Keyword or phrase to search for in note contents (e.g. \"play console password\", \"onboarding copy\"), or empty to list all notes.",
                    ),
                ),
                "required" to listOf("query"),
            ),
        )

        val addNoteSpec = ToolSpec(
            name = ADD_TOOL,
            description = "Save a new note to the user's personal notes scratchpad. Returns a confirmation once saved.",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "content" to mapOf(
                        "type" to "string",
                        "description" to "The exact text to save as a new note.",
                    ),
                ),
                "required" to listOf("content"),
            ),
        )
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
