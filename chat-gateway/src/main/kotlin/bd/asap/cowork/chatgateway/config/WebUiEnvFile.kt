package bd.asap.cowork.chatgateway.config

import java.io.File

/**
 * `web-ui/.env` — read only by Vite's dev server (a separate `node`
 * process), not this JVM, so unlike [DotEnv] there's no `System.getenv`
 * fallback here; checking this process's environment would be checking the
 * wrong process entirely. Only meaningful in local dev — a packaged build
 * serves the pre-built React bundle directly and never runs Vite at all.
 */
object WebUiEnvFile {
    private val env = EnvFile(File("web-ui/.env"))

    fun get(key: String): String? = env.get(key)

    fun set(key: String, value: String) = env.set(key, value)
}
