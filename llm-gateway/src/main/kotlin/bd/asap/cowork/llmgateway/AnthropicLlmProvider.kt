package bd.asap.cowork.llmgateway

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Default LLM provider: Claude Opus 5, per PLAN.md §9 (Decisions Log) — the
 * strongest current model for the agentic, multi-file coding work the
 * orchestrator and platform dev agents do.
 *
 * [apiKeyProvider] is re-resolved on every request rather than cached, so
 * an admin-panel key change (or switching which key source wins) takes
 * effect on the very next message with no restart — mirrors the prior
 * prototype's `ClaudeAgentService`, which never cached its client either.
 * Returning `null` falls back to the Anthropic SDK's own environment
 * lookup (`ANTHROPIC_API_KEY`).
 */
class AnthropicLlmProvider(
    private val apiKeyProvider: () -> String? = { null },
    private val model: String = "claude-opus-5",
    private val maxTokens: Long = 8000,
) : LlmProvider {
    override val id: String = "anthropic"

    private fun client(): AnthropicClient {
        val apiKey = apiKeyProvider()
        return if (apiKey != null) {
            AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        } else {
            AnthropicOkHttpClient.fromEnv()
        }
    }

    override fun streamComplete(systemPrompt: String?, messages: List<ChatMessage>): Flow<String> =
        channelFlow {
            val builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)

            if (systemPrompt != null) {
                builder.system(systemPrompt)
            }
            for (message in messages) {
                when (message.role) {
                    ChatRole.USER -> builder.addUserMessage(message.content)
                    ChatRole.ASSISTANT -> builder.addAssistantMessage(message.content)
                }
            }

            client().messages().createStreaming(builder.build()).use { streamResponse ->
                streamResponse.stream().forEach { event ->
                    event.contentBlockDelta().ifPresent { contentBlockDelta ->
                        contentBlockDelta.delta().text().ifPresent { textDelta ->
                            trySend(textDelta.text())
                        }
                    }
                }
            }
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
        val toolDefinitions = tools.map { spec ->
            @Suppress("UNCHECKED_CAST")
            val properties = spec.parametersSchema["properties"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val required = spec.parametersSchema["required"] as? List<String> ?: emptyList()

            Tool.builder()
                .name(spec.name)
                .description(spec.description)
                .inputSchema(
                    Tool.InputSchema.builder()
                        .type(JsonValue.from("object"))
                        .properties(
                            Tool.InputSchema.Properties.builder()
                                .putAllAdditionalProperties(properties.mapValues { JsonValue.from(it.value) })
                                .build(),
                        )
                        .required(required)
                        .build(),
                )
                .build()
        }

        val initialMessage = MessageParam.builder().role(MessageParam.Role.USER)
        if (images.isEmpty()) {
            initialMessage.content(userMessage)
        } else {
            initialMessage.contentOfBlockParams(
                listOf(ContentBlockParam.ofText(TextBlockParam.builder().text(userMessage).build())) +
                    images.map { it.toContentBlock() },
            )
        }
        val messages = (history.map { it.toMessageParam() } + initialMessage.build()).toMutableList()

        repeat(MAX_TOOL_ITERATIONS) {
            val accumulator = MessageAccumulator.create()
            val builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .messages(messages)
            toolDefinitions.forEach { builder.addTool(it) }

            client().messages().createStreaming(builder.build()).use { streamResponse ->
                streamResponse.stream().forEach { event ->
                    accumulator.accumulate(event)
                    event.contentBlockDelta().ifPresent { delta ->
                        delta.delta().text().ifPresent { textDelta -> trySend(AgentStreamEvent.TextDelta(textDelta.text())) }
                    }
                }
            }

            val finalMessage = accumulator.message()
            if (finalMessage.stopReason().orElse(null) != StopReason.TOOL_USE) {
                return@channelFlow
            }

            messages += finalMessage.toParam()

            val toolResults = finalMessage.content().filter { it.isToolUse() }.map { block ->
                val toolUse = block.asToolUse()
                @Suppress("UNCHECKED_CAST")
                val input = toolUse._input().convert(Map::class.java) as Map<String, Any?>

                send(AgentStreamEvent.ToolActivity(toolUse.name(), describe(toolUse.name(), input), ToolActivityStatus.STARTED))
                val result = executor.execute(toolUse.name(), input) { progress ->
                    send(AgentStreamEvent.ToolActivity(toolUse.name(), progress, ToolActivityStatus.STARTED))
                }
                send(
                    AgentStreamEvent.ToolActivity(
                        toolUse.name(),
                        describe(toolUse.name(), input),
                        if (result.isError) ToolActivityStatus.FAILED else ToolActivityStatus.FINISHED,
                    ),
                )
                emitMediaNotes(result) { send(it) }

                ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(result.summary)
                        .isError(result.isError)
                        .build(),
                )
            }

            messages += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(toolResults).build()
        }

        send(AgentStreamEvent.TextDelta("\n\n(Reached the tool-call limit before finishing.)"))
    }.flowOn(Dispatchers.IO)

    private fun ImageAttachment.toContentBlock(): ContentBlockParam =
        ContentBlockParam.ofImage(
            ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(mimeType))
                        .data(base64Data)
                        .build(),
                )
                .build(),
        )

    private fun ChatMessage.toMessageParam(): MessageParam =
        MessageParam.builder()
            .role(if (role == ChatRole.ASSISTANT) MessageParam.Role.ASSISTANT else MessageParam.Role.USER)
            .content(content)
            .build()

    private companion object {
        const val MAX_TOOL_ITERATIONS = 12
    }
}
