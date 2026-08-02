package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.WorkspaceSettingsStore
import bd.asap.cowork.orchestrator.ProjectContext
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Backs the admin panel's workspace picker: which directory on disk the
 * agents actually read/write/build in. Confirming a new root takes effect
 * immediately (updates the live [ProjectContext]) and persists across
 * restarts via [WorkspaceSettingsStore].
 */
class WorkspaceService(
    private val context: ProjectContext,
    private val store: WorkspaceSettingsStore,
) {
    suspend fun status(): WorkspaceStatus = WorkspaceStatus(configured = store.readRootPath() != null, root = context.workspaceRoot)

    fun browse(path: String?): WorkspaceBrowseResult {
        val target = File(path?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home"))
        if (!target.isDirectory) throw AppException.BadRequest("Not a directory: ${target.path}")

        val canonical = target.canonicalFile
        val entries = (canonical.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
            .map { WorkspaceBrowseEntry(name = it.name, path = it.path) }

        return WorkspaceBrowseResult(path = canonical.path, parent = canonical.parent, entries = entries)
    }

    suspend fun confirm(path: String): WorkspaceStatus {
        val dir = File(path)
        if (!dir.isDirectory) throw AppException.BadRequest("Not a directory: $path")
        if (!dir.canWrite()) throw AppException.BadRequest("Directory isn't writable: $path")

        context.confirm(Paths.get(path))
        store.writeRootPath(context.workspaceRoot)
        return status()
    }

    fun storageStatus(): StorageStatus = StorageStatus(
        categories = STORAGE_CATEGORIES.map { (name, label, dirName) ->
            val dir = File(context.workspaceRoot, dirName)
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            StorageCategory(name = name, label = label, fileCount = files.size, totalBytes = files.sumOf { it.length() })
        },
    )

    fun cleanup(target: String): StorageStatus {
        val matching = if (target == "all") STORAGE_CATEGORIES else STORAGE_CATEGORIES.filter { it.first == target }
        if (matching.isEmpty()) throw AppException.BadRequest("Unknown cleanup target: $target")

        matching.forEach { (_, _, dirName) ->
            File(context.workspaceRoot, dirName).listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        }
        return storageStatus()
    }

    fun backupItems(): BackupItemsResponse =
        BackupItemsResponse(items = STORAGE_CATEGORIES.map { (name, label, _) -> BackupItem(name, label) })

    /**
     * Zips the selected categories into a timestamped .zip in [destination].
     * The destination can't be inside the workspace itself, or the backup
     * would immediately start growing the very thing it's backing up.
     */
    fun backup(destination: String, items: List<String>): BackupResult {
        if (items.isEmpty()) throw AppException.BadRequest("Select at least one item to back up")

        val destDir = File(destination)
        if (!destDir.isDirectory) throw AppException.BadRequest("Not a directory: $destination")
        if (!destDir.canWrite()) throw AppException.BadRequest("Directory isn't writable: $destination")

        val workspaceRoot = File(context.workspaceRoot).canonicalFile
        val canonicalDest = destDir.canonicalFile
        if (canonicalDest == workspaceRoot || canonicalDest.path.startsWith(workspaceRoot.path + File.separator)) {
            throw AppException.BadRequest("Backup destination can't be inside the workspace")
        }

        val selected = STORAGE_CATEGORIES.filter { it.first in items }
        if (selected.isEmpty()) throw AppException.BadRequest("Unknown backup item(s): $items")

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val zipFile = File(destDir, "asap-backup-$timestamp.zip")

        var fileCount = 0
        var totalBytes = 0L
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            selected.forEach { (_, _, dirName) ->
                File(workspaceRoot, dirName).listFiles()?.filter { it.isFile }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("$dirName/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    fileCount++
                    totalBytes += file.length()
                }
            }
        }

        return BackupResult(zipPath = zipFile.absolutePath, fileCount = fileCount, totalBytes = totalBytes)
    }

    private companion object {
        val STORAGE_CATEGORIES = listOf(
            Triple("screenshots", "Screenshots", ".asap-screenshots"),
            Triple("videos", "Videos", ".asap-videos"),
            Triple("builds", "Built APKs", ".asap-builds"),
        )
    }
}
