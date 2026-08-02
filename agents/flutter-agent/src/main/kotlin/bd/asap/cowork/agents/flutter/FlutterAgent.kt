package bd.asap.cowork.agents.flutter

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
import bd.asap.cowork.toolintegrations.FlutterTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #8: the Flutter Development Agent — same real-tool-use
 * structure as [bd.asap.cowork.agents.android.AndroidAgent] and
 * [bd.asap.cowork.agents.ios.IosAgent]. Scaffolding and building are
 * Flutter-specific ([FlutterTools.specs]'s `create_flutter_project`/
 * `run_flutter`), but running/verifying the result reuses the exact same
 * Android emulator and iOS Simulator tools those two agents already have —
 * a Flutter app still ultimately runs on one of those two, so there's no
 * separate "Flutter emulator" to manage. Every tool is dispatched through
 * `build-runner` (see that module), so this process never invokes
 * flutter/Gradle/adb/xcodebuild/xcrun itself.
 */
class FlutterAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "flutter-agent"
    override val capabilities: Set<Capability> = setOf(Capability.FLUTTER_BUILD)
    override val description: String =
        "Actually scaffolds/creates a new Flutter project on disk (not just a plan) — also builds it, boots an Android emulator or iOS Simulator, installs and launches the app, captures screenshots. Use for 'scaffold/create/set up' a Flutter project, or any 'build/run/test the Flutter app' request."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Flutter agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Flutter Development Agent inside ASAP-Cowork. You have real tools to scaffold, build, run, and verify a Flutter project — use them rather than just describing what you'd do.")
            appendLine("Typical flow for a new app: create_flutter_project, then either:")
            appendLine("- Android: manage_emulator (action=\"start\") to boot a device, run_flutter (command=\"build apk --debug\"), run_flutter (command=\"install -d <deviceId>\") to install it, launch_app with the applicationId, then capture_device_screenshot.")
            appendLine("- iOS: manage_ios_simulator (action=\"start\") to boot a simulator, run_flutter (command=\"build ios --debug --simulator\") — its output reports the built .app path — then launch_ios_app with that appPath and the bundleId, then capture_ios_screenshot.")
            appendLine("For changes to an existing project, edit files with run_terminal_command as needed, then run_flutter (command=\"test\") or rebuild and reverify the same way.")
            appendLine("Whenever the user asks to see the app, verify it visually, or asks for a screenshot, call capture_device_screenshot or capture_ios_screenshot directly depending on which platform is running. Whenever they ask for a video, recording, or screen capture, call record_device_video or record_ios_video directly — don't just describe what you would do, and don't ask for confirmation first.")
            appendLine("A screenshot taken right after launch usually just shows the splash screen — pass delaySeconds: 2-3 to wait for the real UI to render first, unless the user specifically wants to see the splash/loading screen, in which case capture immediately (omit delaySeconds).")
            appendLine("Report concisely what you did and what the result was — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a UI design reference or a bug screenshot) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = FlutterTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(systemPrompt, task.input, FlutterTools.specs, executor, FlutterTools::describe, images, history)
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
