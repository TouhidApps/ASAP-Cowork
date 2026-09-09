package bd.asap.cowork.agents.imaging

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
import bd.asap.cowork.toolintegrations.ImagingTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * Generates and edits real raster images via a configured Image AI provider
 * (OpenAI or Gemini, chosen in the admin panel's Image AI section) — unlike
 * BrandingAgent's SVG-only logos, this agent calls an actual
 * image-generation API. Every image gets a permanent, descriptive filename
 * under images/ so a later chat message can reference it by name for
 * edit_image, and optimize_image shrinks/crops one down before it's
 * actually used as an app asset.
 */
class ImagingAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "imaging-agent"
    override val capabilities: Set<Capability> = setOf(Capability.IMAGING)
    override val description: String =
        "Generates and edits raster images (UI mockups, logos, backgrounds, icon concepts) from a text prompt via a real image-generation API (OpenAI or Gemini), and can shrink/crop one down for use as an app asset. Use for 'generate an image/logo/background', 'edit that image', or 'make this a small app icon' requests. Requires an OpenAI or Gemini image API key configured in the admin panel's Image AI section."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Imaging agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Imaging Agent inside ASAP-Cowork. You have real tools to generate and edit raster images via an actual image-generation API — use them rather than just describing an image.")
            appendLine("Before your first generate_image/edit_image/optimize_image call, figure out which scaffolded project this is for: check the workspace for a subdirectory containing settings.gradle.kts, package.json, pubspec.yaml, or project.yml (run_terminal_command with \"ls\" at the workspace root, then check inside if unclear). Pass that subdirectory's name as \"directory\" on every one of those calls, so this project's images never mix with another project's in the same workspace — leave \"directory\" blank only if nothing has been scaffolded yet.")
            appendLine("generate_image creates a brand-new image from a text prompt and saves it under a descriptive, kebab-case filename you choose (e.g. \"login-screen-mockup\", \"app-icon-concept\", \"hero-background\") — that name is permanent: the user can reference it later by name to have it revised.")
            appendLine("If the user is asking to change/revise/tweak something that already exists (e.g. \"make the background blue\", referencing an image by name or by what you just made), use edit_image with that exact filename, the same \"directory\" you used to generate it, and the instruction — never call generate_image again for a revision, you'd lose the name continuity the user is relying on.")
            appendLine("Before telling the user an image is ready to actually use as an app asset (an icon, a button image, a small in-app image), always call optimize_image on it first — it shrinks it to a reasonable size (and can center-crop to a square for icons) so it isn't dropped into a coding project oversized. Don't claim an image is \"ready to use\" until you've done this.")
            appendLine("run_terminal_command (e.g. \"ls <project>/images/\") lets you check what images already exist before deciding whether to generate or edit.")
            appendLine("generate_image/edit_image/optimize_image's results include the real URL the chat UI shows the image at — never construct that URL yourself, always rely on what the tool returns.")
            appendLine("If a tool reports Image AI isn't configured, tell the user to add an OpenAI or Gemini image API key in the admin panel's Image AI section — you can't configure it yourself.")
            appendLine("Report concisely what you produced and its filename — you don't need to restate tool output verbatim, the user can see the image.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a reference to match the style of) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = ImagingTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, ImagingTools.specs, executor, ImagingTools::describe, images, history,
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
