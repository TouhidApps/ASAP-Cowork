package bd.asap.cowork.agents.kmp

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
import bd.asap.cowork.toolintegrations.KmpTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #7: the KMP (Kotlin Multiplatform) Development
 * Agent — same real-tool-use structure as
 * [bd.asap.cowork.agents.android.AndroidAgent]. `create_kmp_project`
 * scaffolds a `shared` module with a real shared Compose Multiplatform UI,
 * an `androidApp` module, and a real `iosApp` Xcode project rendering that
 * same shared UI — so this agent's roster is the union of android-agent's
 * and ios-agent's tools (Gradle/emulator/launch/screenshot/video for
 * Android, xcodebuild/simulator/launch/screenshot/video for iOS), all
 * dispatched through `build-runner` like everything else — this process
 * never invokes Gradle, adb, or xcodebuild itself.
 */
class KmpAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "kmp-agent"
    override val capabilities: Set<Capability> = setOf(Capability.KMP_BUILD)
    override val description: String =
        "Actually scaffolds/creates a new Kotlin Multiplatform + Compose Multiplatform project on disk (not just a plan) — a shared module with a real shared UI (not just shared logic), a runnable Android app module, and a runnable iOS app (Xcode project included) rendering that same shared UI. Builds/runs/verifies both. Use for 'scaffold/create/set up' a KMP / Kotlin Multiplatform / Compose Multiplatform project, or any 'build/run/test' request against one."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("KMP agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the KMP (Kotlin Multiplatform) + Compose Multiplatform Development Agent inside ASAP-Cowork. You have real tools to scaffold, build, run, and verify both the Android and iOS sides of a KMP project — use them rather than just describing what you'd do.")
            appendLine("Typical flow for a new project's Android side: create_kmp_project, then run_gradle (directory the project name, task \"androidApp:assembleDebug\") to build it, then manage_emulator to boot a device, run_gradle (task \"androidApp:installDebug\") to install, launch_app with the applicationId to actually start it, and capture_device_screenshot to verify it rendered.")
            appendLine("Typical flow for the same project's iOS side: manage_ios_simulator (action=\"start\") to boot a simulator FIRST (run_xcodebuild falls back to an unbuildable \"generic\" destination otherwise), then run_xcodebuild (directory \"<project>/iosApp\", scheme \"iosApp\") — its first build runs a Gradle script phase that compiles and embeds the shared framework, so it can take a while — then launch_ios_app with the built .app path and bundle id, then capture_ios_screenshot. Both Android and iOS render the exact same shared @Composable UI from the `shared` module.")
            appendLine("For changes to an existing project, edit files with run_terminal_command as needed, then rebuild and reverify the same way — a change to shared/src/commonMain affects both platforms.")
            appendLine("Whenever the user asks to see the app, verify it visually, or asks for a screenshot, call capture_device_screenshot or capture_ios_screenshot directly depending on which platform is running. Whenever they ask for a video, recording, or screen capture, call record_device_video or record_ios_video directly — don't just describe what you would do, and don't ask for confirmation first.")
            appendLine("A screenshot taken right after launch usually just shows the splash screen — pass delaySeconds: 2-3 to wait for the real UI to render first, unless the user specifically wants to see the splash/loading screen, in which case capture immediately (omit delaySeconds).")
            appendLine("Report concisely what you did and what the result was — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a UI design reference or a bug screenshot) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = KmpTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(systemPrompt, task.input, KmpTools.specs, executor, KmpTools::describe, images, history)
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
