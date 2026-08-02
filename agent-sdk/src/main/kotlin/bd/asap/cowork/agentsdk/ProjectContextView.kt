package bd.asap.cowork.agentsdk

/**
 * Read-only, capability-scoped slice of the orchestrator's full project
 * context. Agents receive this per task; they never hold or mutate state
 * between calls — the orchestrator is the only place that does.
 */
interface ProjectContextView {
    /** Absolute path to the project workspace this task operates in. */
    val workspaceRoot: String

    /** Stack signatures the orchestrator detected in [workspaceRoot] (e.g. "android", "flutter"). */
    val detectedStacks: Set<String>
}
