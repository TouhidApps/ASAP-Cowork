package bd.asap.cowork.llmgateway

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionStreamOptions
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

/**
 * Talks to a local Ollama server through its OpenAI-compatible endpoint
 * (`$host/v1`), reusing [OpenAiCompatLoop] instead of hand-rolling the same
 * streaming/tool-call logic twice — Ollama translates OpenAI-shaped tool
 * calls to/from its native format for tool-capable models (llama3.1,
 * qwen2.5, mistral-nemo, ...). The API key is a required-but-ignored
 * placeholder; Ollama doesn't check it.
 *
 * [hostProvider] is re-resolved on every request (env var, no live switcher
 * needed for a host). The active model is mutable at runtime via
 * [setModel] — the admin panel's Ollama panel calls it directly on this
 * same instance — defaulting from [initialModel] (an env var) so a fresh
 * server still has a sane model with no admin-panel visit required.
 */
class OllamaLlmProvider(
    private val hostProvider: () -> String = { DEFAULT_HOST },
    initialModel: () -> String = { DEFAULT_MODEL },
    private val maxTokens: Long = 4096,
    private val usageListener: UsageListener = UsageListener {},
) : LlmProvider {
    override val id: String = "ollama"
    override val requiresApiKey: Boolean = false

    @Volatile
    private var model: String = initialModel()

    fun host(): String = hostProvider()

    fun currentModel(): String = model

    fun setModel(model: String) {
        this.model = model
    }

    private fun client(): OpenAIClient =
        OpenAIOkHttpClient.builder().baseUrl("${hostProvider().trimEnd('/')}/v1").apiKey("ollama").build()

    // fast is ignored here — Ollama is a single locally-hosted model with no
    // billed-per-token cost, so there's no cheaper tier to route to.
    override fun streamComplete(systemPrompt: String?, messages: List<ChatMessage>, fast: Boolean): Flow<String> = flow {
        val builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(model))
            .maxCompletionTokens(maxTokens)
            .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build())

        if (systemPrompt != null) {
            builder.addMessage(ChatCompletionSystemMessageParam.builder().content(systemPrompt).build())
        }
        for (message in messages) {
            when (message.role) {
                ChatRole.USER -> builder.addUserMessage(message.content)
                ChatRole.ASSISTANT ->
                    builder.addMessage(ChatCompletionAssistantMessageParam.builder().content(message.content).build())
            }
        }

        var lastUsage: com.openai.models.completions.CompletionUsage? = null
        try {
            client().chat().completions().createStreaming(builder.build()).use { response ->
                val iterator = response.stream().iterator()
                while (iterator.hasNext()) {
                    val chunk = iterator.next()
                    chunk.usage().ifPresent { lastUsage = it }
                    val choice = chunk.choices().firstOrNull() ?: continue
                    choice.delta().content().orElse(null)?.let { text -> emit(text) }
                }
            }
        } catch (e: IOException) {
            throw LlmProviderException("Couldn't reach Ollama at ${hostProvider()}. Make sure it's running.", e)
        }
        lastUsage?.let { usage -> usageListener.onUsage(LlmUsage(id, model, usage.promptTokens(), usage.completionTokens())) }
    }.flowOn(Dispatchers.IO)

    override fun runAgenticLoop(
        systemPrompt: String,
        userMessage: String,
        tools: List<ToolSpec>,
        executor: ToolExecutor,
        describe: (String, Map<String, Any?>) -> String,
        images: List<ImageAttachment>,
        history: List<ChatMessage>,
    ): Flow<AgentStreamEvent> = channelFlow {
        try {
            with(OpenAiCompatLoop) {
                runOpenAiCompatLoop(
                    client = client(),
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                    tools = tools,
                    executor = executor,
                    errorPrefix = "Ollama request failed",
                    describe = describe,
                    images = images,
                    history = history,
                    providerId = id,
                    modelName = model,
                    usageListener = usageListener,
                ) {
                    model(ChatModel.of(model))
                    maxCompletionTokens(maxTokens)
                }
            }
        } catch (e: IOException) {
            throw LlmProviderException("Couldn't reach Ollama at ${hostProvider()}. Make sure it's running.", e)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val DEFAULT_HOST = "http://localhost:11434"
        const val DEFAULT_MODEL = "llama3.1:8b"
    }
}
