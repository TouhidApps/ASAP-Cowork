package bd.asap.cowork.agents.scaffolding

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
 * Phase 1's original proof-of-pipeline agent (chat-gateway ->
 * orchestrator-core -> agent-sdk contract -> llm-gateway -> streamed events
 * back to the client), kept on for stacks that don't have a real
 * scaffolding tool yet (iOS/Swift, KMP, Flutter, React Native) and for
 * early "what would scaffolding look like" planning before a stack is
 * chosen. For Kotlin/Android, [bd.asap.cowork.agents.android.AndroidAgent]
 * can actually create the project on disk — this agent explicitly defers
 * to it rather than just describing what scaffolding would involve.
 *
 * Reads the provider through [LlmProviderRegistry] rather than holding one
 * directly, so switching the active provider (e.g. from the admin panel)
 * takes effect on the very next task.
 */
class ScaffoldingAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "scaffolding-agent"
    override val capabilities: Set<Capability> = setOf(Capability.SCAFFOLDING)
    override val description: String =
        "Plans and describes a project's initial skeleton — does not write files itself. Use only for stacks with no real scaffolding tool yet (iOS/Swift, KMP, Flutter, React Native), or for 'what would scaffolding look like' planning before a stack is picked. For Kotlin/Android, prefer the Android agent instead — it actually creates the project."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Scaffolding agent is thinking..."))

        val systemPrompt = buildString {
            appendLine("You are the Scaffolding Agent inside ASAP-Cowork, an AI agent platform that takes a mobile app from idea to production.")
            appendLine("You only help the user plan and describe how to scaffold a new mobile app project — you cannot write files yourself.")
            appendLine("If the request is for a Kotlin/Android project, say so plainly and tell the user to ask to build/create the Android app instead — the Android agent can actually create it on disk, right now, in this same chat.")
            appendLine("For every other stack (iOS/Swift, KMP, Flutter, React Native), describe the skeleton concretely: the directory layout and the first few files, so the plan is actionable even though nothing is written yet.")
            appendLine("Every skeleton you describe must follow multi-module architecture (split by layer/feature into separate modules/packages, not one monolith) and Clean Architecture (domain layer has no outward dependencies; data/infrastructure and presentation/UI layers depend inward on domain, never the reverse).")
            appendLine("Workspace: ${context.workspaceRoot}")
            appendLine("Detected stacks: ${context.detectedStacks.ifEmpty { setOf("none detected yet") }}")
            appendLine("Be concise and concrete.")
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
