package bd.asap.cowork.buildrunner

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(val status: String, val service: String, val timestamp: Long)

fun Route.healthRoute() {
    get("/health") {
        call.respond(HealthStatus(status = "UP", service = "build-runner", timestamp = System.currentTimeMillis()))
    }
}
