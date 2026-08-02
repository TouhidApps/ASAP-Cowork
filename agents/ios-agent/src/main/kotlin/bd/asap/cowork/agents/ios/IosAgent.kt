package bd.asap.cowork.agents.ios

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
import bd.asap.cowork.toolintegrations.IosTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #6: the iOS Development Agent — same real-tool-use
 * structure as [bd.asap.cowork.agents.android.AndroidAgent], scaffolding
 * and building against Xcode/the Simulator instead of Gradle/the Android
 * emulator. Every tool in [IosTools] is dispatched through `build-runner`
 * (see that module), so this process never invokes xcodebuild/xcrun
 * itself.
 */
class IosAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "ios-agent"
    override val capabilities: Set<Capability> = setOf(Capability.IOS_BUILD)
    override val description: String =
        "Actually scaffolds/creates a new Swift/iOS (SwiftUI) project on disk (not just a plan) — also builds it with xcodebuild, boots the iOS Simulator, installs and launches the app, captures screenshots. Use for 'scaffold/create/set up' an iOS/Swift project, or any 'build/run/test the iOS app' request."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("iOS agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the iOS Development Agent inside ASAP-Cowork. You have real tools to scaffold, build, run, and verify a Swift/iOS (SwiftUI) project — use them rather than just describing what you'd do.")
            appendLine("Typical flow for a new app: create_ios_project, then manage_ios_simulator (action=\"start\") to boot a simulator, then run_xcodebuild (action=\"build\") — its result reports the built .app path — then launch_ios_app with that appPath and the project's bundleId, and capture_ios_screenshot to verify it rendered.")
            appendLine("For changes to an existing project, edit files with run_terminal_command as needed, then rebuild and reverify the same way.")
            appendLine("This only targets the iOS Simulator — never a physical device or a signed archive build.")
            appendLine("Whenever the user asks to see the app, verify it visually, or asks for a screenshot, call capture_ios_screenshot directly. Whenever they ask for a video, recording, or screen capture, call record_ios_video directly — don't just describe what you would do, and don't ask for confirmation first.")
            appendLine("A screenshot taken right after launch usually just shows the splash screen — pass delaySeconds: 2-3 to capture_ios_screenshot to wait for the real UI to render first, unless the user specifically wants to see the splash/loading screen, in which case capture immediately (omit delaySeconds).")
            appendLine("Report concisely what you did and what the result was — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a UI design reference or a bug screenshot) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = IosTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(systemPrompt, task.input, IosTools.specs, executor, IosTools::describe, images, history)
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
