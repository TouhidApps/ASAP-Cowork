package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.DotEnv
import bd.asap.cowork.chatgateway.config.ImageGenCredentialsStore
import bd.asap.cowork.imagegen.ImageGenCredentials
import bd.asap.cowork.imagegen.ImageGenCredentialsRegistry

private val KNOWN_PROVIDERS = listOf("openai", "gemini")

/**
 * Business logic behind the admin panel's Image AI section — separate from
 * AdminService's chat-LLM providers()/setProvider(), a different concern
 * (what backs image generation, not the orchestrator's chat model). Every
 * write goes through both [store] (persists across restarts) and
 * [ImageGenCredentialsRegistry] (what generate_image/edit_image actually
 * read), so a save takes effect on the very next chat message with no
 * restart.
 */
class ImageAiService(private val store: ImageGenCredentialsStore) {
    fun status(): ImageAiStatus {
        val current = ImageGenCredentialsRegistry.current()
        return ImageAiStatus(
            available = KNOWN_PROVIDERS.map { id ->
                val key = apiKeyFor(id, current)
                ImageAiProviderInfo(id = id, hasApiKey = !key.isNullOrBlank(), apiKey = key)
            },
            current = current?.provider,
        )
    }

    fun setProvider(provider: String): ImageAiStatus {
        requireKnownProvider(provider)
        store.setProvider(provider)
        applyToRegistry()
        return status()
    }

    fun setCredential(provider: String, apiKey: String): ImageAiStatus {
        requireKnownProvider(provider)
        if (apiKey.isBlank()) throw AppException.BadRequest("API key must not be blank")
        store.setApiKey(provider, apiKey.trim())
        applyToRegistry()
        return status()
    }

    fun clearCredential(provider: String): ImageAiStatus {
        requireKnownProvider(provider)
        store.clearApiKey(provider)
        applyToRegistry()
        return status()
    }

    /** Re-derives the registry's snapshot from .env after any write, same as store.read() does at DI startup. */
    private fun applyToRegistry() {
        val provider = DotEnv.get(ImageGenCredentialsStore.PROVIDER_KEY) ?: KNOWN_PROVIDERS.first()
        ImageGenCredentialsRegistry.set(
            ImageGenCredentials(
                provider = provider,
                openAiApiKey = DotEnv.get(ImageGenCredentialsStore.OPENAI_KEY_KEY),
                geminiApiKey = DotEnv.get(ImageGenCredentialsStore.GEMINI_KEY_KEY),
            ),
        )
    }

    private fun apiKeyFor(provider: String, current: ImageGenCredentials?) = when (provider) {
        "openai" -> current?.openAiApiKey ?: DotEnv.get(ImageGenCredentialsStore.OPENAI_KEY_KEY)
        "gemini" -> current?.geminiApiKey ?: DotEnv.get(ImageGenCredentialsStore.GEMINI_KEY_KEY)
        else -> null
    }

    private fun requireKnownProvider(id: String) {
        if (id !in KNOWN_PROVIDERS) throw AppException.BadRequest("Unknown provider: $id")
    }
}
