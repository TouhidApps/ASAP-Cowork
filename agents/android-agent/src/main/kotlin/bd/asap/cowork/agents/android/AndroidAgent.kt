package bd.asap.cowork.agents.android

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
import bd.asap.cowork.toolintegrations.AndroidTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * Fifth agent in the roadmap (PLAN.md §3, roadmap #5): a real Android
 * development agent, not a text generator. Runs a full agentic tool-use
 * loop (via [LlmProviderRegistry]'s [bd.asap.cowork.llmgateway.LlmProvider.runAgenticLoop])
 * against the tool roster in [AndroidTools] — scaffold a project, run
 * Gradle tasks, boot an emulator, capture a screenshot — all operating
 * directly on [ProjectContextView.workspaceRoot], the user's confirmed
 * project directory (see chat-gateway's workspace routes), never the
 * orchestrator's own source tree.
 */
class AndroidAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "android-agent"
    override val capabilities: Set<Capability> = setOf(Capability.ANDROID_BUILD)
    override val description: String =
        "Actually scaffolds/creates a new Kotlin/Android project on disk (not just a plan) — also runs Gradle tasks, boots an emulator, installs and launches the app, captures screenshots. Use for 'scaffold/create/set up' an Android/Kotlin project, or any 'build/run/test the Android app' request."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Android agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Android Development Agent inside ASAP-Cowork. You have real tools to scaffold, build, run, and verify a Kotlin/Android (Jetpack Compose) project — use them rather than just describing what you'd do.")
            appendLine("Typical flow for a new app: create_android_project, then run_gradle to build it, then manage_emulator to boot a device, run_gradle to install (task=\"installDebug\"), launch_app to actually start it, and capture_device_screenshot to verify it rendered.")
            appendLine("For changes to an existing project, edit files with run_terminal_command as needed, then rebuild and reverify the same way.")
            appendLine("Whenever the user asks to see the app, verify it visually, or asks for a screenshot, call capture_device_screenshot directly. Whenever they ask for a video, recording, or screen capture, call record_device_video directly — don't just describe what you would do, and don't ask for confirmation first.")
            appendLine("A screenshot taken right after launch_app usually just shows the splash screen — pass delaySeconds: 2-3 to capture_device_screenshot to wait for the real UI to render first, unless the user specifically wants to see the splash/loading screen, in which case capture immediately (omit delaySeconds).")
            appendLine("Report concisely what you did and what the result was — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a UI design reference or a bug screenshot) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = AndroidTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(systemPrompt, task.input, AndroidTools.specs, executor, AndroidTools::describe, images, history)
            .collect { event ->
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
