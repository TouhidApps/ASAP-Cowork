package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.agents.analytics.AnalyticsAgent
import bd.asap.cowork.agents.android.AndroidAgent
import bd.asap.cowork.agents.architecture.ArchitectureAdvisorAgent
import bd.asap.cowork.agents.backend.BackendAgent
import bd.asap.cowork.agents.branding.BrandingAgent
import bd.asap.cowork.agents.cicd.CicdAgent
import bd.asap.cowork.agents.debugging.DebuggingAgent
import bd.asap.cowork.agents.documentation.DocumentationAgent
import bd.asap.cowork.agents.flutter.FlutterAgent
import bd.asap.cowork.agents.generalpurpose.GeneralPurposeAgent
import bd.asap.cowork.agents.ios.IosAgent
import bd.asap.cowork.agents.kmp.KmpAgent
import bd.asap.cowork.agents.landingpage.LandingPageAgent
import bd.asap.cowork.agents.legal.LegalDocAgent
import bd.asap.cowork.agents.notes.NotesAgent
import bd.asap.cowork.agents.performance.PerformanceAgent
import bd.asap.cowork.agents.publishing.PublishingAgent
import bd.asap.cowork.agents.reactnative.ReactNativeAgent
import bd.asap.cowork.agents.requirements.RequirementsAgent
import bd.asap.cowork.agents.scaffolding.ScaffoldingAgent
import bd.asap.cowork.agents.security.SecurityReviewAgent
import bd.asap.cowork.agents.storeasset.StoreAssetAgent
import bd.asap.cowork.agents.techstack.TechStackAgent
import bd.asap.cowork.agents.testing.TestingAgent
import bd.asap.cowork.agents.workspace.WorkspaceAgent
import bd.asap.cowork.chatgateway.config.DotEnv
import bd.asap.cowork.chatgateway.config.FirebaseCredentialsStore
import bd.asap.cowork.chatgateway.config.ProviderCredentialsStore
import bd.asap.cowork.chatgateway.config.ToolchainSettingsStore
import bd.asap.cowork.chatgateway.config.WorkspaceSettingsStore
import bd.asap.cowork.chatgateway.features.admin.AdminService
import bd.asap.cowork.chatgateway.features.admin.FirebaseCliService
import bd.asap.cowork.chatgateway.features.admin.FirebaseService
import bd.asap.cowork.chatgateway.features.admin.AllowedHostsService
import bd.asap.cowork.chatgateway.features.admin.OllamaAdminService
import bd.asap.cowork.chatgateway.features.admin.ToolchainService
import bd.asap.cowork.chatgateway.features.admin.UsageService
import bd.asap.cowork.chatgateway.features.admin.WorkspaceService
import bd.asap.cowork.chatgateway.features.notes.NoteService
import bd.asap.cowork.chatgateway.features.plan.PlanService
import bd.asap.cowork.chatgateway.features.project.ProjectFilesService
import bd.asap.cowork.contextstore.ApiUsageRecord
import bd.asap.cowork.contextstore.ApiUsageRepository
import bd.asap.cowork.contextstore.ContextDatabase
import bd.asap.cowork.contextstore.ConversationRepository
import bd.asap.cowork.contextstore.NoteRepository
import bd.asap.cowork.contextstore.SettingsRepository
import bd.asap.cowork.firebase.FirebaseCredentialsRegistry
import bd.asap.cowork.llmgateway.AnthropicLlmProvider
import bd.asap.cowork.llmgateway.GeminiLlmProvider
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import bd.asap.cowork.llmgateway.LlmUsage
import bd.asap.cowork.llmgateway.OllamaLlmProvider
import bd.asap.cowork.llmgateway.OpenAiLlmProvider
import bd.asap.cowork.llmgateway.UsageListener
import bd.asap.cowork.orchestrator.AgentRegistry
import bd.asap.cowork.orchestrator.IntentClassifier
import bd.asap.cowork.orchestrator.Orchestrator
import bd.asap.cowork.orchestrator.ProjectContext
import bd.asap.cowork.toolintegrations.ToolchainPathsRegistry
import bd.asap.cowork.workspacehistory.WorkspaceHistoryService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.nio.file.Paths

/**
 * Every agent module contributes itself to this registry. Adding a new
 * agent later is: register it here (or, once the orchestrator does real
 * fingerprint-based activation, only when its stack signature matches).
 */
