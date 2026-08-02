package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.DotEnv
import bd.asap.cowork.chatgateway.config.WebUiEnvFile

/**
 * Backs the admin panel's "Dev server allowed hosts" section. Adding a host
 * here (a Tailscale device, an ngrok tunnel, ...) writes it to *two* places,
 * because two independent things reject an unrecognized host:
 * - Vite 5+ rejects any request whose Host header isn't on
 *   `server.allowedHosts` (`web-ui/.env`'s `VITE_ALLOWED_HOSTS`, read by
 *   web-ui/vite.config.ts) — this only matters in local dev; a packaged
 *   build never runs Vite.
 * - chat-gateway's own CORS config (`ALLOWED_HOSTS` in the root `.env`,
 *   read by [bd.asap.cowork.chatgateway.plugins.configureHTTP]) rejects any
 *   request whose *Origin* header isn't allow-listed — this is what was
 *   silently killing both REST calls and the chat WebSocket handshake from
 *   a phone on Tailscale even after the Vite-side fix, since it's a
 *   completely separate check on the backend, not the dev server.
 *
 * Vite watches its env files and restarts itself on change, so that half
 * takes effect immediately. chat-gateway's CORS plugin does not support
 * re-reading its allow-list after `install(CORS)` runs at startup, so the
 * backend half of a new host only takes effect after restarting
 * chat-gateway — there's no way around that without restructuring how Ktor
 * installs CORS.
 */
class AllowedHostsService {
    fun status(): AllowedHostsResponse = AllowedHostsResponse(hosts = readHosts())

    fun add(host: String): AllowedHostsResponse {
        val trimmed = host.trim()
        if (trimmed.isBlank()) throw AppException.BadRequest("Host must not be blank")
        val current = readHosts()
        if (trimmed !in current) writeHosts(current + trimmed)
        return status()
    }

    fun remove(host: String): AllowedHostsResponse {
        writeHosts(readHosts() - host)
        return status()
    }

    private fun readHosts(): List<String> =
        WebUiEnvFile.get(VITE_KEY)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun writeHosts(hosts: List<String>) {
        val joined = hosts.joinToString(",")
        WebUiEnvFile.set(VITE_KEY, joined)
        DotEnv.set(CORS_KEY, joined)
    }

    private companion object {
        const val VITE_KEY = "VITE_ALLOWED_HOSTS"
        const val CORS_KEY = "ALLOWED_HOSTS"
    }
}
