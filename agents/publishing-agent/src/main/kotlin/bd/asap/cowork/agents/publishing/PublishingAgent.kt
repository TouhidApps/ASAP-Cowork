package bd.asap.cowork.agents.publishing

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.ProjectContextView
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.agentsdk.ToolActivityStatus as AgentToolActivityStatus
import bd.asap.cowork.firebase.FirebaseDistributeTool
import bd.asap.cowork.llmgateway.AgentStreamEvent
import bd.asap.cowork.llmgateway.ChatMessage
import bd.asap.cowork.llmgateway.ChatRole
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import bd.asap.cowork.llmgateway.ToolActivityStatus
import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.toolintegrations.TerminalTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths

/**
 * Thirteenth agent in the roadmap (PLAN.md §3, roadmap #13): ships a built
 * app to a distribution channel. Currently just Firebase App Distribution
 * — Google Play Console upload is a future tool on the same roster. Kept
 * separate from [bd.asap.cowork.agents.android.AndroidAgent] (build/run/
 * debug) since shipping is its own lifecycle stage per PLAN.md §8's Ship
 * loop, and `distribute_apk` already builds internally before uploading,
 * so this agent needs no build-specific tools of its own.
 */
class PublishingAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "publishing-agent"
    override val capabilities: Set<Capability> = setOf(Capability.PUBLISH)
    override val description: String =
        "Builds and uploads an Android app to Firebase App Distribution. Use for 'upload/share/distribute/ship the app to testers' requests. Requires Firebase credentials configured in the admin panel's Settings tab first."

    private val tools = listOf(TerminalTool.spec, FirebaseDistributeTool.spec)

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Publishing agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Publishing Agent inside ASAP-Cowork. Your job is to ship a built Android app to testers via Firebase App Distribution.")
            appendLine("distribute_apk builds the project (if needed) and uploads it in one step — you don't need to build it yourself first.")
            appendLine("Use run_terminal_command only to inspect the project (e.g. confirm the directory) if you're not sure what to pass distribute_apk.")
            appendLine("If distribute_apk reports Firebase isn't configured, tell the user to set it up in the admin panel's Settings tab — you can't configure it yourself.")
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        // Wrapped in try/catch since this runs mid-stream inside the agentic
        // loop — an uncaught exception here would tear down the whole
        // streaming response instead of just failing one tool call (same
        // reasoning as tool-integrations' AndroidTools.executorFor).
        val executor = ToolExecutor { name, input, onProgress ->
            try {
                when (name) {
                    TerminalTool.NAME -> TerminalTool.execute(workspaceRoot, input, onProgress)
                    FirebaseDistributeTool.NAME -> FirebaseDistributeTool.execute(workspaceRoot, input, onProgress)
                    else -> ToolResult("Unknown tool: $name", isError = true)
                }
            } catch (e: Exception) {
                ToolResult("Tool \"$name\" failed unexpectedly: ${e.message}", isError = true)
            }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(systemPrompt, task.input, tools, executor, ::describe, history = history).collect { event ->
            when (event) {
                is AgentStreamEvent.TextDelta -> emit(AgentEvent.TextDelta(event.text))
                is AgentStreamEvent.ToolActivity ->
                    emit(AgentEvent.ToolActivity(event.tool, event.label, event.status.toAgentEventStatus()))
            }
        }

        emit(AgentEvent.Result("Done."))
    }

    /** A short, human-readable label for a tool call in progress, shown live in the chat UI via AgentEvent.ToolActivity. */
    private fun describe(name: String, input: Map<String, Any?>): String = when (name) {
        TerminalTool.NAME -> "Running: ${(input["command"] as? String).orEmpty()}"
        FirebaseDistributeTool.NAME -> "Building APK and uploading to Firebase App Distribution"
        else -> name
    }

    private fun ToolActivityStatus.toAgentEventStatus(): AgentToolActivityStatus = when (this) {
        ToolActivityStatus.STARTED -> AgentToolActivityStatus.STARTED
        ToolActivityStatus.FINISHED -> AgentToolActivityStatus.FINISHED
        ToolActivityStatus.FAILED -> AgentToolActivityStatus.FAILED
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
