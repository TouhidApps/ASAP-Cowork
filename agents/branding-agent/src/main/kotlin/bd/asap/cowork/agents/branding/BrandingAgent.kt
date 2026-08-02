package bd.asap.cowork.agents.branding

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
import bd.asap.cowork.toolintegrations.BrandingTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3: the Branding Agent — naming, visual identity, and a logo.
 * "Logo" here means a real, valid **SVG** wordmark/icon this agent writes
 * itself (SVG is XML text, well within what an LLM can produce correctly
 * and what this session's tools can verify), not AI-generated raster
 * artwork — this codebase has no image-generation API wired up anywhere
 * (Claude doesn't generate images; the OpenAI provider here only
 * implements chat, not its separate Images/DALL-E endpoint), and adding
 * one is a materially different, riskier scope than every other agent's
 * "real tool, no fragile external dependency" pattern.
 */
class BrandingAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "branding-agent"
    override val capabilities: Set<Capability> = setOf(Capability.BRANDING)
    override val description: String =
        "Names the app (if not already named), proposes a color palette and font pairing, and writes a real SVG wordmark + icon logo plus a brand guide to the workspace. Use for 'name this app', 'design a logo', or 'pick a color scheme' requests. Produces vector (SVG) logos, not AI-generated raster art — this platform has no image-generation API wired up."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Branding agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Branding Agent inside ASAP-Cowork. You have real tools to write files — use them to actually produce the brand kit, not just describe one.")
            appendLine("If the app doesn't have a settled name yet, propose 3 options (short, memorable, checkable-in-your-head for obvious trademark/domain collisions — you can't actually check availability, say so) and ask which one, or pick the strongest if the user just wants you to decide. If it's already named (check the workspace: settings.gradle.kts's rootProject.name, package.json's \"name\", or pubspec.yaml's \"name\"), use that.")
            appendLine("Propose a color palette: primary, secondary, accent, background, and text colors as hex codes, with a one-line rationale for the overall feel. Propose a font pairing (a heading font + a body font, both real Google Fonts names so they're actually usable).")
            appendLine("Write an SVG wordmark (the app name set in the chosen heading font via <text font-family=\"...\">, in the primary color, viewBox sized to the text) with write_brand_asset (filename \"logo-wordmark.svg\"), and a separate simple geometric icon mark (an abstract shape reflecting the app's theme — not literally the text) with write_brand_asset (filename \"icon.svg\"). Keep both valid, minimal SVG — no external references, they must render standalone.")
            appendLine("Write brand-guide.md the same way (write_brand_asset, filename \"brand-guide.md\") documenting: the chosen name and a one-line rationale, the color palette (hex + what each is for), the font pairing, and a short logo usage note (background this wordmark actually contrasts well against, minimum clear space).")
            appendLine("write_brand_asset's result includes the real URL the chat UI shows the file at — never write your own \"![...](...)\" image markdown or link for these files, the tool's own output already shows them; just reference the file by name in your reply.")
            appendLine("Note plainly that <text>-based SVG logos assume the chosen Google Font is available wherever they're rendered — for production use (app stores, print) they should be converted to outlines first; you can't do that conversion yourself.")
            appendLine("Report concisely what you produced — you don't need to restate file contents verbatim, the user can open them.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. inspiration/reference) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = BrandingTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, BrandingTools.specs, executor, BrandingTools::describe, images, history,
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
