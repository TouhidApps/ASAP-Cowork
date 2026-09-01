package bd.asap.cowork.chatgateway.features.email

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.contextstore.EmailNotificationSettings
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Mounted at `/api/v1/admin/tools/email` by [bd.asap.cowork.chatgateway.features.admin.adminRoutes]'s
 * `/tools` block — Email lives under the admin panel's "Tools" nav
 * section (ADMIN_TOKEN-gated, same as every other admin feature), with
 * more tool-specific route functions expected to join it there the same
 * way as more tools are added. The OAuth *callback* itself is a separate,
 * unauthenticated route — see [emailOAuthCallbackRoute] below.
 */
fun Route.emailToolsRoutes() {
    val emailService by inject<EmailService>()

    route("/email") {
        route("/accounts") {
            get {
                call.respond(ApiResponse.ok(emailService.listAccounts()))
            }

            post("/{id}/default") {
                val id = call.parameters["id"].orEmpty()
                emailService.setDefaultAccount(id)
                call.respond(ApiResponse.ok(Unit))
            }

            delete("/{id}") {
                val id = call.parameters["id"].orEmpty()
                emailService.disconnectAccount(id)
                call.respond(ApiResponse.ok(Unit))
            }
        }

        route("/settings") {
            get {
                call.respond(ApiResponse.ok(emailService.getSettings()))
            }

            put {
                val request = call.receive<EmailNotificationSettings>()
                emailService.updateSettings(request)
                call.respond(ApiResponse.ok(emailService.getSettings()))
            }
        }

        route("/oauth") {
            get("/config") {
                call.respond(ApiResponse.ok(emailService.oauthStatus()))
            }

            put("/config") {
                val request = call.receive<SetGmailOAuthCredentialsRequest>()
                call.respond(ApiResponse.ok(emailService.updateOAuthCredentials(request.clientId, request.clientSecret)))
            }

            get("/authorize-url") {
                call.respond(ApiResponse.ok(emailService.buildAuthorizeUrl()))
            }
        }
    }
}

/**
 * `GET /api/v1/oauth/gmail/callback` — where Google redirects the browser
 * back to after the user approves (or denies) access. This is a plain
 * top-level browser navigation, so it can't carry the ADMIN_TOKEN bearer
 * header every admin route requires; [EmailService.handleOAuthCallback]'s
 * own `state`-nonce check is this route's actual protection instead.
 * Registered directly in Routing.kt, not inside `adminRoutes()`.
 */
fun Route.emailOAuthCallbackRoute() {
    val emailService by inject<EmailService>()

    get("/api/v1/oauth/gmail/callback") {
        val code = call.request.queryParameters["code"]
        val state = call.request.queryParameters["state"]
        val error = call.request.queryParameters["error"]

        val redirectTarget = try {
            val emailAddress = emailService.handleOAuthCallback(code, state, error)
            "/admin/tools/email?connected=${java.net.URLEncoder.encode(emailAddress, "UTF-8")}"
        } catch (e: Exception) {
            "/admin/tools/email?oauthError=${java.net.URLEncoder.encode(e.message ?: "Connection failed", "UTF-8")}"
        }
        call.respondRedirect(redirectTarget)
    }
}
