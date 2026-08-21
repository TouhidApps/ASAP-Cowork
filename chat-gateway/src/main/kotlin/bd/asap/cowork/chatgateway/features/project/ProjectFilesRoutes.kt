package bd.asap.cowork.chatgateway.features.project

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.common.exceptions.AppException
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Client-facing (no ADMIN_TOKEN) REST surface backing the chat page's
 * "Project" panel — a read-only file/directory browser over the active
 * workspace root, lazily expanded per directory rather than walked up front.
 */
fun Route.projectRoutes() {
    val projectFiles by inject<ProjectFilesService>()

    route("/api/v1/project") {
        get("/tree") {
            call.respond(ApiResponse.ok(projectFiles.tree(call.request.queryParameters["path"])))
        }

        get("/file") {
            val path = call.request.queryParameters["path"] ?: throw AppException.BadRequest("Missing path")
            call.respond(ApiResponse.ok(projectFiles.file(path)))
        }

        get("/file/raw") {
            val path = call.request.queryParameters["path"] ?: throw AppException.BadRequest("Missing path")
            val (file, contentType) = projectFiles.rawFile(path)
            call.respondBytes(file.readBytes(), contentType)
        }
    }
}
