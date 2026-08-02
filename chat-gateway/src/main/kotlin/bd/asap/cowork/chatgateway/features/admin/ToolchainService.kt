package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.ToolchainSettingsStore
import bd.asap.cowork.toolintegrations.DetectedToolchainPath
import bd.asap.cowork.toolintegrations.ToolchainDetector
import bd.asap.cowork.toolintegrations.ToolchainInstaller
import bd.asap.cowork.toolintegrations.ToolchainPathsRegistry
import java.io.File

/**
 * Business logic behind the admin panel's toolchain settings section: view
 * live detection state for each SDK (status), save explicit paths
 * (update), and trigger a best-effort automatic install (install).
 */
class ToolchainService(private val store: ToolchainSettingsStore) {
    fun status(): ToolchainStatus {
        val paths = ToolchainPathsRegistry.current()
        return ToolchainStatus(
            flutter = ToolchainDetector.detectFlutter(paths.flutterSdkPath).toInfo(),
            androidSdk = ToolchainDetector.detectAndroidSdk(paths.androidSdkPath).toInfo(),
            java = ToolchainDetector.detectJava(paths.javaHomePath).toInfo(),
            xcode = ToolchainDetector.detectXcode(paths.xcodePath).toInfo(),
            xcodeGen = ToolchainDetector.detectXcodeGen(paths.xcodeGenPath).toInfo(),
        )
    }

    suspend fun update(
        flutterSdkPath: String?,
        androidSdkPath: String?,
        javaHomePath: String?,
        xcodePath: String?,
        xcodeGenPath: String?,
    ): ToolchainStatus {
        listOfNotNull(flutterSdkPath, androidSdkPath, javaHomePath, xcodePath, xcodeGenPath)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { path ->
                if (!File(path).isDirectory) throw AppException.BadRequest("Not a directory: $path")
            }
        ToolchainPathsRegistry.set(store.setPaths(flutterSdkPath, androidSdkPath, javaHomePath, xcodePath, xcodeGenPath))
        return status()
    }

    /**
     * Runs the installer for one component, then — if it succeeded and a
     * path was found afterward — saves that path immediately, same as if
     * the user had clicked "use this path?" themselves. "xcode" is
     * rejected up front regardless of Homebrew availability — there's no
     * cask for it (App Store / Apple's developer site only), unlike every
     * other component here.
     */
    suspend fun install(component: String): ToolchainStatus {
        if (component == "xcode") {
            throw AppException.BadRequest(
                "Xcode can't be installed automatically — install it from the App Store or " +
                    "https://developer.apple.com/download/all/, then set its path here.",
            )
        }
        if (!ToolchainDetector.canAutoInstall()) {
            throw AppException.BadRequest("Automatic install isn't available on this server (macOS with Homebrew required).")
        }

        val (success, output) = when (component) {
            "flutter" -> ToolchainInstaller.installFlutter()
            "android-sdk" -> ToolchainInstaller.installAndroidSdk()
            "java" -> ToolchainInstaller.installJava()
            "xcodegen" -> ToolchainInstaller.installXcodeGen()
            else -> throw AppException.BadRequest("Unknown toolchain component: $component")
        }
        if (!success) throw AppException.BadRequest("Install failed:\n${output.takeLast(1_000)}")

        val current = ToolchainPathsRegistry.current()
        val detectedPath = when (component) {
            "flutter" -> ToolchainDetector.detectFlutter(null).detectedPath
            "android-sdk" -> ToolchainDetector.detectAndroidSdk(null).detectedPath
            "java" -> ToolchainDetector.detectJava(null).detectedPath
            else -> ToolchainDetector.detectXcodeGen(null).detectedPath
        }
        if (detectedPath != null) {
            val resolved = when (component) {
                "flutter" -> store.setPaths(detectedPath, current.androidSdkPath, current.javaHomePath, current.xcodePath, current.xcodeGenPath)
                "android-sdk" -> store.setPaths(current.flutterSdkPath, detectedPath, current.javaHomePath, current.xcodePath, current.xcodeGenPath)
                "java" -> store.setPaths(current.flutterSdkPath, current.androidSdkPath, detectedPath, current.xcodePath, current.xcodeGenPath)
                else -> store.setPaths(current.flutterSdkPath, current.androidSdkPath, current.javaHomePath, current.xcodePath, detectedPath)
            }
            ToolchainPathsRegistry.set(resolved)
        }
        return status()
    }

    private fun DetectedToolchainPath.toInfo() = ToolchainPathInfo(configuredPath, detectedPath, available, installable)
}
