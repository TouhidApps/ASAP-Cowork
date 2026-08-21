package bd.asap.cowork.agents.workspace

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ProjectContextView
import bd.asap.cowork.agentsdk.Task
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Answers "what's in my workspace" / "list my projects" by listing the
 * top-level folders under [ProjectContextView.workspaceRoot] directly off
 * disk. Unlike every other agent this needs no LLM call — "what folders are
 * in this directory" has one correct answer, not something to reason about
 * — so it stays a pure filesystem read.
 */
class WorkspaceAgent : Agent {
    override val id: String = "workspace-agent"
    override val capabilities: Set<Capability> = setOf(Capability.WORKSPACE)
    override val description: String =
        "Lists the project folders in the user's configured workspace directory. Use for 'list my projects', 'what's in my workspace', 'show my working directory' requests. Not for building, scaffolding, or modifying anything."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        val root = File(context.workspaceRoot)
        val entries = (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }

        val reply = buildString {
            appendLine("Workspace: ${context.workspaceRoot}")
            if (entries.isEmpty()) {
                appendLine("No project folders found here yet.")
            } else {
                entries.forEach { appendLine("- ${it.name}") }
            }
            if (context.detectedStacks.isNotEmpty()) {
                appendLine()
                appendLine("Detected stack(s) at the workspace root: ${context.detectedStacks.joinToString(", ")}")
            }
        }.trimEnd()

        emit(AgentEvent.TextDelta(reply))
        emit(AgentEvent.Result(reply))
    }
}
