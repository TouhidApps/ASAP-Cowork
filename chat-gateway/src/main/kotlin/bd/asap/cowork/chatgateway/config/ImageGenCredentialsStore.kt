package bd.asap.cowork.chatgateway.config

import bd.asap.cowork.imagegen.ImageGenCredentials

/**
 * Image AI provider selection + per-provider API keys saved from the admin
 * panel's Image AI section, backed by the `.env` file (see [DotEnv]) — a
 * separate namespace of keys from the chat-LLM OPENAI_API_KEY/GEMINI_API_KEY,
 * since the user may want different keys/billing for images vs. chat. Read
 * by ImagingAgent's tools via ImageGenCredentialsRegistry, not this store
 * directly.
 */
class ImageGenCredentialsStore {
    fun read(): ImageGenCredentials? {
        val provider = DotEnv.get(PROVIDER_KEY) ?: return null
        return ImageGenCredentials(
            provider = provider,
            openAiApiKey = DotEnv.get(OPENAI_KEY_KEY),
            geminiApiKey = DotEnv.get(GEMINI_KEY_KEY),
        )
    }

    fun setProvider(provider: String) = DotEnv.set(PROVIDER_KEY, provider)

    fun setApiKey(provider: String, apiKey: String) = when (provider) {
        "openai" -> DotEnv.set(OPENAI_KEY_KEY, apiKey)
        "gemini" -> DotEnv.set(GEMINI_KEY_KEY, apiKey)
        else -> Unit
    }

    fun clearApiKey(provider: String) = when (provider) {
        "openai" -> DotEnv.set(OPENAI_KEY_KEY, "")
        "gemini" -> DotEnv.set(GEMINI_KEY_KEY, "")
        else -> Unit
    }

    companion object {
        const val PROVIDER_KEY = "IMAGE_GEN_PROVIDER"
        const val OPENAI_KEY_KEY = "OPENAI_IMAGE_API_KEY"
        const val GEMINI_KEY_KEY = "GEMINI_IMAGE_API_KEY"
    }
}
