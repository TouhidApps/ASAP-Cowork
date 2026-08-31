package bd.asap.cowork.agents.generalpurpose

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
import bd.asap.cowork.toolintegrations.GeneralPurposeTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * Catch-all for a request that doesn't match any specialized agent's
 * capability — sending an email, editing an arbitrary XML/JSON/config file,
 * installing or uninstalling a package or app, or any other one-off system
 * task. Every specialized agent above this one in the roster narrows the
 * model's tool choices to exactly what that job needs; this one instead
 * hands the model the same [run_terminal_command][GeneralPurposeTools] escape
 * hatch every other agent already relies on, with no narrower framing, so it
 * never dead-ends a request just because no dedicated agent exists yet.
 */
class GeneralPurposeAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "general-purpose-agent"
    override val capabilities: Set<Capability> = setOf(Capability.GENERAL)
    override val description: String =
        "Catch-all for anything that doesn't fit one of the other capabilities above: sending an email, editing an arbitrary file (XML, JSON, YAML, config), installing or uninstalling a package or app, or running a one-off shell command. Route here only when no other capability actually matches the request."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("General-purpose agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the General-Purpose Agent inside ASAP-Cowork — the catch-all for requests that don't belong to any of the platform, build, or lifecycle agents.")
            appendLine("You have one real tool, run_terminal_command, which runs a shell command in the user's workspace. Use it to actually do the task (send an email via a CLI mailer, edit a file in place, install/uninstall a package, inspect the filesystem, etc.) rather than only describing what should happen.")
            appendLine("If the task needs a credential, tool, or piece of information you don't have (e.g. an SMTP account, an API key, which package manager to use), ask rather than guessing.")
            appendLine("Report concisely what you actually did — you don't need to restate command output verbatim, the user can see it.")
            appendLine("Workspace root: ${context.workspaceRoot}")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = GeneralPurposeTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, GeneralPurposeTools.specs, executor, GeneralPurposeTools::describe, images, history,
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
