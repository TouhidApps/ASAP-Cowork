package bd.asap.cowork.chatgateway.config

import bd.asap.cowork.contextstore.SettingsRepository
import bd.asap.cowork.toolintegrations.ToolchainPaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredToolchainPaths(
    val flutterSdkPath: String? = null,
    val androidSdkPath: String? = null,
    val javaHomePath: String? = null,
    val xcodePath: String? = null,
    val xcodeGenPath: String? = null,
)

/**
 * SDK root paths saved from the admin panel, backed by the SQLite
 * `settings` table (one row, JSON-encoded). A value saved here wins over
 * the FLUTTER_SDK_PATH/ANDROID_SDK_PATH/JAVA_HOME_PATH env vars, which
 * themselves fall back to the standard ANDROID_HOME/JAVA_HOME a shell
 * profile might already set.
 */
class ToolchainSettingsStore(private val settings: SettingsRepository) {
    private suspend fun read(): StoredToolchainPaths {
        val stored = settings.get(KEY) ?: return StoredToolchainPaths()
        return runCatching { Json.decodeFromString<StoredToolchainPaths>(stored) }.getOrDefault(StoredToolchainPaths())
    }

    private suspend fun write(paths: StoredToolchainPaths) = settings.set(KEY, Json.encodeToString(paths))

    suspend fun resolve(): ToolchainPaths {
        val stored = read()
        return ToolchainPaths(
            flutterSdkPath = stored.flutterSdkPath?.takeIf { it.isNotBlank() } ?: DotEnv.get("FLUTTER_SDK_PATH"),
            androidSdkPath = stored.androidSdkPath?.takeIf { it.isNotBlank() } ?: DotEnv.get("ANDROID_SDK_PATH") ?: DotEnv.get("ANDROID_HOME"),
            javaHomePath = stored.javaHomePath?.takeIf { it.isNotBlank() } ?: DotEnv.get("JAVA_HOME_PATH") ?: DotEnv.get("JAVA_HOME"),
            xcodePath = stored.xcodePath?.takeIf { it.isNotBlank() } ?: DotEnv.get("XCODE_PATH"),
            xcodeGenPath = stored.xcodeGenPath?.takeIf { it.isNotBlank() } ?: DotEnv.get("XCODEGEN_PATH"),
        )
    }

    suspend fun setPaths(
        flutterSdkPath: String?,
        androidSdkPath: String?,
        javaHomePath: String?,
        xcodePath: String?,
        xcodeGenPath: String?,
    ): ToolchainPaths {
        write(
            StoredToolchainPaths(
                flutterSdkPath = flutterSdkPath?.trim()?.takeIf { it.isNotBlank() },
                androidSdkPath = androidSdkPath?.trim()?.takeIf { it.isNotBlank() },
                javaHomePath = javaHomePath?.trim()?.takeIf { it.isNotBlank() },
                xcodePath = xcodePath?.trim()?.takeIf { it.isNotBlank() },
                xcodeGenPath = xcodeGenPath?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        return resolve()
    }

    private companion object {
        const val KEY = "toolchain.paths"
    }
}
