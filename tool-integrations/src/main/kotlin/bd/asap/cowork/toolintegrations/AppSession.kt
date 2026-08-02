package bd.asap.cowork.toolintegrations

import java.io.File

/**
 * Tracks the package name most recently launched via [LaunchAppTool], so the
 * Device Logs panel can default to that app's own process instead of the
 * whole device's logcat firehose (system services, other apps, etc.) — the
 * same "selected process" default Android Studio's Logcat pane uses.
 *
 * Backed by a file in the OS temp directory rather than a plain in-memory
 * JVM field: [LaunchAppTool] runs inside the **build-runner** process (every
 * agent tool call is dispatched there), while the web UI's live Device Logs
 * panel reads this via `chat-gateway`'s own `LogcatRoutes` — a separate JVM.
 * A `@Volatile var` only updates the copy in whichever process wrote it, so
 * chat-gateway always reported "no app launched" regardless of what was
 * actually running. A shared file is visible to both processes on the same
 * machine, which is all `AppSession` needs given this is single-developer,
 * local-first software (see PLAN.md's decisions log).
 */
object AppSession {
    private val file = File(System.getProperty("java.io.tmpdir"), "asap-cowork-app-session.txt")

    fun current(): String? = try {
        file.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
    } catch (e: Exception) {
        null
    }

    fun set(packageName: String?) {
        try {
            if (packageName.isNullOrBlank()) file.delete() else file.writeText(packageName)
        } catch (e: Exception) {
            // Best-effort — a failed write just means the next read falls
            // back to "no app launched" rather than crashing the caller.
        }
    }
}
