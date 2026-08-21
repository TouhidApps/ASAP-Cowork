package bd.asap.cowork.llmgateway

import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.OpenAIException
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionStreamOptions
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.completions.CompletionUsage
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Shared streaming tool-use loop for any OpenAI-Chat-Completions-compatible
 * backend — used by both [OpenAiLlmProvider] (the real API) and
 * [OllamaLlmProvider] (Ollama's OpenAI-compatible endpoint), since they're
 * the same wire format. Tool-call arguments arrive as string fragments
 * spread across streamed chunks and only become valid JSON once fully
 * concatenated, so that accumulation logic only needs to exist here once.
 */
internal object OpenAiCompatLoop {
    private const val MAX_TOOL_ITERATIONS = 12
    private val json = Json { ignoreUnknownKeys = true }

    private class PendingToolCall {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    suspend fun ProducerScope<AgentStreamEvent>.runOpenAiCompatLoop(
        client: OpenAIClient,
        systemPrompt: String,
        userMessage: String,
        tools: List<ToolSpec>,
        executor: ToolExecutor,
        errorPrefix: String,
        describe: (String, Map<String, Any?>) -> String,
        images: List<ImageAttachment>,
        history: List<ChatMessage>,
        providerId: String,
        modelName: String,
        usageListener: UsageListener,
        buildParams: ChatCompletionCreateParams.Builder.() -> Unit,
    ) {
        val functionTools = tools.map { spec ->
            FunctionDefinition.builder()
                .name(spec.name)
                .description(spec.description)
                .parameters(
                    FunctionParameters.builder()
                        .putAllAdditionalProperties(spec.parametersSchema.mapValues { JsonValue.from(it.value) })
                        .build(),
                )
                .build()
        }

        val initialUserMessage = ChatCompletionUserMessageParam.builder()
        if (images.isEmpty()) {
            initialUserMessage.content(userMessage)
        } else {
            initialUserMessage.contentOfArrayOfContentParts(
                listOf(ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(userMessage).build())) +
                    images.map { it.toContentPart() },
            )
        }
        val messages = mutableListOf(
            ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content(systemPrompt).build()),
        )
        messages += history.map { it.toMessageParam() }
        messages += ChatCompletionMessageParam.ofUser(initialUserMessage.build())

        repeat(MAX_TOOL_ITERATIONS) {
            val textThisTurn = StringBuilder()
            val pendingCalls = sortedMapOf<Long, PendingToolCall>()
            var usedTools = false
            var lastUsage: CompletionUsage? = null

            try {
                val params = ChatCompletionCreateParams.builder()
                    .apply(buildParams)
                    .apply { functionTools.forEach { addFunctionTool(it) } }
                    .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build())
                    .messages(messages)
                    .build()

                client.chat().completions().createStreaming(params).use { response ->
                    val iterator = response.stream().iterator()
                    while (iterator.hasNext()) {
                        val chunk = iterator.next()
                        chunk.usage().ifPresent { lastUsage = it }
                        val choice = chunk.choices().firstOrNull() ?: continue
                        val delta = choice.delta()

                        delta.content().orElse(null)?.let { text ->
                            textThisTurn.append(text)
                            send(AgentStreamEvent.TextDelta(text))
                        }

                        delta.toolCalls().orElse(null)?.forEach { call ->
                            val pending = pendingCalls.getOrPut(call.index()) { PendingToolCall() }
                            call.id().orElse(null)?.let { pending.id = it }
                            call.function().orElse(null)?.let { fn ->
                                fn.name().orElse(null)?.let { pending.name = it }
                                fn.arguments().orElse(null)?.let { pending.arguments.append(it) }
                            }
                        }

                        if (choice.finishReason().orElse(null) == ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS) {
                            usedTools = true
                        }
                    }
                }
            } catch (e: OpenAIException) {
                if (e.message?.contains("does not support tools") == true) {
                    throw LlmProviderException(
                        "\"$modelName\" doesn't support tool calling, so it can't read or write anything in your workspace. Pick a different model in Settings → Local AI model (Ollama).",
                        e,
                    )
                }
                throw LlmProviderException("$errorPrefix: ${e.message}", e)
            }

            lastUsage?.let { usage ->
                usageListener.onUsage(LlmUsage(providerId, modelName, usage.promptTokens(), usage.completionTokens()))
            }

            if (!usedTools || pendingCalls.isEmpty()) {
                return
            }

            val assistantMessage = ChatCompletionAssistantMessageParam.builder().apply {
                if (textThisTurn.isNotEmpty()) content(textThisTurn.toString())
            }
            val resolvedCalls = pendingCalls.entries.sortedBy { it.key }.map { (index, call) ->
                val id = call.id ?: "call_$index"
                val name = call.name.orEmpty()
                val arguments = call.arguments.toString()
                assistantMessage.addToolCall(
                    ChatCompletionMessageFunctionToolCall.builder()
                        .id(id)
                        .function(
                            ChatCompletionMessageFunctionToolCall.Function.builder()
                                .name(name)
                                .arguments(arguments)
                                .build(),
                        )
                        .build(),
                )
                Triple(id, name, arguments)
            }
            messages += ChatCompletionMessageParam.ofAssistant(assistantMessage.build())

            resolvedCalls.forEach { (id, name, argumentsJson) ->
                val input = parseArguments(argumentsJson)
                send(AgentStreamEvent.ToolActivity(name, describe(name, input), ToolActivityStatus.STARTED))
                val result = executor.execute(name, input) { progress ->
                    send(AgentStreamEvent.ToolActivity(name, progress, ToolActivityStatus.STARTED))
                }
                send(
                    AgentStreamEvent.ToolActivity(
                        name,
                        describe(name, input),
                        if (result.isError) ToolActivityStatus.FAILED else ToolActivityStatus.FINISHED,
                    ),
                )
                emitMediaNotes(result) { send(it) }
                messages += ChatCompletionMessageParam.ofTool(
                    ChatCompletionToolMessageParam.builder().toolCallId(id).content(result.summary).build(),
                )
            }
        }

        send(AgentStreamEvent.TextDelta("\n\n(Reached the tool-call limit before finishing.)"))
    }

    private fun ChatMessage.toMessageParam(): ChatCompletionMessageParam =
        if (role == ChatRole.ASSISTANT) {
            ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder().content(content).build())
        } else {
            ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(content).build())
        }

    private fun ImageAttachment.toContentPart(): ChatCompletionContentPart =
        ChatCompletionContentPart.ofImageUrl(
            ChatCompletionContentPartImage.builder()
                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url("data:$mimeType;base64,$base64Data").build())
                .build(),
        )

    private fun parseArguments(raw: String): Map<String, Any?> =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?.mapValues { (_, value) -> value.toKotlinValue() }
            ?: emptyMap()

    private fun JsonElement.toKotlinValue(): Any? = when (this) {
        is JsonObject -> mapValues { (_, v) -> v.toKotlinValue() }
        is JsonArray -> map { it.toKotlinValue() }
        is JsonNull -> null
        is JsonPrimitive -> longOrNull ?: doubleOrNull ?: booleanOrNull ?: content
    }
}

/** Wraps a provider SDK's own exception (or a configuration error) so callers see a plain message. */
internal class LlmProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
