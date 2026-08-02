package bd.asap.cowork.toolintegrations.buildrunner

import bd.asap.cowork.llmgateway.ToolResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content

/**
 * The only way `AndroidTools` (or any future platform agent's tool roster)
 * reaches Gradle/adb/emulator: over HTTP to the standalone `build-runner`
 * process (PLAN.md §5), never in-process. Every AndroidTools operation is
 * effectively a thin wrapper: build the input map, call [execute], forward
 * progress, return the [ToolResult] — exactly like calling the tool object
 * directly used to, just relocated to another process.
 */
class BuildRunnerClient(
    private val baseUrl: String = System.getenv("BUILD_RUNNER_URL") ?: "http://localhost:8090",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            // Generously above GradleTool's own 900s build timeout — the
            // client should see the tool's own timeout message, not a
            // client-side timeout cutting the stream first.
            requestTimeoutMillis = 20 * 60 * 1000
            socketTimeoutMillis = 20 * 60 * 1000
        }
    }

    suspend fun execute(
        tool: String,
        workspaceRoot: File,
        input: Map<String, Any?>,
        onProgress: suspend (String) -> Unit = {},
    ): ToolResult {
        val response = try {
            http.post("$baseUrl/execute") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ExecuteRequest(tool, workspaceRoot.absolutePath, input.toJsonObject())))
            }
        } catch (e: Exception) {
            return ToolResult(
                "build-runner isn't reachable at $baseUrl — start it with \"./gradlew :build-runner:run\" (or run asap.sh, which starts it automatically). (${e.message})",
                isError = true,
            )
        }

        val channel = response.bodyAsChannel()
        var result: ToolResult? = null
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue
            val element = json.parseToJsonElement(line).jsonObject
            when (element["type"]?.jsonPrimitive?.content) {
                "progress" -> onProgress(element.stringOrNull("message").orEmpty())
                "result" -> result = ToolResult(
                    summary = element.stringOrNull("summary").orEmpty(),
                    isError = element["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    imageUrl = element.stringOrNull("imageUrl"),
                    videoUrl = element.stringOrNull("videoUrl"),
                    notice = element.stringOrNull("notice"),
                )
            }
        }

        return result ?: ToolResult("build-runner closed the connection without returning a result.", isError = true)
    }
}
