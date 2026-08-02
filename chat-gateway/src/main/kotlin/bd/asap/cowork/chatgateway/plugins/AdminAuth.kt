package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.DotEnv
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header

/**
 * Route-scoped guard for the admin routes: requires `Authorization: Bearer
 * <ADMIN_TOKEN>` matching the server's configured token. Installed inside
 * the `/api/v1/admin` route block (see AdminRoutes.kt), not globally, so
 * chat and health stay open.
 */
val AdminAuth = createRouteScopedPlugin("AdminAuth") {
    onCall { call ->
        val expected = DotEnv.get("ADMIN_TOKEN")
        if (expected.isNullOrBlank()) {
            throw AppException.Unauthorized("ADMIN_TOKEN is not configured on the server")
        }

        val provided = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        if (provided != expected) {
            throw AppException.Unauthorized("Invalid or missing admin token")
        }
    }
}
