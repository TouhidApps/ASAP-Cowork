package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class ToolchainPathInfo(
    val configuredPath: String? = null,
    val detectedPath: String? = null,
    val available: Boolean = false,
    val installable: Boolean = false,
)

@Serializable
data class ToolchainStatus(
    val flutter: ToolchainPathInfo,
    val androidSdk: ToolchainPathInfo,
    val java: ToolchainPathInfo,
    val xcode: ToolchainPathInfo,
    val xcodeGen: ToolchainPathInfo,
)

@Serializable
data class SetToolchainPathsRequest(
    val flutterSdkPath: String? = null,
    val androidSdkPath: String? = null,
    val javaHomePath: String? = null,
    val xcodePath: String? = null,
    val xcodeGenPath: String? = null,
)
