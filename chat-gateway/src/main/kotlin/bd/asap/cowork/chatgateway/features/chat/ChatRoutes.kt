package bd.asap.cowork.chatgateway.features.chat

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.contextstore.ConversationRepository
import bd.asap.cowork.contextstore.StoredAttachment
import bd.asap.cowork.orchestrator.ProjectContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject
import java.io.File
import java.util.UUID

private const val UPLOADS_DIR_NAME = ".asap-uploads"
private const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024

private val ALLOWED_IMAGE_EXTENSIONS = mapOf(
    "image/png" to "png",
    "image/jpeg" to "jpg",
    "image/gif" to "gif",
    "image/webp" to "webp",
)

/**
 * Client-facing (no ADMIN_TOKEN) REST surface backing the chat page: the
 * history drawer (list past conversations, load one's messages), and image
 * attachments — POST stores an image under `<workspaceRoot>/.asap-uploads/`
 * and returns a [StoredAttachment] the client includes on its next WS
 * message; GET serves it back by filename, mirroring how Routing.kt serves
 * captured screenshots. Sending/streaming itself stays entirely on the
 * `/ws/chat` WebSocket — see Routing.kt — a conversation row only exists
 * once its first message has been sent, so there's no create endpoint here.
 */
fun Route.chatRoutes() {
    val conversations by inject<ConversationRepository>()
    val projectContext by inject<ProjectContext>()

    route("/api/v1/chat/conversations") {
        get {
            call.respond(ApiResponse.ok(conversations.listConversations()))
        }

        get("/{id}/messages") {
            val id = call.parameters["id"].orEmpty()
            call.respond(ApiResponse.ok(conversations.getMessages(id)))
        }
    }

    route("/api/v1/chat/uploads") {
        post {
            val workspaceRoot = File(projectContext.workspaceRoot)

            var saved: StoredAttachment? = null

            call.receiveMultipart().forEachPart { part ->
                if (saved == null && part is PartData.FileItem) {
                    val extension = ALLOWED_IMAGE_EXTENSIONS[part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }]
                    if (extension != null) {
                        val bytes = part.provider().readRemaining(MAX_UPLOAD_BYTES + 1).readByteArray()
                        if (bytes.size <= MAX_UPLOAD_BYTES) {
                            val uploadsDir = File(workspaceRoot, UPLOADS_DIR_NAME).apply { mkdirs() }
                            val filename = "upload-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$extension"
                            File(uploadsDir, filename).writeBytes(bytes)
                            saved = StoredAttachment(
                                url = "/api/v1/chat/uploads/$filename",
                                mimeType = if (extension == "jpg") "image/jpeg" else "image/$extension",
                            )
                        }
                    }
                }
                part.dispose()
            }

            val attachment = saved ?: throw AppException.BadRequest(
                "No valid image found in upload (allowed: png, jpeg, gif, webp; max ${MAX_UPLOAD_BYTES / (1024 * 1024)}MB).",
            )
            call.respond(ApiResponse.ok(attachment))
        }

        get("/{filename}") {
            val filename = call.parameters["filename"].orEmpty()
            if (filename.isBlank() || filename.contains('/') || filename.contains("..")) {
                call.respondText("Invalid filename", status = HttpStatusCode.BadRequest)
                return@get
            }

            val file = File(File(projectContext.workspaceRoot, UPLOADS_DIR_NAME), filename)
            if (!file.exists() || !file.isFile) {
                call.respondText("Upload not found", status = HttpStatusCode.NotFound)
                return@get
            }

            val contentType = when (file.extension.lowercase()) {
                "png" -> ContentType.Image.PNG
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "gif" -> ContentType.Image.GIF
                "webp" -> ContentType("image", "webp")
                else -> ContentType.Application.OctetStream
            }
            call.respondBytes(file.readBytes(), contentType)
        }
    }
}
