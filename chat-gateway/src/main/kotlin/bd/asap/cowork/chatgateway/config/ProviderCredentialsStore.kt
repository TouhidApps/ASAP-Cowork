package bd.asap.cowork.chatgateway.config

import bd.asap.cowork.contextstore.SettingsRepository

/**
 * The currently-selected LLM provider id, backed by the SQLite `settings`
 * table so it survives a restart. (Per-provider API keys used to live here
 * too, but now go straight to `.env` — see [DotEnv] — so admin-panel edits
 * don't require a separate persistence layer from the file the app already
 * reads keys from.) `llm-gateway`'s
 * [bd.asap.cowork.llmgateway.LlmProviderRegistry] deliberately has no
 * persistence of its own — it must not depend on context-store — so the
 * chosen provider is persisted here instead and re-applied to the registry
 * at DI startup.
 */
class ProviderCredentialsStore(private val settings: SettingsRepository) {
    suspend fun getCurrentProvider(): String? = settings.get(CURRENT_PROVIDER_KEY)

    suspend fun setCurrentProvider(id: String) = settings.set(CURRENT_PROVIDER_KEY, id)

    private companion object {
        const val CURRENT_PROVIDER_KEY = "llm.currentProvider"
    }
}
