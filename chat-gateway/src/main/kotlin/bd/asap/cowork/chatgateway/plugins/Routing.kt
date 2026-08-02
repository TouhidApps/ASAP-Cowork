package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.TaskAttachment
import bd.asap.cowork.chatgateway.ChatEvent
import bd.asap.cowork.chatgateway.HealthStatus
import bd.asap.cowork.chatgateway.IncomingMessage
import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.features.admin.adminRoutes
import bd.asap.cowork.chatgateway.features.chat.chatRoutes
import bd.asap.cowork.chatgateway.features.logcat.logcatRoutes
import bd.asap.cowork.chatgateway.features.notes.notesRoutes
import bd.asap.cowork.chatgateway.toWire
import bd.asap.cowork.contextstore.ConversationRepository
import bd.asap.cowork.contextstore.StoredMessage
import bd.asap.cowork.orchestrator.Orchestrator
import bd.asap.cowork.orchestrator.ProjectContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.io.File

/**
 * `GET /health` proves routing -> DI -> orchestrator wiring is intact.
 * `WS /ws/chat` is the streaming pipeline: each user message is recorded
 * (persisted to SQLite via `ConversationRepository`, so history survives a
 * restart), then handed to `Orchestrator.route()` along with every prior
 * message in the same conversation, which classifies it against the
 * registered agent roster and picks the right one automatically — the
 * client never names a capability — and gives the picked agent the same
 * history so replies stay coherent turn to turn. Every AgentEvent the
 * orchestrator emits (including which agent got activated) is forwarded to
 * the client as it happens.
 * `/api/v1/admin` routes are the admin panel's API — see AdminRoutes.kt.
 * `/api/v1/notes` is a personal scratchpad, unrelated to chat — see
 * NoteRoutes.kt.
 */
fun Application.configureRouting() {
    val orchestrator by inject<Orchestrator>()
    val conversation by inject<ConversationRepository>()
    val projectContext by inject<ProjectContext>()

    routing {
        get("/health") {
            call.respond(
                ApiResponse.ok(
                    HealthStatus(status = "UP", service = "chat-gateway", timestamp = System.currentTimeMillis()),
                ),
            )
        }

        adminRoutes()
        notesRoutes()
        chatRoutes()
        logcatRoutes()

        get("/api/v1/screenshots/{filename}") {
            val filename = call.parameters["filename"]
            if (filename.isNullOrBlank() || "/" in filename || ".." in filename) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val file = File(projectContext.workspaceRoot, ".asap-screenshots/$filename")
            if (!file.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondBytes(file.readBytes(), ContentType.Image.PNG)
        }

        get("/api/v1/videos/{filename}") {
            val filename = call.parameters["filename"]
            if (filename.isNullOrBlank() || "/" in filename || ".." in filename) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val file = File(projectContext.workspaceRoot, ".asap-videos/$filename")
            if (!file.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondBytes(file.readBytes(), ContentType.Video.MP4)
        }

        // Serves whatever BrandingAgent wrote via write_brand_asset (SVG
        // logos, brand-guide.md) — an unrecognized extension falls back to
        // a generic download rather than 404ing, since write_brand_asset
        // accepts any filename.
        get("/api/v1/branding/{filename}") {
            val filename = call.parameters["filename"]
            if (filename.isNullOrBlank() || "/" in filename || ".." in filename) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val file = File(projectContext.workspaceRoot, "branding/$filename")
            if (!file.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val contentType = when (file.extension.lowercase()) {
                "svg" -> ContentType("image", "svg+xml")
                "md" -> ContentType.Text.Plain
                "png" -> ContentType.Image.PNG
                "jpg", "jpeg" -> ContentType.Image.JPEG
                else -> ContentType.Application.OctetStream
            }
            call.respondBytes(file.readBytes(), contentType)
        }

        webSocket("/ws/chat") {
            // One connection is one conversation. The client can ask to
            // resume a specific one via `?conversationId=`, e.g. when the
            // user picks a past conversation from the history drawer or the
            // page reloads mid-conversation; an unknown/missing id falls
            // back to starting fresh. The row itself is created lazily, on
            // the first user message of a fresh connection, so a connection
            // that never sends anything doesn't leave an empty "New chat"
            // behind in the database — the client learns that id (and a
            // resumed one, for confirmation) via a `conversation_started`
            // event, since it has no other way to find out what a fresh
            // connection's conversation id ended up being.
            val requestedId = call.request.queryParameters["conversationId"]?.takeIf { it.isNotBlank() }
            var activeConversationId: String? = requestedId?.takeIf { conversation.findConversation(it) != null }

            activeConversationId?.let { id ->
                send(Frame.Text(Json.encodeToString<ChatEvent>(ChatEvent(type = "conversation_started", conversationId = id))))
            }

            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                val incomingMessage = runCatching { Json.decodeFromString<IncomingMessage>(text) }
                    .getOrElse { IncomingMessage(content = text) }

                val isFreshConversation = activeConversationId == null
                val conversationId = activeConversationId ?: conversation.createConversation().id.also { activeConversationId = it }
                if (isFreshConversation) {
                    send(Frame.Text(Json.encodeToString<ChatEvent>(ChatEvent(type = "conversation_started", conversationId = conversationId))))
                }

                // Fetched before appending this turn's own message below, so
                // it's exactly what came before — the agent gets the new
                // message separately as `input`, same as every other call site.
                val history = conversation.getMessages(conversationId).map { ConversationTurn(it.role, it.content) }

                conversation.appendMessage(
                    conversationId,
                    StoredMessage(role = "user", content = incomingMessage.content, attachments = incomingMessage.attachments),
                )

                // Resolves each attachment's served URL (/api/v1/chat/uploads/<filename>,
                // from ChatRoutes.kt's upload endpoint) back to the file on disk, so the
                // picked agent can actually read the image bytes instead of only knowing
                // an attachment exists.
                val attachments = incomingMessage.attachments.map { attachment ->
                    TaskAttachment(
                        path = File(File(projectContext.workspaceRoot, ".asap-uploads"), attachment.url.substringAfterLast('/')).absolutePath,
                        mimeType = attachment.mimeType,
                    )
                }

                val assistantReply = StringBuilder()

                orchestrator.route(incomingMessage.content, history = history, attachments = attachments).collect { event ->
                    if (event is AgentEvent.TextDelta) assistantReply.append(event.text)
                    send(Frame.Text(Json.encodeToString<ChatEvent>(event.toWire())))
                }

                if (assistantReply.isNotEmpty()) {
                    conversation.appendMessage(conversationId, StoredMessage(role = "assistant", content = assistantReply.toString()))
                }
            }
        }

        // Serves the React chat UI bundled into this jar's own classpath at
        // "static/" (see chat-gateway/build.gradle.kts's copyWebUi task) —
        // this is what makes a packaged chat-gateway-all.jar a single
        // self-contained app: one process, one URL, no separate Node/npm
        // server needed. default("index.html") falls back to the SPA's
        // entry point for any path that isn't a real static asset (e.g. a
        // browser-router route like /settings on a hard refresh), letting
        // react-router take over client-side. In plain source checkouts
        // where web-ui hasn't been built yet, this route simply 404s —
        // ktor's routing tree still matches the more specific literal
        // routes above (/health, /api/..., /ws/chat) ahead of this one.
        staticResources("/", "static") {
            default("index.html")
        }
    }
}
