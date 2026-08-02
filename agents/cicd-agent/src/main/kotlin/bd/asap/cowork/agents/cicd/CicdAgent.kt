package bd.asap.cowork.agents.cicd

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
import bd.asap.cowork.toolintegrations.CicdTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #12: the CI/CD Build Agent — configures build
 * pipelines. Scoped to GitHub Actions specifically (ubiquitous, needs no
 * external service to author correctly, and matches how this very repo
 * would be hosted) rather than trying to support every CI provider.
 * Like [bd.asap.cowork.agents.testing.TestingAgent], this introduces no
 * new tools: a workflow file is just a file, and validating one means
 * running the exact same build/test commands it specifies, which
 * [CicdTools] assembles from tools the platform agents already have
 * working.
 */
class CicdAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "cicd-agent"
    override val capabilities: Set<Capability> = setOf(Capability.CICD)
    override val description: String =
        "Configures a GitHub Actions CI pipeline for an existing project — builds and tests on every push — and validates it by actually running the same steps locally before considering it done, not just writing plausible-looking YAML. Use for 'set up CI', 'add a GitHub Actions workflow', or 'automate the build' requests."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("CI/CD agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the CI/CD Build Agent inside ASAP-Cowork. You configure GitHub Actions workflows — the one CI provider this agent supports, since it needs no external account/service to author correctly — and you have real tools to validate one before calling the job done.")
            appendLine("First inspect the actual project (run_terminal_command: \"find . -maxdepth 2\", \"cat package.json\"/\"cat pubspec.yaml\"/\"cat requirements.txt\"/\"cat composer.json\" as relevant) rather than assuming a layout — detected stack(s) here: ${context.detectedStacks.ifEmpty { setOf("none detected yet — inspect the workspace") }}. Detection covers Android/iOS/KMP/Flutter/React Native and Spring Boot/Node backends; it does NOT recognize Python or PHP backends yet, so check for requirements.txt/composer.json yourself before ruling those out.")
            appendLine("Write the workflow with run_terminal_command as a heredoc to .github/workflows/ci.yml, triggered on push and pull_request. Map each detected stack to a job:")
            appendLine("- Android/KMP: actions/setup-java (temurin, matching the project's own toolchain) + ./gradlew build test (via ./gradlew wrapper, actions/checkout first).")
            appendLine("- iOS: runs-on: macos-latest, xcodebuild -scheme <scheme> -destination 'platform=iOS Simulator,name=iPhone 15' build test.")
            appendLine("- Flutter: subosito/flutter-action, then flutter pub get && flutter test && flutter build apk --debug.")
            appendLine("- React Native: actions/setup-node, npm ci, then a separate job (or step) for the android/ subfolder's own ./gradlew assembleDebug — iOS needs runs-on: macos-latest plus CocoaPods, skip it if the project doesn't already have a Podfile.lock checked in.")
            appendLine("- Spring Boot backend: same as Android/KMP's Gradle job, pointed at that subdirectory.")
            appendLine("- Node/Express backend: actions/setup-node, npm ci, npm test.")
            appendLine("- Python/FastAPI backend: actions/setup-python, pip install -r requirements.txt, then pytest if tests exist.")
            appendLine("- PHP backend: shivammathur/setup-php, then whatever's already set up — don't assume Composer/PHPUnit are configured if there's no composer.json.")
            appendLine("After writing it, validate it — actually run the same build/test commands the workflow specifies (run_gradle/run_xcodebuild/run_flutter/run_terminal_command) right here, so you're reporting whether the pipeline would actually pass, not just that the YAML looks plausible. Say plainly which steps you validated and which you couldn't (e.g. no macOS runner available for an iOS job, or a required secret/credential isn't set here).")
            appendLine("Report concisely what you set up and what you validated — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = CicdTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, CicdTools.specs, executor, CicdTools::describe, images, history,
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
