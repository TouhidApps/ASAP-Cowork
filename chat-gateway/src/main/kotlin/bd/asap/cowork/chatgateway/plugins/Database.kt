package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.contextstore.ContextDatabase
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

/**
 * Runs Flyway migrations and connects Exposed against `data/app.db`
 * (relative to the process's working directory — see the `run` task's
 * `workingDir` fix in build.gradle.kts). The connection pool is closed on
 * shutdown so a restart doesn't leak file handles. Returns the whole
 * [ContextDatabase] wrapper (not just the Exposed `Database`) since the
 * admin dashboard also needs [ContextDatabase.sizeBytes].
 */
fun Application.configureDatabase(): ContextDatabase {
    val store = ContextDatabase.connect()
    environment.monitor.subscribe(ApplicationStopped) { store.close() }
    return store
}
