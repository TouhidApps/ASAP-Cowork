package bd.asap.cowork.agentsdk

import java.nio.file.Files
import java.nio.file.Path

/** One file a code-generation agent produced, parsed out of the model's reply. */
data class GeneratedFile(val path: String, val content: String)

private val FILE_BLOCK = Regex("""===FILE: (.+?)===\r?\n(.*?)\r?\n===END FILE===""", RegexOption.DOT_MATCHES_ALL)

/**
 * Extracts files from a model reply that follows the `===FILE: <path>===`
 * / `===END FILE===` convention every code-gen agent's system prompt asks
 * for. Agents parse the full buffered reply with this after their stream
 * completes, rather than during streaming, to keep parsing simple.
 */
fun parseGeneratedFiles(reply: String): List<GeneratedFile> =
    FILE_BLOCK.findAll(reply).map { GeneratedFile(path = it.groupValues[1].trim(), content = it.groupValues[2]) }.toList()

/**
 * Path-safe file writer scoped to one project directory under the
 * workspace — code-gen agents write generated project files here rather
 * than into the orchestrator's own source tree. Rejects any path that
 * would escape [root] (e.g. via ".." or an absolute path) so a malformed
 * model reply can't write outside the sandbox.
 */
class Workspace(private val root: Path) {
    init {
        Files.createDirectories(root)
    }

    val rootPath: Path get() = root

    /**
     * Resolves [relativePath] against [root], or `null` if it would escape
     * (absolute path, "..", or a symlink pointing outside). Non-throwing —
     * tools that just need a safe directory/file reference (run a command
     * here, read this file) use this instead of [write]'s hard failure.
     */
    fun resolve(relativePath: String): Path? {
        val trimmed = relativePath.trim()
        if (trimmed.isBlank() || Path.of(trimmed).isAbsolute) return null

        val target = root.resolve(trimmed).normalize()
        return target.takeIf { it.startsWith(root) }
    }

    fun write(relativePath: String, content: String): Path {
        val target = resolve(relativePath)
            ?: throw IllegalArgumentException("Refusing to write outside workspace: $relativePath")

        Files.createDirectories(target.parent)
        Files.writeString(target, content)
        return target
    }
}
