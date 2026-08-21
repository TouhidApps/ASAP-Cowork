package bd.asap.cowork.llmgateway

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionStreamOptions
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * OpenAI provider, via the official Java SDK's Chat Completions API. Uses a
 * low reasoning effort since most of what this platform's agents do is
 * short, tool-scoped work — raise it if replies feel shallow on harder
 * asks. [apiKeyProvider] is re-resolved on every request, same reasoning
 * as [AnthropicLlmProvider].
 */
class OpenAiLlmProvider(
    private val apiKeyProvider: () -> String?,
    private val model: ChatModel = ChatModel.GPT_5_6_SOL,
    /** Used instead of [model] when a call passes `fast = true` (e.g. intent classification) — a short routing/classification decision doesn't need the flagship model's cost. */
    private val fastModel: ChatModel = ChatModel.GPT_5_4_MINI,
    private val maxTokens: Long = 4096,
    private val usageListener: UsageListener = UsageListener {},
) : LlmProvider {
    override val id: String = "openai"

    private fun client(): OpenAIClient {
        val apiKey = apiKeyProvider()
            ?: throw LlmProviderException("OpenAI API key is not set. Add it from the admin panel, or set OPENAI_API_KEY.")
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build()
    }

    override fun streamComplete(systemPrompt: String?, messages: List<ChatMessage>, fast: Boolean): Flow<String> = flow {
        val effectiveModel = if (fast) fastModel else model
        val builder = ChatCompletionCreateParams.builder()
            .model(effectiveModel)
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
        client().chat().completions().createStreaming(builder.build()).use { response ->
            val iterator = response.stream().iterator()
            while (iterator.hasNext()) {
                val chunk = iterator.next()
                chunk.usage().ifPresent { lastUsage = it }
                val choice = chunk.choices().firstOrNull() ?: continue
                choice.delta().content().orElse(null)?.let { text -> emit(text) }
            }
        }
        lastUsage?.let { usage -> usageListener.onUsage(LlmUsage(id, effectiveModel.asString(), usage.promptTokens(), usage.completionTokens())) }
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
        with(OpenAiCompatLoop) {
            runOpenAiCompatLoop(
                client = client(),
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                tools = tools,
                executor = executor,
                errorPrefix = "OpenAI request failed",
                describe = describe,
                images = images,
                history = history,
                providerId = id,
                modelName = model.asString(),
                usageListener = usageListener,
            ) {
                model(model)
                maxCompletionTokens(maxTokens)
                reasoningEffort(ReasoningEffort.NONE)
            }
        }
    }.flowOn(Dispatchers.IO)
}
