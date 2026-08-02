package bd.asap.cowork.orchestrator

import bd.asap.cowork.agentsdk.ProjectContextView
import java.nio.file.Files
import java.nio.file.Path

/**
 * The orchestrator's live, mutable understanding of the project: which
 * directory agents actually operate in, and its detected stacks. This is
 * the only place persistent state lives — agents are stateless and receive
 * a read-only [ProjectContextView] slice per task.
 *
 * The root isn't fixed at startup — [confirm] repoints it (chat-gateway's
 * workspace routes call this when the user picks a project directory via
 * the DirectoryPicker), so a running server can be redirected at a real
 * target project without a restart.
 */
class ProjectContext(initialRoot: Path) : ProjectContextView {
    @Volatile
    private var root: Path = initialRoot.toAbsolutePath().normalize()

    init {
        Files.createDirectories(root)
    }

    override val workspaceRoot: String get() = root.toString()

    private val _detectedStacks: MutableSet<String> = ProjectFingerprinter.detect(root).toMutableSet()
    override val detectedStacks: Set<String> get() = _detectedStacks

    @Synchronized
    fun confirm(newRoot: Path) {
        root = newRoot.toAbsolutePath().normalize()
        rescan()
    }

    @Synchronized
    fun rescan() {
        _detectedStacks.clear()
        _detectedStacks += ProjectFingerprinter.detect(root)
    }
}
