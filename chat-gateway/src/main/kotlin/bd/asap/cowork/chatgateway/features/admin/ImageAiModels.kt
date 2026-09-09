package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class ImageAiProviderInfo(
    val id: String, // "openai" | "gemini"
    val hasApiKey: Boolean,
    /** The key as stored in `.env`, so it can be reviewed/copied again — null if unset. */
    val apiKey: String? = null,
)

@Serializable
data class ImageAiStatus(
    val available: List<ImageAiProviderInfo>,
    val current: String?,
)

@Serializable
data class SetImageAiProviderRequest(val provider: String)

@Serializable
data class SetImageAiCredentialRequest(val apiKey: String)
