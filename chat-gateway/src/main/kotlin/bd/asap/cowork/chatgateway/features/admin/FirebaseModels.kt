package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class FirebaseStatus(
    val configured: Boolean,
    val appId: String? = null,
    val ciToken: String? = null,
    val testerGroups: String? = null,
    val releaseNotes: String? = null,
)

@Serializable
data class SetFirebaseCredentialsRequest(
    val appId: String,
    val ciToken: String,
    val testerGroups: String? = null,
    val releaseNotes: String? = null,
)

@Serializable
data class GenerateCiTokenResult(val token: String)

@Serializable
data class FirebaseAppInfo(
    val appId: String,
    val displayName: String? = null,
    val platform: String,
)

@Serializable
data class ListFirebaseAppsRequest(
    val projectId: String,
    /** Optional — lets "list apps" authenticate with a CI token that hasn't been saved yet (e.g. just generated). */
    val ciToken: String? = null,
)

@Serializable
data class ListFirebaseAppsResult(val apps: List<FirebaseAppInfo>)
