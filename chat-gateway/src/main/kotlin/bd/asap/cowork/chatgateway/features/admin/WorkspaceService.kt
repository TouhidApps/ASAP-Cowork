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
        // listFiles() returns null both for a genuinely empty directory and for
        // one the OS refuses to list (e.g. macOS TCC silently denying a headless
        // JVM access to ~/Documents/~/Desktop/~/Downloads) — folding that into
        // emptyArray() would show "No subfolders here" for a directory that
        // actually has some, which is exactly wrong here.
        val listed = canonical.listFiles()
            ?: throw AppException.BadRequest(
                "Can't list \"${canonical.path}\" — permission denied. On macOS, grant this app's terminal/IDE " +
                    "access under System Settings -> Privacy & Security -> Files and Folders, then try again.",
            )
        val entries = listed
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
        BackupItemsResponse(
            items = STORAGE_CATEGORIES.map { (name, label, _) -> BackupItem(name, label) } + PROJECT_BACKUP_ITEM,
        )

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

        val includeProject = PROJECT_BACKUP_ITEM.name in items
        val selectedCategories = STORAGE_CATEGORIES.filter { it.first in items }
        if (selectedCategories.isEmpty() && !includeProject) {
            throw AppException.BadRequest("Unknown backup item(s): $items")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val zipFile = File(destDir, "asap-backup-$timestamp.zip")

        var fileCount = 0
        var totalBytes = 0L
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            selectedCategories.forEach { (_, _, dirName) ->
                File(workspaceRoot, dirName).listFiles()?.filter { it.isFile }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("$dirName/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    fileCount++
                    totalBytes += file.length()
                }
            }
            if (includeProject) {
                val (projectFiles, projectBytes) = addProjectFiles(zip, workspaceRoot)
                fileCount += projectFiles
                totalBytes += projectBytes
            }
        }

        return BackupResult(zipPath = zipFile.absolutePath, fileCount = fileCount, totalBytes = totalBytes)
    }

    /**
     * Walks the whole project tree into the zip under a "project/" prefix,
     * skipping directories that are either regenerable build/dependency
     * output (build/, .dart_tool/, .gradle/, node_modules/, Pods/,
     * DerivedData/ — across Flutter, Android/KMP, and React Native/iOS
     * alike, these are what actually make a project directory huge, and
     * `flutter clean`/`gradle clean` just delete the same directories under
     * the hood) or already covered by their own backup category above
     * (.asap-screenshots/.asap-videos/.asap-builds) — excluding them here
     * shrinks the backup exactly like a "clean" would, without mutating the
     * live project on disk the way actually running those clean commands
     * would (which would leave the workspace unbuildable until a rebuild).
     */
    private fun addProjectFiles(zip: ZipOutputStream, workspaceRoot: File): Pair<Int, Long> {
        var fileCount = 0
        var totalBytes = 0L
        val rootPath = workspaceRoot.toPath()
        workspaceRoot.walkTopDown()
            .onEnter { dir -> dir.name !in EXCLUDED_PROJECT_DIR_NAMES && !dir.name.startsWith(".asap-") }
            .filter { it.isFile }
            .forEach { file ->
                val relative = rootPath.relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry("project/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                fileCount++
                totalBytes += file.length()
            }
        return fileCount to totalBytes
    }

    private companion object {
        val STORAGE_CATEGORIES = listOf(
            Triple("screenshots", "Screenshots", ".asap-screenshots"),
            Triple("videos", "Videos", ".asap-videos"),
            Triple("builds", "Built APKs", ".asap-builds"),
        )
        val PROJECT_BACKUP_ITEM = BackupItem("project", "Project files (app source, excluding build/deps)")
        val EXCLUDED_PROJECT_DIR_NAMES = setOf("build", ".dart_tool", ".gradle", "node_modules", "Pods", "DerivedData")
    }
}
