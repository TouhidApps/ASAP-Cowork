package bd.asap.cowork.agents.requirements

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.ProjectContextView
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.llmgateway.ChatMessage
import bd.asap.cowork.llmgateway.ChatRole
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * First agent in the "from scratch" lifecycle (PLAN.md §3, roadmap #1):
 * turns a raw app idea into user stories, scope, and acceptance criteria.
 * Produces the requirements the Architecture Advisor and Tech Stack agents
 * reason from next.
 */
class RequirementsAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "requirements-agent"
    override val capabilities: Set<Capability> = setOf(Capability.REQUIREMENTS)
    override val description: String =
        "Turns a raw app idea into user stories, scope, and acceptance criteria. Use for new/vague app ideas or 'what should this app do' questions."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Requirements agent is thinking..."))

        val systemPrompt = buildString {
            appendLine("You are the Requirements & Scope Agent inside ASAP-Cowork, an AI agent platform that takes a mobile app from idea to production.")
            appendLine("Turn the user's raw app idea into a concrete requirements document:")
            appendLine("- A short problem statement and target user")
            appendLine("- A prioritized list of user stories (\"As a <user>, I want <goal>, so that <benefit>\")")
            appendLine("- Explicit in-scope and out-of-scope items for a first version")
            appendLine("- Acceptance criteria for the highest-priority stories")
            appendLine("Ask at most one clarifying question if the idea is too vague to scope; otherwise produce the document directly.")
            appendLine("Be concrete and concise — this document is read by the Architecture Advisor and Tech Stack agents next.")
        }

        val messages = task.history.map { it.toChatMessage() } + ChatMessage(ChatRole.USER, task.input)
        val fullReply = StringBuilder()
        providers.current().streamComplete(systemPrompt, messages)
            .collect { delta ->
                fullReply.append(delta)
                emit(AgentEvent.TextDelta(delta))
            }

        emit(AgentEvent.Result(fullReply.toString()))
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
