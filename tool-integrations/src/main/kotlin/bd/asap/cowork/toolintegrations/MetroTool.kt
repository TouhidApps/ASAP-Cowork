package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Starts/stops the Metro JS bundler — a **debug** React Native build
 * fetches its JS from Metro at runtime rather than bundling it in, so
 * without this running, an installed debug app just shows a red "Unable
 * to load script" error instead of the app. (A release build bundles JS
 * at build time and doesn't need this — [ReactNativeTools] still exposes
 * it since debug is the normal day-to-day loop.)
 */
object MetroTool {
    const val NAME = "manage_metro_bundler"
    private const val DEFAULT_PORT = 8081
    private const val READY_TIMEOUT_SECONDS = 30L
    private const val POLL_INTERVAL_MS = 1_000L
    private const val STATUS_CHECK_TIMEOUT_MS = 2_000

    val spec = ToolSpec(
        name = NAME,
        description = "Starts or stops the Metro JS bundler for a React Native project — required for a debug build to actually load its JS at runtime. action=\"start\" with directory boots it and waits until ready, reusing one already running for the same project. action=\"stop\" kills the one this session started.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("start", "stop")),
                "directory" to mapOf("type" to "string", "description" to "Project directory, relative to the workspace root. Required for action=\"start\"."),
                "port" to mapOf("type" to "integer", "description" to "Defaults to $DEFAULT_PORT."),
            ),
            "required" to listOf("action"),
        ),
    )

    private val mutex = Mutex()

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>): ToolResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            when (input["action"] as? String) {
                "start" -> start(workspaceRoot, input)
                "stop" -> stop()
                else -> ToolResult("\"action\" must be one of: start, stop.", isError = true)
            }
        }
    }

    private fun start(workspaceRoot: File, input: Map<String, Any?>): ToolResult {
        val directory = input["directory"] as? String
            ?: return ToolResult("\"directory\" is required for action=\"start\".", isError = true)
        val port = (input["port"] as? Number)?.toInt() ?: DEFAULT_PORT

        val current = MetroSession.current()
        if (current != null && current.port == port && isReady(port)) {
            return ToolResult("Metro is already running for \"${current.projectDir}\" on port $port — reusing it.")
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(directory)
            ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        val projectDir = projectDirPath.toFile()
        if (!File(projectDir, "package.json").exists()) {
            return ToolResult("No package.json found in $directory — run create_react_native_project first.", isError = true)
        }

        current?.process?.destroyForcibly()

        val logFile = File(projectDir, ".asap-metro/metro.log").apply { parentFile.mkdirs() }
        return try {
            val builder = ProcessBuilder("npx", "react-native", "start", "--port", port.toString())
                .directory(projectDir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            ToolchainEnvironment.configure(builder)
            val process = builder.start()
            waitForReady(process, directory, port, logFile)
        } catch (e: IOException) {
            ToolResult("Failed to start Metro (${e.message}). Is Node.js installed and on PATH?", isError = true)
        }
    }

    private fun waitForReady(process: Process, directory: String, port: Int, logFile: File): ToolResult {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                return ToolResult("Metro exited unexpectedly. Log:\n${logFile.readText().takeLast(2000)}", isError = true)
            }
            if (isReady(port)) {
                MetroSession.set(MetroSession.Running(process, directory, port))
                return ToolResult("Metro is running for \"$directory\" on port $port.")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        process.destroyForcibly()
        return ToolResult("Metro didn't become ready within ${READY_TIMEOUT_SECONDS}s. Log:\n${logFile.readText().takeLast(2000)}", isError = true)
    }

    /** Metro's own `/status` endpoint replies "packager-status:running" once it's actually serving — more reliable than assuming readiness after a fixed delay. */
    private fun isReady(port: Int): Boolean = try {
        val connection = URI("http://localhost:$port/status").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = STATUS_CHECK_TIMEOUT_MS
        connection.readTimeout = STATUS_CHECK_TIMEOUT_MS
        val body = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        connection.responseCode == 200 && body.contains("packager-status:running")
    } catch (e: IOException) {
        false
    }

    private fun stop(): ToolResult {
        val current = MetroSession.current() ?: return ToolResult("Metro isn't running.")
        current.process.destroyForcibly()
        MetroSession.set(null)
        return ToolResult("Stopped Metro for \"${current.projectDir}\".")
    }
}
