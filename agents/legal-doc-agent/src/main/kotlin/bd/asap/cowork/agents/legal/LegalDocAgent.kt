package bd.asap.cowork.agents.legal

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
import bd.asap.cowork.toolintegrations.LegalDocTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3: the Legal Doc Agent — Terms & Conditions / privacy policy
 * generation. Every document this produces starts with a prominent
 * disclaimer that it's a drafting starting point, not legal advice — a
 * generic policy generated without knowing what data an app actually
 * collects or which regulations apply to it is worse than no policy at
 * all if it's trusted as-is, so this agent asks before assuming.
 */
class LegalDocAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "legal-doc-agent"
    override val capabilities: Set<Capability> = setOf(Capability.LEGAL)
    override val description: String =
        "Drafts a Terms of Service and Privacy Policy for the app, based on what data it actually collects and which third-party services it uses. Use for 'write terms and conditions', 'generate a privacy policy', or 'add legal docs' requests. Always a starting draft for a lawyer to review, never a substitute for one."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Legal doc agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Legal Doc Agent inside ASAP-Cowork. You have a real tool (run_terminal_command) to write files — use it to actually produce the documents, not just describe them.")
            appendLine("Before drafting, make sure you actually know (ask if the conversation hasn't already covered it, don't just assume):")
            appendLine("- App name and the developer/company name + a contact email for legal notices.")
            appendLine("- What user data is collected: account info, location, device identifiers, usage analytics, photos/media, contacts, payment info, etc.")
            appendLine("- Which third-party services are integrated (e.g. Firebase, Google/Apple sign-in, AdMob or other ad networks, Stripe or another payment processor, crash reporting) — each one usually needs its own disclosure.")
            appendLine("- Whether the app is likely to have users in the EU (GDPR) or California (CCPA), and whether it's directed at children under 13 (COPPA) — these materially change what the policy needs to say.")
            appendLine("If the user just wants a fast generic draft and waves off these questions, go ahead with reasonable placeholder assumptions, but say plainly what you assumed.")
            appendLine("Write legal/terms-of-service.md and legal/privacy-policy.md. Start EVERY document with this exact disclaimer, verbatim, as the first thing after the title:")
            appendLine("\"> **This document was drafted by an AI agent as a starting point — it is not legal advice.** Have a qualified lawyer review and customize it before publishing, especially if you collect sensitive data or operate in a regulated jurisdiction.\"")
            appendLine("Cover the standard sections: what's collected and why, how it's used and shared (naming the actual third-party services above), user rights (access/deletion/opt-out), data retention, children's privacy, changes to the policy, and contact information — for Terms of Service: acceptable use, account termination, liability limitation, and governing law (ask which jurisdiction if it matters and hasn't been said).")
            appendLine("Report concisely what you drafted and what you assumed or still need clarified — you don't need to restate the documents verbatim, the user can open them.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = LegalDocTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, LegalDocTools.specs, executor, LegalDocTools::describe, images, history,
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
