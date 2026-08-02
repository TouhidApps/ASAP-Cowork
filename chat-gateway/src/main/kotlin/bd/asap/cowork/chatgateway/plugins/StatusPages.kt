package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.common.exceptions.AppException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

/**
 * Single place where exceptions become JSON. Route code throws AppException
 * subtypes; anything else is treated as an unexpected 500 and logged with
 * its stack trace instead of leaking internals to the client.
 */
fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {
        exception<AppException> { call, cause ->
            call.respond(cause.status, ApiResponse.error(cause.code, cause.message))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse.error("INTERNAL_ERROR", "Something went wrong"),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiResponse.error("NOT_FOUND", "Resource not found"))
        }
    }
}
