package bd.asap.cowork.agents.landingpage

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
import bd.asap.cowork.toolintegrations.LandingPageTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3: the Landing Page Agent — a marketing/landing page, as one
 * self-contained static HTML file (inline CSS, minimal vanilla JS only if
 * truly needed) that needs no build step or server to view. Deliberately
 * reuses other agents' output where it exists rather than starting cold:
 * [bd.asap.cowork.agents.branding.BrandingAgent]'s brand-guide.md for
 * colors/fonts, device-agent-captured screenshots under
 * `.asap-screenshots/` for real product visuals instead of placeholders,
 * and [bd.asap.cowork.agents.legal.LegalDocAgent]'s generated docs for the
 * footer's legal links.
 */
class LandingPageAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "landing-page-agent"
    override val capabilities: Set<Capability> = setOf(Capability.LANDING_PAGE)
    override val description: String =
        "Writes a self-contained marketing landing page (a single HTML file, inline CSS, no build step) for the app — hero section, features, screenshots if any exist, and a call to action. Use for 'build a landing page', 'make a website for this app', or 'create a marketing page' requests."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Landing page agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Landing Page Agent inside ASAP-Cowork. You have a real tool (run_terminal_command) to write files — use it to actually produce the page, not just describe one.")
            appendLine("Before writing anything, check the workspace for material to reuse rather than inventing from scratch: run_terminal_command (\"cat branding/brand-guide.md\") for an existing color palette/font pairing/logo to match, (\"find . -path '*/.asap-screenshots/*' -name '*.png'\") for real screenshots to showcase instead of placeholder images, and (\"ls legal/\") for existing terms-of-service.md/privacy-policy.md to link from the footer. Use whatever you find; don't block on any of it being missing.")
            appendLine("Gather (ask only if truly unclear, don't block on it): the app name, a one-line tagline, a short description, and 3-5 key features to highlight. Infer the name from the workspace if you can (settings.gradle.kts's rootProject.name, package.json's \"name\", pubspec.yaml's \"name\") rather than asking if it's already obvious.")
            appendLine("Write one self-contained file, landing-page/index.html: a hero section (name, tagline, a prominent call-to-action button), a features section (one card per feature), a screenshot showcase if you found real ones, and a footer (copyright line, links to the legal docs if they exist). Inline all CSS in a <style> block — no external stylesheet, no build step, no framework — and make it responsive with simple flexbox/grid and a couple of media queries, so it looks reasonable on both desktop and mobile.")
            appendLine("Report concisely what you produced and what you reused (brand guide, screenshots, legal links) versus made up — you don't need to restate the file contents verbatim, the user can open it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a design reference) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = LandingPageTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, LandingPageTools.specs, executor, LandingPageTools::describe, images, history,
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
