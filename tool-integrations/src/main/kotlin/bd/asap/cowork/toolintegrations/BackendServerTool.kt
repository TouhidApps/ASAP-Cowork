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
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Starts/stops a scaffolded backend's dev server — one tool across all
 * four [create_backend_project] stacks, since "start a long-running
 * process, wait until it's actually accepting connections, track it so
 * it can be stopped later" is identical regardless of which stack it is;
 * only the launch command differs. Same reasoning as [MetroTool] for why
 * this can't just be `run_terminal_command`: a dev server never exits on
 * its own, which doesn't fit [ProcessRunner]'s run-to-completion model.
 *
 * Readiness is a raw TCP connect to the port rather than an HTTP request
 * to a specific path — the four stacks don't share a common health-check
 * route, but "something is listening" is a reliable, stack-agnostic
 * enough signal that the process didn't crash on startup.
 */
object BackendServerTool {
    const val NAME = "manage_backend_server"
    private const val DEFAULT_PORT = 8080
    private const val READY_TIMEOUT_SECONDS = 120L
    private const val POLL_INTERVAL_MS = 1_000L
    private const val CONNECT_TIMEOUT_MS = 500

    val spec = ToolSpec(
        name = NAME,
        description = "Starts or stops a scaffolded backend's dev server. action=\"start\" with directory and stack (\"spring-boot\", \"node-express\", \"python-fastapi\", or \"php\") boots it and waits until it's accepting connections, reusing one already running for the same project/port. action=\"stop\" kills the one this session started.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("start", "stop")),
                "directory" to mapOf("type" to "string", "description" to "Project directory, relative to the workspace root. Required for action=\"start\"."),
                "stack" to mapOf(
                    "type" to "string",
                    "enum" to listOf("spring-boot", "node-express", "python-fastapi", "php"),
                    "description" to "Required for action=\"start\".",
                ),
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
        val stack = input["stack"] as? String
            ?: return ToolResult("\"stack\" is required for action=\"start\".", isError = true)
        val port = (input["port"] as? Number)?.toInt() ?: DEFAULT_PORT

        val current = BackendServerSession.current()
        if (current != null && current.port == port && isListening(port)) {
            return ToolResult("A ${current.stack} server is already running for \"${current.projectDir}\" on port $port — reusing it.")
        }

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(directory)
            ?: return ToolResult("Invalid or out-of-workspace directory: $directory", isError = true)
        val projectDir = projectDirPath.toFile()

        val command = when (stack) {
            "spring-boot" -> {
                if (!File(projectDir, "gradlew").canExecute()) {
                    return ToolResult("No gradlew found in $directory — run create_backend_project first.", isError = true)
                }
                listOf("./gradlew", "bootRun", "--args=--server.port=$port")
            }
            "node-express" -> {
                if (!File(projectDir, "node_modules").isDirectory) {
                    return ToolResult("No node_modules found in $directory — run create_backend_project first.", isError = true)
                }
                listOf("node", "index.js")
            }
            "python-fastapi" -> {
                val uvicorn = File(projectDir, "venv/bin/uvicorn")
                if (!uvicorn.canExecute()) {
                    return ToolResult("No venv found in $directory — run create_backend_project first.", isError = true)
                }
                listOf(uvicorn.absolutePath, "main:app", "--host", "0.0.0.0", "--port", port.toString())
            }
            "php" -> listOf("php", "-S", "127.0.0.1:$port", "-t", ".")
            else -> return ToolResult("\"stack\" must be one of: spring-boot, node-express, python-fastapi, php.", isError = true)
        }

        current?.process?.destroyForcibly()

        val logFile = File(projectDir, ".asap-server/server.log").apply { parentFile.mkdirs() }
        return try {
            val builder = ProcessBuilder(command)
                .directory(projectDir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            if (stack == "node-express") builder.environment()["PORT"] = port.toString()
            ToolchainEnvironment.configure(builder)
            val process = builder.start()
            waitForReady(process, directory, stack, port, logFile)
        } catch (e: IOException) {
            ToolResult("Failed to start the $stack server (${e.message}).", isError = true)
        }
    }

    private fun waitForReady(process: Process, directory: String, stack: String, port: Int, logFile: File): ToolResult {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                return ToolResult("$stack server exited unexpectedly. Log:\n${logFile.readText().takeLast(2000)}", isError = true)
            }
            if (isListening(port)) {
                BackendServerSession.set(BackendServerSession.Running(process, directory, stack, port))
                return ToolResult("$stack server is running for \"$directory\" on port $port.")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        process.destroyForcibly()
        return ToolResult("$stack server didn't start listening within ${READY_TIMEOUT_SECONDS}s. Log:\n${logFile.readText().takeLast(2000)}", isError = true)
    }

    private fun isListening(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("localhost", port), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: IOException) {
        false
    }

    private fun stop(): ToolResult {
        val current = BackendServerSession.current() ?: return ToolResult("No backend server is running.")
        current.process.destroyForcibly()
        BackendServerSession.set(null)
        return ToolResult("Stopped the ${current.stack} server for \"${current.projectDir}\".")
    }
}
