package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.chatgateway.ChatEvent
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Every currently-open `/ws/chat` connection, so something outside that
 * connection's own request/response loop — the email agent's background
 * poll, or any future agent doing the same — can push a [ChatEvent] to the
 * client without waiting for the user to send a message first. Routing.kt
 * registers a session on connect and unregisters it in a `finally` on
 * disconnect. Each session's own per-message loop already calls `send` on
 * its `DefaultWebSocketServerSession`; a background broadcast calling `send`
 * concurrently on the same session would race with that, since Ktor's
 * WebSocket session isn't safe to write to from two coroutines at once — the
 * per-session [Mutex] here serializes the two.
 */
object ChatSocketRegistry {
    private val sessions = ConcurrentHashMap<String, Pair<DefaultWebSocketServerSession, Mutex>>()

    fun register(id: String, session: DefaultWebSocketServerSession) {
        sessions[id] = session to Mutex()
    }

    fun unregister(id: String) {
        sessions.remove(id)
    }

    /** Sends [event] to every currently-open chat connection. A send failure on one connection (e.g. it just closed) never stops delivery to the others. */
    suspend fun broadcast(event: ChatEvent) {
        val text = Json.encodeToString(event)
        sessions.values.forEach { (session, mutex) ->
            runCatching { mutex.withLock { session.send(Frame.Text(text)) } }
        }
    }
}
