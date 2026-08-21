package bd.asap.cowork.chatgateway.features.admin

import kotlinx.serialization.Serializable

@Serializable
data class OllamaModelInfo(
    val name: String,
    val sizeBytes: Long,
    /** Whether Ollama reports "tools" in this model's capabilities (via `/api/show`) — models without it (e.g. the gemma3 family) reject any tool-enabled chat request outright, which breaks every agent that reads/writes the workspace. */
    val supportsTools: Boolean = true,
)

@Serializable
data class SuggestedOllamaModel(
    val name: String,
    val approxSizeGb: Double,
    val minRamGb: Double,
    val recommended: Boolean,
    val fitsSystemMemory: Boolean,
    val supportsTools: Boolean,
)

@Serializable
data class OllamaPullProgress(
    val model: String,
    val status: String,
    val percent: Int?,
    val message: String?,
)

/** Progress of running the Ollama install script itself — separate from [OllamaPullProgress], which is for a model download and only ever applies once Ollama is already installed and reachable. */
@Serializable
data class OllamaInstallProgress(
    val status: String,
    val message: String?,
)

@Serializable
data class OllamaStatus(
    val installed: Boolean,
    val systemMemoryGb: Double,
    val installedModels: List<OllamaModelInfo>,
    val currentModel: String,
    val suggestedModels: List<SuggestedOllamaModel>,
    val installInstructions: List<String>?,
    /** Whether [installOllama] can actually run the install script on this server's OS (macOS/Linux) — Windows has no scriptable installer, only a manual download link in [installInstructions]. */
    val canAutoInstall: Boolean,
    val install: OllamaInstallProgress?,
    val pull: OllamaPullProgress?,
)

@Serializable
data class SetOllamaModelRequest(val model: String)

@Serializable
data class PullOllamaModelRequest(val model: String)

@Serializable
data class DeleteOllamaModelsRequest(val models: List<String>)