fun appModule(contextDatabase: ContextDatabase) = module {
    single { contextDatabase }
    single { contextDatabase.database }
    single { SettingsRepository(get()) }
    single { NoteRepository(get()) }
    single { ConversationRepository(get()) }
    single { NoteService(get()) }
    single { ProviderCredentialsStore(get()) }
    single { ApiUsageRepository(get()) }
    single {
        // Shared by every provider single below — persists straight to SQLite so the admin
        // panel's usage tab has real data with no llm-gateway -> context-store dependency (see
        // UsageListener's doc comment).
        val repository = get<ApiUsageRepository>()
        UsageListener { usage: LlmUsage ->
            repository.record(
                ApiUsageRecord(
                    provider = usage.providerId,
                    model = usage.model,
                    inputTokens = usage.inputTokens,
                    outputTokens = usage.outputTokens,
                ),
            )
        }
    }
    single { AnthropicLlmProvider(apiKeyProvider = { DotEnv.get("ANTHROPIC_API_KEY") }, usageListener = get()) }
    single { OpenAiLlmProvider(apiKeyProvider = { DotEnv.get("OPENAI_API_KEY") }, usageListener = get()) }
    single { GeminiLlmProvider(apiKeyProvider = { DotEnv.get("GEMINI_API_KEY") }, usageListener = get()) }
    single {
        OllamaLlmProvider(
            hostProvider = { DotEnv.get("OLLAMA_HOST") ?: OllamaLlmProvider.DEFAULT_HOST },
            // The admin panel's model picker writes straight back to this
            // same OLLAMA_MODEL key (see OllamaAdminService.setModel), so
            // this is the one source of truth — no separate DB-persisted
            // override to layer on top of it.
            initialModel = { DotEnv.get("OLLAMA_MODEL") ?: OllamaLlmProvider.DEFAULT_MODEL },
            usageListener = get(),
        )
    }
    single {
        val allProviders = listOf(get<AnthropicLlmProvider>(), get<OpenAiLlmProvider>(), get<GeminiLlmProvider>(), get<OllamaLlmProvider>())
        // The active provider survives restarts — read back whatever was
        // last persisted via AdminService.setProvider, falling back to the
        // first provider the same way LlmProviderRegistry's own default does.
        val persistedId = runBlocking { get<ProviderCredentialsStore>().getCurrentProvider() }
        val currentId = persistedId?.takeIf { id -> allProviders.any { it.id == id } } ?: allProviders.first().id
        LlmProviderRegistry(providers = allProviders, currentId = currentId)
    }
    single { WorkspaceSettingsStore() }
    single {
        val store = get<WorkspaceSettingsStore>()
        // Default to a workspace/ subfolder of this repo, never the repo root
        // itself — agents must never write into the orchestrator's own
        // Gradle modules. Confirming a real project directory (workspace
        // routes) overrides this and persists across restarts.
        val defaultRoot = Paths.get(System.getProperty("user.dir"), "workspace")
        val initialRoot = store.readRootPath()?.let(Paths::get) ?: defaultRoot
        ProjectContext(initialRoot)
    }
    single { WorkspaceHistoryService(get()) }
    single { PlanService(get()) }
    single { ProjectFilesService(get()) }
    single(createdAtStart = true) {
        val store = ToolchainSettingsStore(get())
        // Eager (createdAtStart) so ToolchainPathsRegistry is seeded at
        // startup, not on the first admin request — every tool that shells
        // out reads the registry synchronously.
        ToolchainPathsRegistry.set(runBlocking { store.resolve() })
        store
    }
    single(createdAtStart = true) {
        val store = FirebaseCredentialsStore()
        FirebaseCredentialsRegistry.set(store.read())
        store
    }
    single {
        AgentRegistry().apply {
            register(RequirementsAgent(get()))
            register(ArchitectureAdvisorAgent(get()))
            register(TechStackAgent(get()))
            register(ScaffoldingAgent(get()))
            register(AndroidAgent(get()))
            register(IosAgent(get()))
            register(FlutterAgent(get()))
            register(KmpAgent(get()))
            register(ReactNativeAgent(get()))
            register(BackendAgent(get()))
            register(TestingAgent(get()))
            register(DebuggingAgent(get()))
            register(CicdAgent(get()))
            register(BrandingAgent(get()))
            register(LegalDocAgent(get()))
            register(LandingPageAgent(get()))
            register(StoreAssetAgent(get()))
            register(DocumentationAgent(get()))
            register(AnalyticsAgent(get()))
            register(SecurityReviewAgent(get()))
            register(PerformanceAgent(get()))
            register(PublishingAgent(get()))
            register(NotesAgent(get(), get()))
            register(WorkspaceAgent())
            // Catch-all: registered last so every specialized capability above
            // gets first pick; only IntentClassifier's unmatched-reply fallback
            // and explicit Capability.GENERAL tasks reach it.
            register(GeneralPurposeAgent(get()))
        }
    }
    single { IntentClassifier(get()) }
    single { Orchestrator(get(), get(), get()) }
    single { WorkspaceService(get(), get()) }
    single { ToolchainService(get()) }
    single { FirebaseService(get()) }
    single { FirebaseCliService() }
    single { OllamaAdminService(get()) }
    single { AllowedHostsService() }
    single { UsageService(get()) }
    single { AdminService(get(), get(), get(), get(), get()) }
}

fun Application.configureDI(contextDatabase: ContextDatabase) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(contextDatabase))
    }
}
