package bd.asap.cowork.agents.reactnative

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
import bd.asap.cowork.toolintegrations.ReactNativeTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #9 (React Native alongside the other platform
 * agents per §8 phase 3): same real-tool-use structure as
 * [bd.asap.cowork.agents.android.AndroidAgent]. [ReactNativeTools.specs]'s
 * `create_react_native_project` and `manage_metro_bundler` are the only
 * React-Native-specific tools; the scaffolded `android/`/`ios/`
 * subprojects build and run exactly like plain Android/iOS projects, so
 * this agent reuses android-agent's and ios-agent's existing
 * Gradle/xcodebuild/emulator/simulator/launch/screenshot/video tools.
 * Every tool is dispatched through `build-runner`, so this process never
 * invokes Gradle, xcodebuild, adb, xcrun, or Metro itself.
 */
class ReactNativeAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "react-native-agent"
    override val capabilities: Set<Capability> = setOf(Capability.REACT_NATIVE_BUILD)
    override val description: String =
        "Actually scaffolds/creates a new React Native project on disk (not just a plan) — also builds its Android/iOS native projects, boots an emulator or simulator, starts the Metro bundler, installs and launches the app, captures screenshots. Use for 'scaffold/create/set up' a React Native project, or any 'build/run/test' request against one."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("React Native agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the React Native Development Agent inside ASAP-Cowork. You have real tools to scaffold, build, run, and verify a React Native project — use them rather than just describing what you'd do.")
            appendLine("Typical flow for a new app on Android: create_react_native_project, then manage_metro_bundler (action=\"start\", directory the project name) — a debug build needs it running to fetch JS — then manage_emulator to boot a device, run_gradle (directory \"<project>/android\", task \"installDebug\") to build+install, launch_app with the applicationId, then capture_device_screenshot.")
            appendLine("On iOS: run_terminal_command (\"pod install\") inside <project>/ios first (needs CocoaPods — create_react_native_project doesn't run this itself), then manage_ios_simulator to boot a simulator, run_xcodebuild (directory \"<project>/ios\", scheme the project name) — its result reports the built .app path — then launch_ios_app with that appPath and the bundleId, then capture_ios_screenshot. Metro must be running here too.")
            appendLine("For changes to an existing project, edit files with run_terminal_command as needed, then rebuild and reverify the same way.")
            appendLine("Whenever the user asks to see the app, verify it visually, or asks for a screenshot, call capture_device_screenshot or capture_ios_screenshot directly depending on which platform is running. Whenever they ask for a video, recording, or screen capture, call record_device_video or record_ios_video directly — don't just describe what you would do, and don't ask for confirmation first.")
            appendLine("A screenshot taken right after launch usually just shows the splash screen — pass delaySeconds: 2-3 to wait for the real UI to render first, unless the user specifically wants to see the splash/loading screen, in which case capture immediately (omit delaySeconds).")
            appendLine("Report concisely what you did and what the result was — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a UI design reference or a bug screenshot) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = ReactNativeTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, ReactNativeTools.specs, executor, ReactNativeTools::describe, images, history,
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
