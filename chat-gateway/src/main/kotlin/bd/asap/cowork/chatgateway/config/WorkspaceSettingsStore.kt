package bd.asap.cowork.chatgateway.config

/**
 * Persists the confirmed workspace root across restarts via the `.env` file
 * (key [KEY], see [DotEnv]) rather than the SQLite `settings` table, so it
 * lives alongside every other locally-configured value (API keys, Ollama
 * model) instead of a separate store.
 */
class WorkspaceSettingsStore {
    fun readRootPath(): String? = DotEnv.get(KEY)

    fun writeRootPath(path: String) = DotEnv.set(KEY, path)

    private companion object {
        const val KEY = "WORKSPACE_ROOT_PATH"
    }
}
