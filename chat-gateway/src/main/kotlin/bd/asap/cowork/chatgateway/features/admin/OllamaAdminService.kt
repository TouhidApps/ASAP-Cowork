package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.DotEnv
import bd.asap.cowork.llmgateway.OllamaLlmProvider
import bd.asap.cowork.toolintegrations.ProcessRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Backs the admin panel's "local AI model" section: checks whether Ollama
 * is installed and reachable, lists locally pulled models, suggests models
 * sized to this machine's RAM, and drives both the Ollama install script
 * ([installOllama]) and model pulls ([pullModel]) in the background so the
 * triggering HTTP request returns immediately (both can take minutes).
 * Deliberately two separate progress fields ([OllamaStatus.install] vs.
 * [OllamaStatus.pull]) rather than one shared one — installing Ollama and
 * downloading a model are different actions the UI shows in different
 * places, and conflating them would make a stale "done" from one look like
 * it belongs to the other.
 *
 * Talks to Ollama over plain HTTP (JDK's own HttpClient — no new
 * dependency) rather than shelling out to the `ollama` CLI, since the CLI
 * may not be on PATH even when the server is running as a service. Model
 * switching goes straight through the same [OllamaLlmProvider] instance
 * registered in `LlmProviderRegistry`, so it takes effect on the very next
 * chat message. The chosen model is also persisted to the `.env` file (key
 * `OLLAMA_MODEL`, via [DotEnv]) so it survives a server restart — DI reads
 * it back when constructing [OllamaLlmProvider].
 */
