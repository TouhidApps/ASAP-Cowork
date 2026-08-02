package bd.asap.cowork.agentsdk

import kotlinx.coroutines.flow.Flow

/**
 * A specialist worker the orchestrator routes tasks to. Agents are stateless:
 * all persistent understanding of the project lives in the orchestrator's
 * ProjectContext, not here. An agent module depends only on this interface —
 * never on orchestrator-core or on another agent — so it can be developed,
 * tested, and later extracted into its own service independently.
 */
interface Agent {
    val id: String
    val capabilities: Set<Capability>

    /** One-line, human-readable summary of what this agent handles — the orchestrator's intent classifier reads this to route a raw message without the client naming a capability. */
    val description: String

    fun canHandle(task: Task): Boolean = task.capability in capabilities

    fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent>
}
