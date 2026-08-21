package bd.asap.cowork.chatgateway.features.project

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.orchestrator.ProjectContext
import io.ktor.http.ContentType
import java.io.File

/**
 * Backs the chat page's "Project" panel — a read-only, lazily-expanded
 * browser over whatever [ProjectContext.workspaceRoot] currently points at,
 * so the same tree the AI agents read/write is visible without leaving the
 * chat UI (unlike [bd.asap.cowork.chatgateway.features.admin.WorkspaceService.browse],
 * which only lists directories, for picking the workspace root itself).
 */
class ProjectFilesService(private val context: ProjectContext) {

    fun tree(relativePath: String?): ProjectTreeResult {
        val root = workspaceRoot()
        val target = resolveWithin(root, relativePath)
        if (!target.isDirectory) throw AppException.BadRequest("Not a directory: $relativePath")

        val entries = (target.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { it.toEntry(root) }

        return ProjectTreeResult(path = root.relativize(target), entries = entries)
    }

    fun file(relativePath: String): ProjectFileResult {
        val root = workspaceRoot()
        val target = resolveWithin(root, relativePath)
        if (!target.isFile) throw AppException.NotFound("Not a file: $relativePath")

        val sizeBytes = target.length()
        val extension = target.extension.lowercase()
        return when {
            extension in IMAGE_EXTENSIONS -> ProjectFileResult(path = root.relativize(target), kind = ProjectFileKind.IMAGE, sizeBytes = sizeBytes)
            extension in BINARY_EXTENSIONS && sizeBytes > 0 ->
                ProjectFileResult(path = root.relativize(target), kind = ProjectFileKind.BINARY, sizeBytes = sizeBytes)
            else -> {
                val bytes = target.readBytes()
                if (sizeBytes > 0 && looksBinary(bytes)) {
                    ProjectFileResult(path = root.relativize(target), kind = ProjectFileKind.BINARY, sizeBytes = sizeBytes)
                } else {
                    val truncated = bytes.size > MAX_TEXT_BYTES
                    val content = String(if (truncated) bytes.copyOf(MAX_TEXT_BYTES) else bytes, Charsets.UTF_8)
                    ProjectFileResult(
                        path = root.relativize(target),
                        kind = ProjectFileKind.TEXT,
                        sizeBytes = sizeBytes,
                        content = content,
                        truncated = truncated,
                        language = LANGUAGE_BY_EXTENSION[extension],
                    )
                }
            }
        }
    }

    fun rawFile(relativePath: String): Pair<File, ContentType> {
        val root = workspaceRoot()
        val target = resolveWithin(root, relativePath)
        if (!target.isFile) throw AppException.NotFound("Not a file: $relativePath")
        return target to contentTypeFor(target.extension.lowercase())
    }

    private fun workspaceRoot(): File = File(context.workspaceRoot).canonicalFile

    /** Resolves [relativePath] against [root], rejecting anything that escapes it (e.g. via `..`). */
    private fun resolveWithin(root: File, relativePath: String?): File {
        val normalized = relativePath?.trim()?.trim('/').orEmpty()
        val candidate = if (normalized.isEmpty()) root else File(root, normalized)
        val canonical = candidate.canonicalFile
        if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) {
            throw AppException.BadRequest("Path escapes the workspace: $relativePath")
        }
        if (!canonical.exists()) throw AppException.NotFound("No such file or directory: $relativePath")
        return canonical
    }

    private fun File.toEntry(root: File): ProjectEntry = ProjectEntry(
        name = name,
        path = root.relativize(this),
        type = if (isDirectory) ProjectEntryType.DIRECTORY else ProjectEntryType.FILE,
        sizeBytes = if (isFile) length() else null,
    )

    private fun File.relativize(target: File): String =
        toPath().relativize(target.toPath()).toString().replace(File.separatorChar, '/')

    /** Null-byte sniff over the first chunk — catches binary files an unrecognized extension would otherwise misroute as text. */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.copyOf(minOf(bytes.size, SNIFF_BYTES))
        return sample.any { it == 0.toByte() }
    }

    private fun contentTypeFor(extension: String): ContentType = when (extension) {
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "gif" -> ContentType.Image.GIF
        "svg" -> ContentType("image", "svg+xml")
        "webp" -> ContentType("image", "webp")
        "bmp" -> ContentType("image", "bmp")
        "ico" -> ContentType("image", "x-icon")
        else -> ContentType.Application.OctetStream
    }

    private companion object {
        const val MAX_TEXT_BYTES = 512 * 1024
        const val SNIFF_BYTES = 4096

        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg")
        val BINARY_EXTENSIONS = setOf(
            "zip", "jar", "aar", "apk", "aab", "ipa", "dex", "class",
            "so", "dylib", "dll", "exe", "bin", "keystore", "jks", "p12",
            "pdf", "mp4", "mov", "avi", "mp3", "wav", "ttf", "otf", "woff", "woff2",
        )
        val LANGUAGE_BY_EXTENSION = mapOf(
            "kt" to "kotlin", "kts" to "kotlin", "java" to "java", "dart" to "dart",
            "ts" to "typescript", "tsx" to "tsx", "js" to "javascript", "jsx" to "jsx",
            "json" to "json", "md" to "markdown", "xml" to "xml", "gradle" to "groovy",
            "yaml" to "yaml", "yml" to "yaml", "html" to "html", "css" to "css",
            "py" to "python", "rb" to "ruby", "go" to "go", "rs" to "rust",
            "swift" to "swift", "c" to "c", "h" to "c", "cpp" to "cpp", "hpp" to "cpp",
            "sh" to "bash", "sql" to "sql", "toml" to "toml", "properties" to "properties",
        )
    }
}
