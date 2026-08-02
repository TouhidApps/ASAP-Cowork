package bd.asap.cowork.chatgateway.common.exceptions

import io.ktor.http.HttpStatusCode

/**
 * Base for exceptions that should be translated into a JSON ApiResponse by
 * the StatusPages plugin instead of leaking a raw 500.
 */
sealed class AppException(
    val code: String,
    override val message: String,
    val status: HttpStatusCode,
) : RuntimeException(message) {

    class NotFound(message: String) :
        AppException(code = "NOT_FOUND", message = message, status = HttpStatusCode.NotFound)

    class BadRequest(message: String) :
        AppException(code = "BAD_REQUEST", message = message, status = HttpStatusCode.BadRequest)

    class Unauthorized(message: String = "Unauthorized") :
        AppException(code = "UNAUTHORIZED", message = message, status = HttpStatusCode.Unauthorized)

    class UpstreamError(message: String) :
        AppException(code = "UPSTREAM_ERROR", message = message, status = HttpStatusCode.BadGateway)
}
