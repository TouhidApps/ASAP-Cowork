package bd.asap.cowork.chatgateway.config

import java.io.File

/**
 * Minimal .env reader/writer — reusable across whichever `.env` file a
 * caller needs (see [DotEnv] for the root one chat-gateway itself reads;
 * [WebUiEnvFile] for `web-ui/.env`, which only Vite's dev server reads).
 * Real environment variables always win on read for the *process's own*
 * env file, which is why that behavior lives in [DotEnv] rather than here —
 * `web-ui/.env` is read by a separate `node`/Vite process, not this JVM, so
 * `System.getenv` here would be checking the wrong process's environment.
 *
 * [get] re-reads the file on every call rather than caching it, so a [set]
 * from the admin panel is visible on the very next request — the same
 * "takes effect immediately, no restart" guarantee the SQLite-backed
 * settings this replaced used to provide (and for `web-ui/.env`
 * specifically, Vite watches its env files and restarts itself on change,
 * so a write here takes effect without the user restarting `npm run dev`).
 *
 * The file format is one entry per physical line, so [set] escapes any `\`
 * or newline in [value] (as `\\`/`\n`) rather than writing it verbatim —
 * otherwise a multiline value would break across several lines and get
 * parsed back as garbage/bogus extra keys. [get] reverses the same escaping
 * on read.
 */
class EnvFile(private val file: File) {
    fun get(key: String): String? = readFile()[key]?.takeIf { it.isNotEmpty() }

    /** Writes `key=value` into the .env file — replaces the existing line for [key] if present, appends one otherwise. Every other line (including comments) is left untouched. */
    @Synchronized
    fun set(key: String, value: String) {
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
        val index = lines.indexOfFirst { line -> keyOf(line) == key }
        val newLine = "$key=${escape(value)}"
        if (index >= 0) lines[index] = newLine else lines += newLine
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun keyOf(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val separatorIndex = trimmed.indexOf('=')
        if (separatorIndex <= 0) return null
        return trimmed.substring(0, separatorIndex).trim()
    }

    private fun readFile(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return file.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null

            val separatorIndex = trimmed.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null

            val key = trimmed.substring(0, separatorIndex).trim()
            var value = trimmed.substring(separatorIndex + 1).trim()
            if (value.length >= 2 && value.first() == value.last() && value.first() in "\"'") {
                value = value.substring(1, value.length - 1)
            }
            key to unescape(value)
        }.toMap()
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> { /* normalize CRLF/CR to the \n escape alone */ }
                else -> append(c)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    'n' -> { append('\n'); i += 2 }
                    '\\' -> { append('\\'); i += 2 }
                    else -> { append(c); i += 1 }
                }
            } else {
                append(c); i += 1
            }
        }
    }
}
