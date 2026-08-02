package bd.asap.cowork.chatgateway.config

import java.io.File

/**
 * The root `.env` file — secrets (API keys, ADMIN_TOKEN) and local settings
 * (Ollama host/model) for *this* process (chat-gateway), read from a
 * project-local, gitignored root `.env` instead of the machine's shell
 * profile or the SQLite settings table. Real environment variables always
 * win on read, since both live in this same JVM's process environment.
 *
 * See [EnvFile] for the underlying format/escaping this and [WebUiEnvFile]
 * share.
 */
object DotEnv {
    private val env = EnvFile(File(".env"))

    fun get(key: String): String? = System.getenv(key) ?: env.get(key)

    fun set(key: String, value: String) = env.set(key, value)
}
