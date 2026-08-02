package bd.asap.cowork.toolintegrations.buildrunner

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** POST /execute request body. [workspaceRoot] is an absolute path — build-runner and its caller always run on the same machine (PLAN.md's local-first v1 deployment target), so there's no file-transfer step, just a shared filesystem. */
@Serializable
data class ExecuteRequest(val tool: String, val workspaceRoot: String, val input: JsonObject)

/**
 * The response body is `application/x-ndjson`: one JSON object per line,
 * terminated by exactly one line with `type: "result"`. This lets a long
 * Gradle build or emulator boot report interim progress (mirroring
 * [bd.asap.cowork.llmgateway.ToolExecutor]'s `onProgress` callback) over a
 * plain HTTP response instead of needing a WebSocket for one request/reply.
 */
@Serializable
data class ProgressLine(val message: String, val type: String = "progress")

@Serializable
data class ResultLine(
    val summary: String,
    val isError: Boolean = false,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val notice: String? = null,
    val type: String = "result",
)
