package bd.asap.cowork.llmgateway

import com.google.genai.Client
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import com.google.genai.types.Content
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Base64

/**
 * Gemini provider, via Google's official Java SDK — its own native
 * function-calling loop, unlike OpenAI/Ollama which share
 * [OpenAiCompatLoop]. [apiKeyProvider] is re-resolved on every request,
 * same reasoning as [AnthropicLlmProvider].
 */
class GeminiLlmProvider(
    private val apiKeyProvider: () -> String?,
    private val model: String = "gemini-3.1-pro-preview",
    private val maxTokens: Int = 4096,
) : LlmProvider {
    override val id: String = "gemini"

    private fun client(): Client {
        val apiKey = apiKeyProvider()
            ?: throw LlmProviderException("Gemini API key is not set. Add it from the admin panel, or set GEMINI_API_KEY.")
        return Client.builder().apiKey(apiKey).build()
    }

    override fun streamComplete(systemPrompt: String?, messages: List<ChatMessage>): Flow<String> = flow {
        val client = client()
        val configBuilder = GenerateContentConfig.builder().maxOutputTokens(maxTokens)
        if (systemPrompt != null) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
        }
        val contents = messages.map { it.toContent() }

        try {
            client.models.generateContentStream(model, contents, configBuilder.build()).use { stream ->
                for (chunk in stream) {
                    chunk.parts()?.forEach { part ->
                        part.text().orElse(null)?.let { text -> emit(text) }
                    }
                }
            }
        } catch (e: ApiException) {
            throw LlmProviderException("Gemini request failed: ${e.message}", e)
        } catch (e: GenAiIOException) {
            throw LlmProviderException("Gemini request failed: ${e.message}", e)
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
        val client = client()
        val functionDeclarations = tools.map { spec ->
            FunctionDeclaration.builder()
                .name(spec.name)
                .description(spec.description)
                .parametersJsonSchema(spec.parametersSchema)
                .build()
        }
        val config = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .maxOutputTokens(maxTokens)
            .tools(Tool.builder().functionDeclarations(functionDeclarations).build())
            .build()

        val initialParts = listOf(Part.fromText(userMessage)) + images.map { it.toPart() }
        val messages = (history.map { it.toContent() } + Content.builder().role("user").parts(initialParts).build()).toMutableList()

        repeat(MAX_TOOL_ITERATIONS) {
            val allParts = mutableListOf<Part>()
            val functionCalls = mutableListOf<com.google.genai.types.FunctionCall>()

            try {
                client.models.generateContentStream(model, messages, config).use { stream ->
                    for (chunk in stream) {
                        val parts = chunk.parts()?.toList() ?: emptyList()
                        allParts += parts
                        parts.forEach { part ->
                            part.text().orElse(null)?.let { text -> send(AgentStreamEvent.TextDelta(text)) }
                        }
                        functionCalls += chunk.functionCalls()?.toList() ?: emptyList()
                    }
                }
            } catch (e: ApiException) {
                throw LlmProviderException("Gemini request failed: ${e.message}", e)
            } catch (e: GenAiIOException) {
                throw LlmProviderException("Gemini request failed: ${e.message}", e)
            }

            if (functionCalls.isEmpty()) {
                return@channelFlow
            }

            if (allParts.isNotEmpty()) {
                messages += Content.builder().role("model").parts(allParts).build()
            }

            val responseParts = functionCalls.map { call ->
                val name = call.name().orElse("unknown")
                @Suppress("UNCHECKED_CAST")
                val args = call.args().orElse(emptyMap()) as Map<String, Any?>

                send(AgentStreamEvent.ToolActivity(name, describe(name, args), ToolActivityStatus.STARTED))
                val result = executor.execute(name, args) { progress ->
                    send(AgentStreamEvent.ToolActivity(name, progress, ToolActivityStatus.STARTED))
                }
                send(
                    AgentStreamEvent.ToolActivity(
                        name,
                        describe(name, args),
                        if (result.isError) ToolActivityStatus.FAILED else ToolActivityStatus.FINISHED,
                    ),
                )
                emitMediaNotes(result) { send(it) }
                Part.fromFunctionResponse(name, mapOf("result" to result.summary, "isError" to result.isError))
            }
            messages += Content.builder().role("user").parts(responseParts).build()
        }

        send(AgentStreamEvent.TextDelta("\n\n(Reached the tool-call limit before finishing.)"))
    }.flowOn(Dispatchers.IO)

    private fun ChatMessage.toContent(): Content =
        Content.builder().role(if (role == ChatRole.ASSISTANT) "model" else "user").parts(Part.fromText(content)).build()

    private fun ImageAttachment.toPart(): Part = Part.fromBytes(Base64.getDecoder().decode(base64Data), mimeType)

    private companion object {
        const val MAX_TOOL_ITERATIONS = 12
    }
}
