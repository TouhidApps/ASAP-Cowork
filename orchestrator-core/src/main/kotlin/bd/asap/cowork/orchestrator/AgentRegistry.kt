package bd.asap.cowork.orchestrator

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.Task

/**
 * Holds every agent activated for the current session. Only agents whose
 * capability matches the detected project stack(s) should be registered —
 * lazy activation per PLAN.md §2.
 */
class AgentRegistry {
    private val agents = mutableListOf<Agent>()

    fun register(agent: Agent) {
        agents += agent
    }

    fun findFor(task: Task): List<Agent> = agents.filter { it.canHandle(task) }

    fun all(): List<Agent> = agents.toList()
}
