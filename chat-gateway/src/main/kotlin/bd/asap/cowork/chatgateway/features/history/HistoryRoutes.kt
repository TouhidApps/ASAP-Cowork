package bd.asap.cowork.chatgateway.features.history

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.workspacehistory.WorkspaceHistoryService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Client-facing (no ADMIN_TOKEN) REST surface backing the chat page's "Code
 * Changes" drawer — one entry per AI turn, sourced entirely from the hidden
 * shadow git repo `WorkspaceHistoryService` maintains at
 * `<workspaceRoot>/.asap-history`. There's no database table backing this:
 * git itself is the history.
 */
fun Route.historyRoutes() {
    val history by inject<WorkspaceHistoryService>()

    route("/api/v1/history") {
        get {
            call.respond(ApiResponse.ok(history.listCommits()))
        }

        get("/{commitId}/diff") {
            val commitId = call.parameters["commitId"].orEmpty()
            val against = call.request.queryParameters["against"] ?: "parent"
            val diff = runCatching { history.diff(commitId, against) }
                .getOrElse { throw AppException.NotFound("Unknown history entry: $commitId") }
            call.respond(ApiResponse.ok(diff))
        }

        post("/{commitId}/revert") {
            val commitId = call.parameters["commitId"].orEmpty()
            val reverted = runCatching { history.revertTo(commitId) }
                .getOrElse { throw AppException.NotFound("Unknown history entry: $commitId") }
            call.respond(ApiResponse.ok(reverted))
        }
    }
}
