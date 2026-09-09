package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.ApiResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Mounted at `/api/v1/admin/tools/image-ai` by [adminRoutes]'s `/tools`
 * block, same pattern as [bd.asap.cowork.chatgateway.features.email.emailToolsRoutes] —
 * a separate section from the chat-LLM providers() routes, since this
 * configures what backs image generation, not the orchestrator's chat model.
 */
fun Route.imageAiToolsRoutes() {
    val imageAiService by inject<ImageAiService>()

    route("/image-ai") {
        get {
            call.respond(ApiResponse.ok(imageAiService.status()))
        }

        put("/current") {
            val request = call.receive<SetImageAiProviderRequest>()
            call.respond(ApiResponse.ok(imageAiService.setProvider(request.provider)))
        }

        put("/{id}/credentials") {
            val id = call.parameters["id"].orEmpty()
            val request = call.receive<SetImageAiCredentialRequest>()
            call.respond(ApiResponse.ok(imageAiService.setCredential(id, request.apiKey)))
        }

        delete("/{id}/credentials") {
            val id = call.parameters["id"].orEmpty()
            call.respond(ApiResponse.ok(imageAiService.clearCredential(id)))
        }
    }
}
