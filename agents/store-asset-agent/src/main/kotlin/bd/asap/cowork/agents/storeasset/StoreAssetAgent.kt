package bd.asap.cowork.agents.storeasset

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
import bd.asap.cowork.toolintegrations.GenerateStoreImageTool
import bd.asap.cowork.toolintegrations.StoreAssetTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3: the Store Asset Agent — converts raw screenshots into
 * Play Console/App Store–ready images. [GenerateStoreImageTool] is
 * dimension-agnostic on purpose (exact store requirements drift over
 * time); this agent's system prompt carries the commonly-required
 * dimensions as of recent guidelines and says plainly to confirm current
 * exact numbers against the actual upload screen before finalizing.
 */
class StoreAssetAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "store-asset-agent"
    override val capabilities: Set<Capability> = setOf(Capability.STORE_ASSETS)
    override val description: String =
        "Converts existing screenshots (e.g. ones already captured under .asap-screenshots/) into Play Console/App Store–ready images — scaled and centered on a background at the exact required dimensions, with an optional caption. Use for 'prepare store screenshots', 'make a feature graphic', or 'resize these for the App Store' requests."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Store asset agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Store Asset Agent inside ASAP-Cowork. You have real tools to find existing screenshots and composite them into store-ready images — use them rather than just describing what the output should look like.")
            appendLine("First find source material: run_terminal_command (\"find . -path '*/.asap-screenshots/*' -name '*.png'\" or similar) for screenshots the Android/iOS agents already captured, rather than asking the user to supply new ones if some already exist. Also check branding/brand-guide.md for a color palette to use as the background instead of guessing colors.")
            appendLine("Use generate_store_image for the actual compositing — it scales the source screenshot, centers it with rounded corners on a solid or gradient background canvas at the exact width/height you specify, with an optional caption. It does not know target dimensions itself — that's your job. Commonly required dimensions as of recent guidelines (confirm current exact numbers against the actual Play Console/App Store Connect upload screen before finalizing — these do drift over time):")
            appendLine("- Play Store phone screenshot: e.g. 1080x1920 (portrait) or 1920x1080 (landscape); min 320px/max 3840px on a side, aspect ratio between 16:9 and 9:16.")
            appendLine("- Play Store feature graphic: exactly 1024x500.")
            appendLine("- Play Store hi-res icon: exactly 512x512, fully opaque (no transparency).")
            appendLine("- App Store iPhone screenshot (6.9\"/6.7\" display class): e.g. 1290x2796.")
            appendLine("- App Store iPad Pro 12.9\" screenshot: e.g. 2048x2732.")
            appendLine("- App Store icon: exactly 1024x1024, fully opaque — Apple applies its own corner mask, don't round the corners yourself.")
            appendLine("Write outputs to a clearly named subdirectory, e.g. store-assets/play-store/ or store-assets/app-store/, with filenames describing what each one is (e.g. phone-screenshot-1.png, feature-graphic.png).")
            appendLine("Report concisely what you produced and their exact dimensions — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a screenshot to use directly) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = StoreAssetTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, StoreAssetTools.specs, executor, StoreAssetTools::describe, images, history,
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
