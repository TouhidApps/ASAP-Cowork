package bd.asap.cowork.chatgateway.features.plan

import bd.asap.cowork.chatgateway.common.ApiResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Client-facing (no ADMIN_TOKEN) REST surface backing the chat page's "Plan
 * Preview" panel — a read-only viewer over any Markdown file already on
 * disk in the workspace, not just agent-authored plans.
 */
fun Route.planRoutes() {
    val planService by inject<PlanService>()

    route("/api/v1/plan") {
        get("/files") {
            call.respond(ApiResponse.ok(planService.listMarkdownFiles()))
        }

        get("/content") {
            val path = call.request.queryParameters["path"].orEmpty()
            call.respond(ApiResponse.ok(planService.readMarkdownFile(path)))
        }
    }
}
