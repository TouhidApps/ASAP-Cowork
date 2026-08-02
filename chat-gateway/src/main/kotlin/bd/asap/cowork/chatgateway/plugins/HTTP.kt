package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.chatgateway.config.DotEnv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Local-dev CORS only — allows the Vite dev server's default origins, plus
 * whatever the admin panel's "Dev server allowed hosts" section has added
 * to the root `.env`'s `ALLOWED_HOSTS` (comma-separated) for a phone on
 * Tailscale, an ngrok tunnel, etc. — see [bd.asap.cowork.chatgateway.features.admin.AllowedHostsService].
 * Without a matching entry here, the browser's Origin header gets rejected
 * outright and *every* request fails closed — REST calls and the chat
 * WebSocket handshake alike — which looks exactly like "the composer never
 * finishes connecting" from the frontend, since a rejected WS handshake
 * never fires `onopen`.
 *
 * `install(CORS)` bakes its allow-list in once at startup — Ktor's CORS
 * plugin has no supported way to re-read it per request — so a host added
 * via the admin panel needs a chat-gateway restart to actually take effect
 * here (the Vite-side half of that same admin action applies immediately,
 * since Vite watches its own env files and restarts itself).
 */
fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHost("localhost:8080", schemes = listOf("http"))
        allowHost("127.0.0.1:8080", schemes = listOf("http"))
        DotEnv.get("ALLOWED_HOSTS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { host -> allowHost(host, schemes = listOf("http", "https")) }
    }
}
