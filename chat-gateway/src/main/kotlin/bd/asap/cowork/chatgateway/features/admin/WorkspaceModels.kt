package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceStatus(val configured: Boolean, val root: String)

@Serializable
data class WorkspaceBrowseEntry(val name: String, val path: String)

@Serializable
data class WorkspaceBrowseResult(val path: String, val parent: String?, val entries: List<WorkspaceBrowseEntry>)

@Serializable
data class ConfirmWorkspaceRequest(val path: String)

@Serializable
data class StorageCategory(val name: String, val label: String, val fileCount: Int, val totalBytes: Long)

@Serializable
data class StorageStatus(val categories: List<StorageCategory>)

@Serializable
data class CleanupStorageRequest(val target: String)

@Serializable
data class BackupItem(val name: String, val label: String)

@Serializable
data class BackupItemsResponse(val items: List<BackupItem>)

@Serializable
data class BackupRequest(val destination: String, val items: List<String>)

@Serializable
data class BackupResult(val zipPath: String, val fileCount: Int, val totalBytes: Long)
