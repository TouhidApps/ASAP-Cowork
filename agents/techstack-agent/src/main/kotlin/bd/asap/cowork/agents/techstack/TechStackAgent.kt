package bd.asap.cowork.agents.techstack

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
 * Third agent in the "from scratch" lifecycle (PLAN.md §3, roadmap #3):
 * recommends native vs. cross-platform, specific frameworks/libraries, and a
 * backend stack given the requirements and architecture decided upstream.
 * Its output is what the Project Scaffolding Agent scaffolds against.
 */
class TechStackAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "techstack-agent"
    override val capabilities: Set<Capability> = setOf(Capability.TECH_STACK)
    override val description: String =
        "Recommends native vs. cross-platform, specific frameworks/libraries, and a backend stack given requirements and architecture. Use for 'which framework/library/backend should I use' questions."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Tech stack agent is thinking..."))

        val systemPrompt = buildString {
            appendLine("You are the Tech Stack Recommendation Agent inside ASAP-Cowork, an AI agent platform that supports Kotlin/Android, Swift/iOS, Kotlin Multiplatform, Flutter, and React Native.")
            appendLine("Given the user's requirements and/or architecture decisions, recommend:")
            appendLine("- Native vs. cross-platform, and which of the supported stacks (Android/Kotlin, iOS/Swift, KMP, Flutter, React Native) fits best, with a one-line reason")
            appendLine("- A backend stack if the app needs one (Spring Boot or Node), with a one-line reason")
            appendLine("- Key libraries/frameworks for networking, persistence, and UI appropriate to the chosen stack")
            appendLine("Only recommend from the platforms this system actually supports — do not suggest a stack with no corresponding development agent.")
            appendLine("Workspace: ${context.workspaceRoot}")
            appendLine("Detected stacks: ${context.detectedStacks.ifEmpty { setOf("none detected yet") }}")
            appendLine("Be concrete and concise — this is what the Scaffolding Agent will build against next.")
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
