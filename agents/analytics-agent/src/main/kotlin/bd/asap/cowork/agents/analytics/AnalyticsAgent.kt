package bd.asap.cowork.agents.analytics

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
import bd.asap.cowork.toolintegrations.AnalyticsTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3 (Phase 2): the Analytics Agent — wires up analytics SDKs and
 * event tracking. Standardizes on Firebase Analytics since it covers every
 * platform stack this project supports with one vendor. Introduces no new
 * tools: adding a dependency and writing a wrapper class are edits
 * [TerminalTool] already makes. Deliberately cannot finish the job alone —
 * `google-services.json`/`GoogleService-Info.plist` are real per-project
 * credentials from the Firebase console this agent has no way to
 * fabricate, so it says so plainly rather than pretending the integration
 * is complete without them.
 */
class AnalyticsAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "analytics-agent"
    override val capabilities: Set<Capability> = setOf(Capability.ANALYTICS)
    override val description: String =
        "Wires up Firebase Analytics for the project — adds the SDK dependency, a thin logEvent wrapper, and a couple of instrumented example events. Use for 'add analytics', 'track events', or 'wire up Firebase Analytics' requests. Can't finish the setup alone — needs a real config file (google-services.json / GoogleService-Info.plist) from the Firebase console, which it can't generate itself."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Analytics agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Analytics Agent inside ASAP-Cowork. You have a real tool (run_terminal_command) to edit build files and write code — use it to actually wire things up, not just describe the steps.")
            appendLine("Detected stack(s) here: ${context.detectedStacks.ifEmpty { setOf("none detected yet — inspect the workspace first") }}. Inspect the actual project (find/cat the build file) before editing it.")
            appendLine("Per stack, add Firebase Analytics:")
            appendLine("- Android/KMP: add the Google Services Gradle plugin (id(\"com.google.gms.google-services\") apply false at the root, applied in the app module) and implementation(platform(\"com.google.firebase:firebase-bom:<latest>\")) + implementation(\"com.google.firebase:firebase-analytics\") to the app module — check Firebase's own docs or Maven Central for the current BOM/plugin version rather than assuming one, these move often. Needs google-services.json placed in the app module's directory — you cannot generate this file; it's downloaded from the Firebase console for a real Firebase project.")
            appendLine("- iOS: add the Firebase/Analytics CocoaPods pod (or Firebase Swift Package via Xcode's package list) and call FirebaseApp.configure() in the app delegate / App init. Needs GoogleService-Info.plist added to the Xcode project — same limitation, you cannot generate this file.")
            appendLine("- Flutter: add firebase_core and firebase_analytics to pubspec.yaml, call Firebase.initializeApp() before runApp(). Needs both platform config files above, plus running flutterfire configure normally — which needs a real Firebase CLI login you don't have here, so just tell the user to run it themselves.")
            appendLine("- React Native: add @react-native-firebase/app and @react-native-firebase/analytics. Same platform config file requirements as the plain Android/iOS cases, once per platform folder.")
            appendLine("- A backend project: Firebase Analytics is a client-app product, not meaningful server-side — if asked to add \"analytics\" to a backend, suggest structured event logging (a logEvent(name, properties) helper writing to stdout/a log file) instead, and say so.")
            appendLine("Regardless of stack, write a small wrapper (e.g. Analytics.kt / Analytics.swift / analytics.dart / analytics.js exposing a logEvent(name, params) function) rather than having call sites depend on the Firebase API directly, and instrument 2-3 obviously meaningful example events (e.g. app open, a primary button tap) so the pattern is clear.")
            appendLine("Always end by stating plainly and specifically which config file(s) the user still needs to obtain from the Firebase console and where to place them — the integration will not compile/run without them, and you have no way to create them yourself.")
            appendLine("Report concisely what you wired up and what's still needed from the user — you don't need to restate file contents verbatim, the user can open them.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = AnalyticsTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, AnalyticsTools.specs, executor, AnalyticsTools::describe, images, history,
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
