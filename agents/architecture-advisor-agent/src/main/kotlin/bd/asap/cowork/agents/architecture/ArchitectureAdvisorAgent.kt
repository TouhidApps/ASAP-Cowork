package bd.asap.cowork.agents.architecture

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
 * Second agent in the "from scratch" lifecycle (PLAN.md §3, roadmap #2):
 * decides layering and patterns (e.g. MVVM, Clean Architecture) and a
 * modularization strategy given the requirements produced upstream.
 */
class ArchitectureAdvisorAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "architecture-advisor-agent"
    override val capabilities: Set<Capability> = setOf(Capability.ARCHITECTURE)
    override val description: String =
        "Chooses layering/patterns (MVVM, Clean Architecture) and modularization strategy given requirements. Use for 'how should this be structured' questions."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Architecture advisor is thinking..."))

        val systemPrompt = buildString {
            appendLine("You are the Architecture Advisor Agent inside ASAP-Cowork, an AI agent platform that takes a mobile app from idea to production.")
            appendLine("Given the user's requirements (or a description of the app), recommend:")
            appendLine("- An architectural pattern (e.g. MVVM, Clean Architecture, MVI) with a one-line reason it fits this app")
            appendLine("- A layering breakdown (presentation / domain / data, or whatever fits the pattern chosen)")
            appendLine("- A modularization strategy (single module vs. feature modules vs. a shared KMP core) appropriate to the app's expected size and platform targets")
            appendLine("- Any notable tradeoffs of the recommendation")
            appendLine("Workspace: ${context.workspaceRoot}")
            appendLine("Detected stacks: ${context.detectedStacks.ifEmpty { setOf("none detected yet") }}")
            appendLine("Be concrete and concise — this is read by the Tech Stack Recommendation agent next.")
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
