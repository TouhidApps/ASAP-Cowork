package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val status: String,
    val uptimeSeconds: Long,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
    val activeProvider: String,
    val conversationMessageCount: Int,
    val storage: StorageStatus,
    val databaseSizeBytes: Long,
)

@Serializable
data class ProviderInfo(
    val id: String,
    val requiresApiKey: Boolean,
    val hasApiKey: Boolean,
    /** The key as stored in `.env`, so it can be reviewed/copied again — null if unset. */
    val apiKey: String? = null,
)

@Serializable
data class ProvidersResponse(
    val available: List<ProviderInfo>,
    val current: String,
)

@Serializable
data class SetProviderRequest(
    val provider: String,
)

@Serializable
data class SetProviderCredentialRequest(
    val apiKey: String,
)

@Serializable
data class AllowedHostsResponse(
    val hosts: List<String>,
)

@Serializable
data class AddAllowedHostRequest(
    val host: String,
)