class OllamaAdminService(private val provider: OllamaLlmProvider) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    // Shared by both background jobs (install script, model pull) — they
    // never run concurrently in practice (the UI only offers a pull once
    // Ollama is already installed), but there's no reason for two pools.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var installProgress: OllamaInstallProgress? = null
    @Volatile private var pullProgress: OllamaPullProgress? = null

    suspend fun status(): OllamaStatus = withContext(Dispatchers.IO) {
        val installedModels = fetchTags()
        val installed = installedModels != null
        val memoryGb = systemMemoryGb()
        OllamaStatus(
            installed = installed,
            systemMemoryGb = memoryGb,
            installedModels = installedModels ?: emptyList(),
            currentModel = provider.currentModel(),
            suggestedModels = suggestedModels(memoryGb),
            installInstructions = if (installed) null else installInstructions(),
            canAutoInstall = canAutoInstallOllama(),
            install = installProgress,
            pull = pullProgress,
        )
    }

    /**
     * Runs the official install script in the background (mirrors
     * [pullModel]'s "kick off and let the UI poll [status]" shape) so the
     * triggering HTTP request returns immediately — the script can take a
     * minute or two. Only available on macOS/Linux, where Ollama ships a
     * scriptable installer; Windows only has a GUI download (see
     * [installInstructions]).
     */
    fun installOllama() {
        if (!canAutoInstallOllama()) {
            throw AppException.BadRequest("Automatic install isn't available on this OS — see the manual instructions.")
        }
        if (installProgress?.status == "installing") {
            throw AppException.BadRequest("An install is already in progress.")
        }
        installProgress = OllamaInstallProgress("installing", "Starting…")
        scope.launch { runInstall() }
    }

    private suspend fun runInstall() {
        val (success, output) = ProcessRunner.run(
            command = listOf("sh", "-c", "curl -fsSL https://ollama.com/install.sh | sh"),
            workDir = File(System.getProperty("user.home")),
            timeoutSeconds = 300,
            maxOutputChars = 2_000,
            progressPrefix = "Installing Ollama",
            onProgress = { line -> installProgress = OllamaInstallProgress("installing", line.removePrefix("Installing Ollama — ")) },
        )

        // On macOS the script downloads/unpacks Ollama.app itself (no sudo
        // needed) and only shells out to sudo afterward for a convenience
        // CLI symlink — that step fails non-interactively (no TTY for the
        // password prompt) even though the app installed fine, so the
        // script's own exit code isn't trustworthy there. Check for the app
        // directly instead, and if it's there, launch it with `open` (a
        // normal GUI launch, not sudo) — that's what actually starts
        // Ollama's background server on macOS, no CLI symlink required.
        val macAppInstalled = isMac() && File("/Applications/Ollama.app").exists()
        if (macAppInstalled) {
            ProcessRunner.run(
                command = listOf("open", "-a", "Ollama"),
                workDir = File(System.getProperty("user.home")),
                timeoutSeconds = 15,
                maxOutputChars = 500,
                progressPrefix = "Starting Ollama",
            )
        }

        installProgress = when {
            success || macAppInstalled -> OllamaInstallProgress("done", "Ollama installed — checking whether it's reachable yet…")
            else -> OllamaInstallProgress("error", output.takeLast(500))
        }
    }

    private fun canAutoInstallOllama(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return os.contains("mac") || os.contains("linux")
    }

    private fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

    suspend fun setModel(model: String) {
        if (model.isBlank()) throw AppException.BadRequest("Model name must not be blank")
        val trimmed = model.trim()
        provider.setModel(trimmed)
        DotEnv.set("OLLAMA_MODEL", trimmed)
    }

    /** Removes a pulled model from disk — these can be several GB each, worth being able to reclaim. */
    suspend fun deleteModel(model: String): Unit = withContext(Dispatchers.IO) {
        performDelete(model)
    }

    /**
     * Removes several models in one go (the Storage cleanup section's
     * multi-select). Best-effort — one failure doesn't stop the rest, same
     * spirit as [bd.asap.cowork.chatgateway.features.admin.WorkspaceService.cleanup]
     * deleting whatever files it can. Whichever ones didn't actually delete
     * simply reappear in the refreshed [OllamaStatus] this returns.
     */
    suspend fun deleteModels(models: List<String>): OllamaStatus = withContext(Dispatchers.IO) {
        models.forEach { model -> runCatching { performDelete(model) } }
        status()
    }

    private fun performDelete(model: String) {
        if (model.isBlank()) throw AppException.BadRequest("Model name must not be blank")

        val request = HttpRequest.newBuilder(URI.create("${provider.host()}/api/delete"))
            .header("Content-Type", "application/json")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(buildJsonObject { put("model", model) }.toString()))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw AppException.UpstreamError("Couldn't reach Ollama to delete '$model': ${e.message}")
        }
        if (response.statusCode() !in 200..299) {
            throw AppException.UpstreamError("Failed to delete '$model': HTTP ${response.statusCode()} ${response.body()}")
        }
    }

    fun pullModel(model: String) {
        if (model.isBlank()) throw AppException.BadRequest("Model name must not be blank")
        if (pullProgress?.status == "pulling") {
            throw AppException.BadRequest("A pull is already in progress for '${pullProgress?.model}'")
        }
        val trimmed = model.trim()
        pullProgress = OllamaPullProgress(trimmed, "pulling", 0, "Starting…")
        scope.launch { runPull(trimmed) }
    }

    private fun runPull(model: String) {
        try {
            val request = HttpRequest.newBuilder(URI.create("${provider.host()}/api/pull"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        buildJsonObject {
                            put("model", model)
                            put("stream", true)
                        }.toString(),
                    ),
                )
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
            if (response.statusCode() !in 200..299) {
                pullProgress = OllamaPullProgress(model, "error", null, "Ollama returned HTTP ${response.statusCode()}")
                return
            }

            response.body().use { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: ""
                    val total = obj["total"]?.jsonPrimitive?.longOrNull
                    val completed = obj["completed"]?.jsonPrimitive?.longOrNull
                    val percent = if (total != null && total > 0 && completed != null) {
                        ((completed * 100) / total).toInt()
                    } else {
                        pullProgress?.percent
                    }
                    pullProgress = OllamaPullProgress(model, "pulling", percent, status)
                }
            }

            pullProgress = OllamaPullProgress(model, "done", 100, "Pulled $model")
        } catch (e: Exception) {
            pullProgress = OllamaPullProgress(model, "error", null, e.message ?: "Pull failed")
        }
    }

    private fun fetchTags(): List<OllamaModelInfo>? = try {
        val request = HttpRequest.newBuilder(URI.create("${provider.host()}/api/tags"))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            null
        } else {
            val obj = json.parseToJsonElement(response.body()).jsonObject
            obj["models"]?.jsonArray?.map { element ->
                val model = element.jsonObject
                val name = model["name"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                OllamaModelInfo(
                    name = name,
                    sizeBytes = model["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                    supportsTools = supportsTools(name),
                )
            } ?: emptyList()
        }
    } catch (e: Exception) {
        null
    }

    // Ollama derives a model's capabilities from its chat template, so this
    // is authoritative (unlike guessing from the name) — e.g. the gemma3
    // family reports ["completion", "vision"] with no "tools" entry, and any
    // tool-enabled request against it is rejected outright by Ollama itself.
    // Cached since a given tag's capabilities never change without a re-pull.
    private val toolSupportCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun supportsTools(modelName: String): Boolean = toolSupportCache.getOrPut(modelName) {
        try {
            val request = HttpRequest.newBuilder(URI.create("${provider.host()}/api/show"))
                .timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject { put("model", modelName) }.toString()))
                .header("Content-Type", "application/json")
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                true
            } else {
                val capabilities = json.parseToJsonElement(response.body()).jsonObject["capabilities"]?.jsonArray
                capabilities?.any { it.jsonPrimitive.contentOrNull == "tools" } ?: true
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun systemMemoryGb(): Double {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val totalBytes = (osBean as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize ?: 0L
        return totalBytes / (1024.0 * 1024.0 * 1024.0)
    }

    private fun suggestedModels(systemMemoryGb: Double): List<SuggestedOllamaModel> =
        CATALOG.map { entry ->
            SuggestedOllamaModel(
                name = entry.name,
                approxSizeGb = entry.approxSizeGb,
                minRamGb = entry.minRamGb,
                recommended = entry.name == OllamaLlmProvider.DEFAULT_MODEL && systemMemoryGb >= entry.minRamGb,
                fitsSystemMemory = systemMemoryGb >= entry.minRamGb,
                supportsTools = entry.supportsTools,
            )
        }

    private fun installInstructions(): List<String> {
        val os = System.getProperty("os.name").lowercase()
        val installCmd = when {
            os.contains("mac") || os.contains("linux") -> "curl -fsSL https://ollama.com/install.sh | sh"
            os.contains("win") -> "Download and run the installer from https://ollama.com/download/windows"
            else -> "Visit https://ollama.com/download for install instructions"
        }
        return listOf(
            installCmd,
            "ollama serve   # skip if it's already running as a background service",
            "ollama pull ${provider.currentModel()}",
        )
    }

    private data class CatalogEntry(
        val name: String,
        val approxSizeGb: Double,
        val minRamGb: Double,
        /** The gemma3 family has no tool-calling template in Ollama — a tool-enabled chat request against one fails outright ("does not support tools"), breaking every agent that reads/writes the workspace. */
        val supportsTools: Boolean = true,
    )

    companion object {
        private val CATALOG = listOf(
            CatalogEntry("gemma3:1b", 0.8, 4.0, supportsTools = false),
            CatalogEntry("llama3.2:1b", 1.3, 4.0),
            CatalogEntry("llama3.2:3b", 2.0, 8.0),
            CatalogEntry("gemma3:4b", 3.3, 8.0, supportsTools = false),
            CatalogEntry("llama3.1:8b", 4.7, 8.0),
            CatalogEntry("qwen2.5:7b", 4.7, 8.0),
            CatalogEntry("mistral-nemo:12b", 7.1, 16.0),
            CatalogEntry("gemma3:12b", 8.1, 16.0, supportsTools = false),
            CatalogEntry("gemma3:27b", 17.0, 32.0, supportsTools = false),
            CatalogEntry("qwen3.6:27b", 17.0, 32.0),
            CatalogEntry("llama3.1:70b", 40.0, 64.0),
        )
    }
}
