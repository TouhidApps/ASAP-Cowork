package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.DotEnv
import bd.asap.cowork.chatgateway.config.ProviderCredentialsStore
import bd.asap.cowork.contextstore.ContextDatabase
import bd.asap.cowork.contextstore.ConversationRepository
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import java.lang.management.ManagementFactory

/**
 * Business logic behind the admin panel. New admin capabilities land here —
 * routes and the frontend just call whatever this exposes.
 */
class AdminService(
    private val providers: LlmProviderRegistry,
    private val conversation: ConversationRepository,
    private val workspace: WorkspaceService,
    private val credentials: ProviderCredentialsStore,
    private val contextDatabase: ContextDatabase,
) {
    suspend fun status(): SystemStatus {
        val runtime = Runtime.getRuntime()
        // The JVM's own start time, not "when this Koin single got
        // constructed" — `single { }` (no createdAtStart) is lazy, so that
        // would only be the moment the admin dashboard's first request
        // happened to resolve this instance, not when the server actually
        // came up, making uptime read as "since I opened the dashboard".
        val uptimeSeconds = (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().startTime) / 1000
        return SystemStatus(
            status = "UP",
            uptimeSeconds = uptimeSeconds,
            memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            memoryMaxMb = runtime.maxMemory() / (1024 * 1024),
            activeProvider = providers.current().id,
            conversationMessageCount = conversation.totalMessageCount(),
            storage = workspace.storageStatus(),
            databaseSizeBytes = contextDatabase.sizeBytes(),
        )
    }

    suspend fun conversations() = conversation.listConversations()

    suspend fun conversationMessages(id: String) = conversation.getMessages(id)

    suspend fun deleteConversation(id: String) = conversation.deleteConversation(id)

    suspend fun providers(): ProvidersResponse = ProvidersResponse(
        available = providers.available().map { provider ->
            ProviderInfo(
                id = provider.id,
                requiresApiKey = provider.requiresApiKey,
                hasApiKey = !provider.requiresApiKey || hasApiKey(provider.id),
                apiKey = DotEnv.get(apiKeyEnvVar(provider.id)),
            )
        },
        current = providers.currentId,
    )

    suspend fun setProvider(id: String): ProvidersResponse {
        requireKnownProvider(id)
        providers.switchTo(id)
        credentials.setCurrentProvider(id)
        return providers()
    }

    suspend fun setProviderCredential(id: String, apiKey: String): ProvidersResponse {
        requireKnownProvider(id)
        if (apiKey.isBlank()) throw AppException.BadRequest("API key must not be blank")
        DotEnv.set(apiKeyEnvVar(id), apiKey.trim())
        return providers()
    }

    suspend fun clearProviderCredential(id: String): ProvidersResponse {
        requireKnownProvider(id)
        DotEnv.set(apiKeyEnvVar(id), "")
        return providers()
    }

    private fun hasApiKey(providerId: String): Boolean = !DotEnv.get(apiKeyEnvVar(providerId)).isNullOrBlank()

    private fun apiKeyEnvVar(providerId: String) = "${providerId.uppercase()}_API_KEY"

    private fun requireKnownProvider(id: String) {
        if (providers.available().none { it.id == id }) {
            throw AppException.BadRequest("Unknown provider: $id")
        }
    }
}
