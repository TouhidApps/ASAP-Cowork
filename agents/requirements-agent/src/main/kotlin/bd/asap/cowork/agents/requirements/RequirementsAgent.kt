package bd.asap.cowork.agents.requirements

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.ProjectContextView
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.agentsdk.ToolActivityStatus as AgentToolActivityStatus
import bd.asap.cowork.llmgateway.AgentStreamEvent
import bd.asap.cowork.llmgateway.ChatMessage
import bd.asap.cowork.llmgateway.ChatRole
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import bd.asap.cowork.llmgateway.ToolActivityStatus
import bd.asap.cowork.toolintegrations.RequirementsTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths

/**
 * First agent in the "from scratch" lifecycle (PLAN.md §3, roadmap #1):
 * turns a raw app idea into user stories, scope, and acceptance criteria.
 * Produces the requirements the Architecture Advisor and Tech Stack agents
 * reason from next. Writes the result to docs/requirements.md in the
 * project workspace (and edits that file in place on follow-up requests)
 * rather than only replying in chat, since it's meant to be a durable
 * artifact the later agents and the user both read back.
 */
class RequirementsAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "requirements-agent"
    override val capabilities: Set<Capability> = setOf(Capability.REQUIREMENTS)
    override val description: String =
        "Turns a raw app idea into user stories, scope, and acceptance criteria, and saves it as docs/requirements.md in the project. Use for new/vague app ideas, 'what should this app do' questions, or requests to update/revise the requirements doc."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Requirements agent is thinking..."))

        val systemPrompt = buildString {
            appendLine("You are the Requirements & Scope Agent inside ASAP-Cowork, an AI agent platform that takes a mobile app from idea to production.")
            appendLine("You have a real tool (run_terminal_command) to write files — use it to actually save the requirements document, not just describe it in chat.")
            appendLine("Turn the user's raw app idea into a concrete requirements document with:")
            appendLine("- A short problem statement and target user")
            appendLine("- A prioritized list of user stories (\"As a <user>, I want <goal>, so that <benefit>\")")
            appendLine("- Explicit in-scope and out-of-scope items for a first version")
            appendLine("- Acceptance criteria for the highest-priority stories")
            appendLine("Ask at most one clarifying question if the idea is too vague to scope; otherwise write the document directly.")
            appendLine("Write it to docs/requirements.md. If that file already exists and this request is a revision (e.g. \"add a story for X\", \"drop Y from scope\"), read it first and edit it in place rather than starting over.")
            appendLine("Be concrete and concise — this document is read by the Architecture Advisor and Tech Stack agents next.")
            appendLine("After writing, reply with a short summary of what you saved — you don't need to repeat the whole document back, the user can open the file.")
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = RequirementsTools.executorFor(workspaceRoot)
        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, RequirementsTools.specs, executor, RequirementsTools::describe, history = history,
        ).collect { event ->
            when (event) {
                is AgentStreamEvent.TextDelta -> emit(AgentEvent.TextDelta(event.text))
                is AgentStreamEvent.ToolActivity ->
                    emit(AgentEvent.ToolActivity(event.tool, event.label, event.status.toAgentEventStatus()))
            }
        }

        emit(AgentEvent.Result("Done."))
    }

    private fun ToolActivityStatus.toAgentEventStatus(): AgentToolActivityStatus = when (this) {
        ToolActivityStatus.STARTED -> AgentToolActivityStatus.STARTED
        ToolActivityStatus.FINISHED -> AgentToolActivityStatus.FINISHED
        ToolActivityStatus.FAILED -> AgentToolActivityStatus.FAILED
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
