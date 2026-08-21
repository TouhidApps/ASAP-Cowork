package bd.asap.cowork.chatgateway.features.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectEntry(
    val name: String,
    /** Relative to the workspace root, forward-slash separated, no leading slash. */
    val path: String,
    val type: ProjectEntryType,
    val sizeBytes: Long? = null,
)

enum class ProjectEntryType { DIRECTORY, FILE }

@Serializable
data class ProjectTreeResult(
    /** "" for the workspace root itself. */
    val path: String,
    val entries: List<ProjectEntry>,
)

enum class ProjectFileKind { TEXT, IMAGE, BINARY }

@Serializable
data class ProjectFileResult(
    val path: String,
    val kind: ProjectFileKind,
    val sizeBytes: Long,
    val content: String? = null,
    val truncated: Boolean = false,
    val language: String? = null,
)
