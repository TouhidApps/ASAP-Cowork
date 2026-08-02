package bd.asap.cowork.agents.performance

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
import bd.asap.cowork.toolintegrations.PerformanceTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3 (Phase 2): the Performance Optimization Agent — profiling,
 * startup time, memory/battery analysis. Introduces no new tools:
 * Android's real measurement commands (`adb shell am start -W`,
 * `dumpsys meminfo`, `dumpsys batterystats`) are just [TerminalTool]
 * calls with output this agent knows how to read — the value is the
 * domain knowledge of which commands answer which question, same as
 * [bd.asap.cowork.agents.storeasset.StoreAssetAgent] carrying store
 * dimension knowledge its tool doesn't have. iOS has no equivalent
 * CLI-scriptable profiling without Xcode's Instruments.app, which can't
 * be driven from here — this agent says so rather than guessing.
 */
class PerformanceAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "performance-agent"
    override val capabilities: Set<Capability> = setOf(Capability.PERFORMANCE)
    override val description: String =
        "Measures an Android app's startup time, memory, and battery usage on a real device/emulator via adb, and reports concrete numbers rather than generic advice. Use for 'how fast does this app start', 'check memory usage', or 'profile this app' requests. iOS profiling is much more limited here — real profiling needs Xcode's Instruments.app, which can't be scripted."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Performance agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Performance Optimization Agent inside ASAP-Cowork. You have real tools to actually measure performance on a device — use them and report real numbers, not generic advice like \"consider lazy loading.\"")
            appendLine("First get the app built, installed, and an emulator running if one isn't already (run_gradle to build+install, manage_emulator to boot, launch_app once to warm up). Then measure:")
            appendLine("- Startup time: run_terminal_command (\"adb shell am start -W -n <applicationId>/<launcher-activity>\") — read the TotalTime (cold/warm start, ms) and WaitTime fields in its output directly; don't estimate.")
            appendLine("- Memory: run_terminal_command (\"adb shell dumpsys meminfo <applicationId>\") — read the TOTAL PSS line (and the Java/Native/Graphics breakdown if relevant to what's being investigated).")
            appendLine("- Battery-relevant activity: run_terminal_command (\"adb shell dumpsys batterystats <applicationId>\") for wakelock/wakeup counts — this is heavier output, focus on what's actually elevated rather than dumping everything back.")
            appendLine("You need the app's launcher activity name for the startup command if it isn't obvious — run_terminal_command (\"adb shell cmd package resolve-activity --brief <applicationId>\") resolves it, or check the app's AndroidManifest.xml for the MAIN/LAUNCHER intent-filter.")
            appendLine("iOS has no equivalent CLI-scriptable profiling — real iOS performance work needs Xcode's Instruments.app (Time Profiler, Allocations), which can't be driven from this chat. Say so plainly if asked to profile an iOS app rather than guessing at numbers.")
            appendLine("Report the actual measured numbers, compare against reasonable rules of thumb where relevant (e.g. cold start under ~5s, PSS appropriate for what the app does), and suggest concrete next steps only where the numbers actually indicate a problem.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = PerformanceTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, PerformanceTools.specs, executor, PerformanceTools::describe, images, history,
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
