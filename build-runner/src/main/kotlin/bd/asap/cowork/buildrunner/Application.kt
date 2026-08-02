package bd.asap.cowork.buildrunner

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode

/**
 * The dedicated, always-on process PLAN.md §5 assigns all build/emulator/
 * device execution to — the sandboxing boundary that keeps a runaway or
 * malicious build/tool call from ever touching the orchestrator's (chat-
 * gateway's) process. Nothing in here is agent- or LLM-aware; it just
 * exposes the existing tool-integrations Gradle/ADB/emulator/terminal
 * primitives over HTTP so callers never spawn those processes themselves.
 */
fun main() {
    val port = System.getenv("BUILD_RUNNER_PORT")?.toIntOrNull() ?: 8090
    embeddedServer(
        Netty,
        environment = applicationEnvironment(),
        configure = {
            connector {
                this.port = port
                this.host = "0.0.0.0"
            }
            // Netty's default responseWriteTimeoutSeconds (10s) kills the
            // connection if a tool goes quiet for that long without
            // emitting a progress line — real for
            // check_dependency_vulnerabilities (many sequential OSV.dev
            // detail fetches) and possible for any Gradle/Xcode/Flutter
            // task with a silent stretch. BuildRunnerClient already waits
            // up to 20 minutes; the server must match.
            responseWriteTimeoutSeconds = 20 * 60
        },
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(cause.message ?: "Internal error", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        healthRoute()
        executeRoute()
    }
}
