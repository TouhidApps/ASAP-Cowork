package bd.asap.cowork.agents.documentation

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
import bd.asap.cowork.llmgateway.ImageAttachment
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import bd.asap.cowork.llmgateway.ToolActivityStatus
import bd.asap.cowork.toolintegrations.DocumentationTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3 (Phase 2): the Documentation Agent — generates/maintains a
 * README and architecture docs. Introduces no new tools: reading the
 * actual codebase and writing Markdown are both things [TerminalTool]
 * already does. The whole value here is *accuracy* — real dependency
 * versions and real module structure read from the project, not generic
 * boilerplate — so this agent's system prompt insists on inspecting
 * before writing, same as [bd.asap.cowork.agents.testing.TestingAgent].
 */
class DocumentationAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "documentation-agent"
    override val capabilities: Set<Capability> = setOf(Capability.DOCUMENTATION)
    override val description: String =
        "Writes or updates a README and architecture docs for the actual project in the workspace, based on what's really there — real module structure, real dependencies — not generic boilerplate. Use for 'write a README', 'document this project', or 'add architecture docs' requests."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Documentation agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Documentation Agent inside ASAP-Cowork. You have a real tool (run_terminal_command) to inspect the actual project and write files — use it, don't invent generic boilerplate.")
            appendLine("Before writing anything, actually look at the project: run_terminal_command (\"find . -maxdepth 3 -not -path '*/node_modules/*' -not -path '*/.git/*'\") for the real structure, then read the actual manifest/build files (build.gradle.kts, package.json, pubspec.yaml, requirements.txt, composer.json — whichever exist) for the real name, dependencies, and scripts/tasks. If there's already a README.md, read it first and preserve anything still accurate rather than blindly overwriting.")
            appendLine("Detected stack(s) here: ${context.detectedStacks.ifEmpty { setOf("none detected yet — inspect the workspace") }}.")
            appendLine("Write README.md covering: what the project is (infer from the code if there's no description anywhere), how to set it up and run it (the actual commands for this stack — e.g. ./gradlew build, npm install && npm start, flutter run — not generic placeholders), and the real project structure. If the project is non-trivial (multiple modules/services, or an agent/orchestrator-style architecture), also write ARCHITECTURE.md covering the major components and how they relate — only write this one if there's real architecture worth documenting, not for a single-file script.")
            appendLine("Report concisely what you wrote and what you found out about the project — you don't need to restate file contents verbatim, the user can open them.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = DocumentationTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, DocumentationTools.specs, executor, DocumentationTools::describe, images, history,
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
