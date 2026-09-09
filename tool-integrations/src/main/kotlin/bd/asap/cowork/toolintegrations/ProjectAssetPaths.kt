package bd.asap.cowork.toolintegrations

/**
 * Where a project-scoped asset folder (`branding/`, `images/`) nests.
 * [directory], when given, is a scaffolded project's directory name (e.g.
 * "WeatherNow") — the folder then lands at `<directory>/<folder>/...`
 * instead of the shared workspace root, so multiple projects' assets don't
 * collide. Blank [directory] preserves the original flat, workspace-root
 * layout, for a workspace with nothing project-specific scaffolded yet.
 */
internal fun normalizedProjectDirectory(directory: String?): String = directory?.trim()?.trim('/').orEmpty()

internal fun projectRelativePath(directory: String, folder: String, filename: String): String =
    if (directory.isBlank()) "$folder/$filename" else "$directory/$folder/$filename"

internal fun projectAssetUrl(directory: String, urlPrefix: String, filename: String): String =
    if (directory.isBlank()) "$urlPrefix/$filename" else "$urlPrefix/$directory/$filename"
