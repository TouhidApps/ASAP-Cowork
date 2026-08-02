package bd.asap.cowork.chatgateway.features.notes

import bd.asap.cowork.chatgateway.common.ApiResponse
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.notesRoutes() {
    val noteService by inject<NoteService>()

    route("/api/v1/notes") {
        get {
            call.respond(ApiResponse.ok(noteService.list()))
        }

        post {
            val request = call.receive<CreateNoteRequest>()
            call.respond(ApiResponse.ok(noteService.create(request.content)))
        }

        route("/{id}") {
            put {
                val id = call.parameters["id"].orEmpty()
                val request = call.receive<UpdateNoteRequest>()
                call.respond(ApiResponse.ok(noteService.update(id, request.content)))
            }

            delete {
                val id = call.parameters["id"].orEmpty()
                noteService.delete(id)
                call.respond(ApiResponse.ok(Unit))
            }
        }
    }
}
