package bd.asap.cowork.agents.debugging

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
import bd.asap.cowork.toolintegrations.DebuggingTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #11: the Debugging Agent — runs/attaches to an
 * emulator or simulator, reads device logs, diagnoses a failure, and
 * proposes (or, if asked, applies) a fix. [DebuggingTools] introduces only
 * one new tool ([bd.asap.cowork.toolintegrations.ReadDeviceLogsTool], a
 * one-shot log snapshot for a tool-use loop, distinct from the frontend's
 * live-streaming Device Logs panel) — reproducing and diagnosing a bug
 * means doing what a developer would do by hand: rebuild, relaunch, look
 * at the screen, read the logs, all of which the platform agents' tools
 * already cover.
 */
class DebuggingAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "debugging-agent"
    override val capabilities: Set<Capability> = setOf(Capability.DEBUG)
    override val description: String =
        "Diagnoses a failing build, crash, or misbehaving app in an existing project — rebuilds it, boots an emulator/simulator, reads device logs, takes a screenshot, and reports what's actually wrong, not just a guess from reading code. Use for 'why is this crashing', 'debug this', or 'what does this error mean' requests. Can propose a fix, and apply one if asked to."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Debugging agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Debugging Agent inside ASAP-Cowork. You have real tools to reproduce a failure, inspect what's actually happening, and diagnose it — use them rather than guessing from reading code alone.")
            appendLine("Typical flow: reproduce the failure first (rebuild with run_gradle/run_xcodebuild/run_flutter and read the build error, or boot an emulator/simulator, install and launch the app, and see what happens), then read_device_logs (platform=\"android\" or \"ios\") for anything the build output didn't already explain, and capture_device_screenshot/capture_ios_screenshot if the symptom is visual.")
            appendLine("A screenshot taken right after launch usually just shows the splash screen — pass delaySeconds: 2-3 to wait for the real UI to render first, unless the splash screen itself is what's being debugged (e.g. \"stuck on splash\"), in which case capture immediately (omit delaySeconds).")
            appendLine("Quote the actual error/stack trace/log lines that support your diagnosis — don't assert a cause you haven't seen evidence for in this session's tool output.")
            appendLine("Only apply a fix (editing files with run_terminal_command, then rebuilding to confirm it's actually resolved) if the user asked you to fix it, not just diagnose it — otherwise propose the fix and let them decide.")
            appendLine("Report concisely what you found and what you did about it — you don't need to restate tool output verbatim, the user can see it.")
            appendLine("Detected stack(s) in this workspace: ${context.detectedStacks.ifEmpty { setOf("none detected yet — ask, or inspect the workspace first") }}")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message (e.g. a screenshot of the bug) — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = DebuggingTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, DebuggingTools.specs, executor, DebuggingTools::describe, images, history,
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
