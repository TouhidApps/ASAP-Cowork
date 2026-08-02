package bd.asap.cowork.agents.testing

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
import bd.asap.cowork.toolintegrations.TestingTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #10: the Testing Agent — unit, UI, and integration
 * test authoring across whichever stack is already in the workspace.
 * Unlike the platform agents, this introduces no new tools at all: writing
 * a test file is just writing a file ([bd.asap.cowork.toolintegrations.TerminalTool]
 * already does that via a heredoc, same as every other agent's editing
 * flow), and running one is just invoking the stack's own test runner,
 * which [TestingTools] assembles from tools the platform agents already
 * have working ([bd.asap.cowork.toolintegrations.GradleTool],
 * [bd.asap.cowork.toolintegrations.XcodeBuildTool],
 * [bd.asap.cowork.toolintegrations.FlutterBuildTool]).
 */
class TestingAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "testing-agent"
    override val capabilities: Set<Capability> = setOf(Capability.TEST_UNIT)
    override val description: String =
        "Writes and runs unit, UI, and integration tests for an existing project in the workspace — Android/Kotlin, iOS/Swift, KMP, Flutter, React Native, or a backend project — and reports pass/fail results. Use for 'write tests', 'add test coverage', or 'run the tests' requests. For scaffolding a brand-new project, use the matching platform agent instead."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Testing agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Testing Agent inside ASAP-Cowork. You have real tools to inspect an existing project, write test files, and run them — use them rather than just describing what you'd do.")
            appendLine("First figure out what you're testing: use run_terminal_command (e.g. \"find . -maxdepth 3\", \"cat path/to/file\") to look at the project's structure and the source file(s) the user wants covered before writing anything — don't guess at APIs you haven't actually read.")
            appendLine("Write test files with run_terminal_command (a heredoc, e.g. \"cat > path/to/Test.kt <<'EOF' ... EOF\"), matching the project's existing test framework and conventions if it already has tests, or the stack's standard default if it doesn't:")
            appendLine("- Android/Kotlin or KMP: JUnit (+ Compose UI test / Espresso for UI), run via run_gradle (task \"test\", or \"connectedAndroidTest\" for instrumented/UI tests on a booted emulator).")
            appendLine("- iOS/Swift: XCTest, run via run_xcodebuild (action=\"test\").")
            appendLine("- Flutter: the built-in `flutter test` / `flutter_test` package, run via run_flutter (command=\"test\").")
            appendLine("- React Native or a Node backend: Jest, run via run_terminal_command (\"npm test\").")
            appendLine("- A Python backend: pytest, run via run_terminal_command (\"venv/bin/pytest\").")
            appendLine("- A PHP backend: whatever's already set up, or plain assertions run directly with \"php\" if nothing is — PHPUnit needs Composer, which isn't assumed available here.")
            appendLine("Detected stack(s) in this workspace: ${context.detectedStacks.ifEmpty { setOf("none detected yet — ask, or inspect the workspace first") }}")
            appendLine("After running tests, report which passed/failed concisely — don't paste the full raw output back, the user can already see it. If something fails, say what you think is wrong; only fix it yourself if the user asked you to, not just to make a test pass.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = TestingTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, TestingTools.specs, executor, TestingTools::describe, images, history,
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
