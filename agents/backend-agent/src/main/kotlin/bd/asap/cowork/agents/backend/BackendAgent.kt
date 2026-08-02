package bd.asap.cowork.agents.backend

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
import bd.asap.cowork.toolintegrations.BackendTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Paths
import java.util.Base64

/**
 * PLAN.md §3, roadmap #9 (Backend Development Agent), scoped beyond the
 * original Spring Boot/Node.js pair to four stacks — Spring Boot
 * (Kotlin), Node/Express, Python/FastAPI, and PHP — each paired with a
 * choice of MySQL, PostgreSQL, or SQLite via [BackendTools]'s
 * `create_backend_project`. Every stack gets a working REST CRUD API
 * around one example "Item" resource plus a real, browsable admin UI
 * (Spring Data REST's HAL Explorer, AdminJS, SQLAdmin, or a hand-rolled
 * PHP page, respectively) — never a hand-rolled admin UI where a real,
 * widely-used generator already does the job.
 *
 * Every tool is dispatched through `build-runner`, so this process never
 * shells out to Gradle, npm, pip, or php itself.
 */
class BackendAgent(private val providers: LlmProviderRegistry) : Agent {
    override val id: String = "backend-agent"
    override val capabilities: Set<Capability> = setOf(Capability.BACKEND_BUILD)
    override val description: String =
        "Actually scaffolds/creates a new backend project on disk (not just a plan) — a REST CRUD API plus a real admin UI, for Spring Boot (Kotlin), Node/Express, Python/FastAPI, or PHP, backed by MySQL, PostgreSQL, or SQLite. Also runs it (manage_backend_server) and can build/test it. Use for 'scaffold/create/set up a backend/API/server', or any 'build/run' request against one."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        emit(AgentEvent.Progress("Backend agent is working..."))

        val systemPrompt = buildString {
            appendLine("You are the Backend Development Agent inside ASAP-Cowork. You have real tools to scaffold, build, run, and verify a backend project — use them rather than just describing what you'd do.")
            appendLine("If the user hasn't specified a stack or database, ask which they want (spring-boot/node-express/python-fastapi/php, and mysql/postgres/sqlite) before scaffolding — don't silently guess for a decision this consequential. sqlite needs no setup and is the fastest way to try something out; mysql/postgres need a server already reachable at localhost with default credentials (create_backend_project's result tells you exactly what's expected).")
            appendLine("Typical flow: create_backend_project, then manage_backend_server (action=\"start\", matching stack and directory) to run it, then run_terminal_command with curl to exercise the REST API (e.g. curl -X POST http://localhost:8080/api/items -d '{\"name\":\"...\"}').")
            appendLine("Every stack's result tells you exactly where its admin UI lives (e.g. /api/explorer/index.html for spring-boot, /admin for node-express and python-fastapi, /admin/index.php for php) — mention that URL to the user once the server is running.")
            appendLine("For spring-boot specifically, run_gradle (task \"build\" or \"test\") works normally since those complete and exit — only actually *running* the server needs manage_backend_server, since a dev server never exits on its own.")
            appendLine("For changes to an existing project, edit files with run_terminal_command as needed, then rebuild/restart and reverify the same way.")
            appendLine("Report concisely what you did and what the result was — you don't need to restate tool output verbatim, the user can see it.")
            if (task.attachments.isNotEmpty()) {
                appendLine("The user attached one or more images with this message — they're included below, look at them directly rather than asking the user to attach one.")
            }
        }

        val workspaceRoot = Paths.get(context.workspaceRoot).toFile()
        val executor = BackendTools.executorFor(workspaceRoot)
        val images = task.attachments.mapNotNull { attachment ->
            runCatching { Paths.get(attachment.path).toFile().readBytes() }.getOrNull()
                ?.let { bytes -> ImageAttachment(attachment.mimeType, Base64.getEncoder().encodeToString(bytes)) }
        }

        val history = task.history.map { it.toChatMessage() }

        providers.current().runAgenticLoop(
            systemPrompt, task.input, BackendTools.specs, executor, BackendTools::describe, images, history,
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
