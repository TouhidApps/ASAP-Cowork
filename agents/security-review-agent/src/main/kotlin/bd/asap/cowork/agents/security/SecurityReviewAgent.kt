package bd.asap.cowork.agents.security

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
import bd.asap.cowork.toolintegrations.SecurityReviewTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3 (Phase 2): the Security Review Agent — static analysis,
 * dependency/CVE scanning, secrets detection. Two genuinely new tools:
 * [bd.asap.cowork.toolintegrations.ScanForSecretsTool] (pattern-based,
 * not exhaustive) and
 * [bd.asap.cowork.toolintegrations.CheckDependencyVulnerabilitiesTool]
 * (a real query against the free OSV.dev database, verified live against
 * known-CVE packages before relying on it). Static analysis itself
 * defers to whatever linter a project already has configured — this
 * agent doesn't install one.
 */
class SecurityReviewAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "security-review-agent"
    override val capabilities: Set<Capability> = setOf(Capability.SECURITY)
    override val description: String =
        "Reviews the project for security issues: scans for accidentally committed secrets, checks dependencies against the OSV.dev vulnerability database, and runs whatever static analysis linter the project already has configured. Use for 'security review', 'check for vulnerabilities', or 'scan for secrets' requests. Not a substitute for a real security audit."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Security review agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Security Review Agent inside ASAP-Cowork. You have real tools to actually scan the project — use them, and quote what they found rather than asserting a finding you haven't seen evidence for in this session's tool output.")
            appendLine("Detected stack(s) here: ${context.detectedStacks.ifEmpty { setOf("none detected yet — inspect the workspace first") }}.")
            appendLine("1. Run scan_for_secrets first — it's fast and needs no setup. It's pattern-based and not exhaustive; say so when reporting a clean result, don't imply the project is provably secret-free.")
            appendLine("2. Check dependencies for known vulnerabilities: read the project's actual dependency file (run_terminal_command: \"cat build.gradle.kts\"/\"cat package.json\"/\"cat requirements.txt\"/\"cat pubspec.yaml\"/\"cat composer.json\" as relevant) and extract real {ecosystem, name, version} entries yourself, then pass them to check_dependency_vulnerabilities. Map stacks to ecosystems: Android/Kotlin/KMP/Spring Boot dependencies use \"Maven\" with name formatted as \"groupId:artifactId\"; Node/React Native use \"npm\"; Python uses \"PyPI\"; Flutter/Dart uses \"Pub\"; PHP/Composer uses \"Packagist\". There's no OSV.dev ecosystem for CocoaPods/Swift Package Manager — say so plainly rather than skipping the check silently for an iOS project's pod dependencies.")
            appendLine("3. For static analysis, check whether the project already has a linter configured (an .eslintrc, a detekt config, a requirements-dev.txt with bandit/flake8, etc.) via run_terminal_command, and run it if so (e.g. \"npx eslint .\", \"venv/bin/bandit -r .\"). Don't install a new linter into the user's project just for this review — report what's already there, or say none is configured.")
            appendLine("Summarize findings by severity, quoting the specific tool output that supports each one. Recommend concrete next steps (rotate a leaked credential, bump a vulnerable dependency to a fixed version) rather than vague advice. Always close by saying this review doesn't replace a real security audit for anything handling sensitive data.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = SecurityReviewTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, SecurityReviewTools.specs, executor, SecurityReviewTools::describe, images, history,
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
