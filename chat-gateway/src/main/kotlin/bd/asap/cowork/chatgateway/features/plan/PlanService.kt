package bd.asap.cowork.chatgateway.features.plan

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.orchestrator.ProjectContext
import java.io.File

private val EXCLUDED_DIRS = setOf(
    "node_modules", "build", "dist", "out", "target",
    ".git", ".gradle", ".idea", ".asap-history", ".asap-uploads",
    ".asap-screenshots", ".asap-videos", "venv", ".venv",
)
private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")

/**
 * Read-only view over whatever Markdown already exists on disk in the
 * workspace — plan.md, brand-guide.md, agent-written specs, anything — for
 * the chat page's "Plan Preview" panel. Not tied to any one agent; it's a
 * generic file viewer scoped to `*.md`/`*.markdown`.
 */
class PlanService(private val projectContext: ProjectContext) {
    private val root: File get() = File(projectContext.workspaceRoot)

    fun listMarkdownFiles(): List<MarkdownFileEntry> {
        val results = mutableListOf<MarkdownFileEntry>()
        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name in EXCLUDED_DIRS || child.name.startsWith(".")) continue
                    walk(child)
                } else if (child.extension.lowercase() in MARKDOWN_EXTENSIONS) {
                    results += MarkdownFileEntry(
                        path = child.relativeTo(root).path.replace(File.separatorChar, '/'),
                        name = child.name,
                        updatedAt = child.lastModified(),
                    )
                }
            }
        }
        walk(root)
        return results.sortedByDescending { it.updatedAt }
    }

    fun readMarkdownFile(relativePath: String): String {
        if (relativePath.isBlank()) throw AppException.BadRequest("Missing path")
        val extension = relativePath.substringAfterLast('.', "").lowercase()
        if (extension !in MARKDOWN_EXTENSIONS) throw AppException.BadRequest("Not a markdown file: $relativePath")
        // File(root, child) silently ignores `root` when `child` is itself
        // absolute, so an absolute relativePath would otherwise read any
        // file on disk regardless of the startsWith check below.
        if (File(relativePath).isAbsolute) throw AppException.BadRequest("Invalid path")

        val rootCanonical = root.canonicalFile
        val target = File(root, relativePath).canonicalFile
        if (target != rootCanonical && !target.path.startsWith(rootCanonical.path + File.separator)) {
            throw AppException.BadRequest("Invalid path")
        }
        if (!target.isFile) throw AppException.NotFound("Markdown file not found: $relativePath")
        return target.readText()
    }
}
