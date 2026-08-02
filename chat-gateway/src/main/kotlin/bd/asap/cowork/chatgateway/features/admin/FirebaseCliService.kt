package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Shells out to the `firebase` CLI on the admin panel's behalf, backing the
 * "Generate" buttons next to the App ID / CI token fields in the Firebase
 * settings section. Kept separate from [FirebaseService] (simple state
 * reads/writes) since this is subprocess management, and separate from the
 * `firebase-integration` module since these calls are admin-panel
 * conveniences for filling in the credentials form — not something the
 * Publishing Agent itself ever calls as a tool.
 */
class FirebaseCliService {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Runs `firebase login:ci`, which opens the user's browser for an
     * interactive Google OAuth flow and blocks until they finish. The CLI
     * refuses to run this command at all when it detects no TTY (always
     * true for a subprocess spawned by this backend) unless `--interactive`
     * is passed.
     */
    suspend fun generateCiToken(): String = withContext(Dispatchers.IO) {
        val (exitCode, output) = runFirebaseCommand(listOf("login:ci", "--interactive"), LOGIN_TIMEOUT_SECONDS)
        if (exitCode != 0) {
            throw AppException.UpstreamError("firebase login:ci failed:\n${output.takeLast(2_000)}")
        }
        extractCiToken(output)
            ?: throw AppException.UpstreamError("Couldn't find a token in firebase login:ci's output:\n${output.takeLast(2_000)}")
    }

    /** The CI token appears on its own line right after this marker in `login:ci`'s documented output. */
    private fun extractCiToken(output: String): String? {
        val lines = output.lines()
        val markerIndex = lines.indexOfFirst { it.contains("Use this token to login on a CI server", ignoreCase = true) }
        if (markerIndex == -1) return null
        return lines.drop(markerIndex + 1).map { it.trim() }.firstOrNull { it.isNotBlank() }
    }

    /**
     * Lists a Firebase project's registered Android apps, so the admin
     * panel can offer them as pick-one options for the App ID field.
     * `ciToken`, if given, authenticates this call directly (works even
     * for a token the user just generated but hasn't saved yet);
     * otherwise falls back to whatever `firebase login` session (if any)
     * already exists on this machine.
     */
    suspend fun listAndroidApps(projectId: String, ciToken: String?): List<FirebaseAppInfo> = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) {
            throw AppException.BadRequest("Project ID must not be blank")
        }
        val args = mutableListOf("apps:list", "ANDROID", "--project", projectId, "--json")
        if (!ciToken.isNullOrBlank()) args += listOf("--token", ciToken)

        val (exitCode, output) = runFirebaseCommand(args, LIST_APPS_TIMEOUT_SECONDS)
        val parsed = extractJsonObject(output)
        if (exitCode != 0 || parsed?.get("status")?.jsonPrimitive?.content != "success") {
            val message = parsed?.get("error")?.jsonPrimitive?.content ?: output.takeLast(2_000)
            throw AppException.UpstreamError("firebase apps:list failed: $message")
        }

        parsed.get("result")?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val appId = obj["appId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            FirebaseAppInfo(
                appId = appId,
                displayName = obj["displayName"]?.jsonPrimitive?.content,
                platform = obj["platform"]?.jsonPrimitive?.content ?: "ANDROID",
            )
        }
    }

    /**
     * `--json` still writes a spinner/progress line ahead of the actual
     * JSON — a real terminal overwrites it in place via carriage returns,
     * but a piped subprocess (this backend never has a TTY) just gets it
     * as plain leading text. Skipping to the first `{` strips that prefix.
     */
    internal fun extractJsonObject(output: String): JsonObject? {
        val jsonStart = output.indexOf('{')
        if (jsonStart == -1) return null
        return try {
            json.parseToJsonElement(output.substring(jsonStart)).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun runFirebaseCommand(args: List<String>, timeoutSeconds: Long): Pair<Int, String> = try {
        val process = ProcessBuilder(listOf("firebase") + args)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            -1 to "Timed out after ${timeoutSeconds}s."
        } else {
            val output = process.inputStream.bufferedReader().readText()
            process.exitValue() to output
        }
    } catch (e: IOException) {
        val notFound = e.message?.contains("error=2") == true || e.message?.contains("No such file", ignoreCase = true) == true
        -1 to if (notFound) {
            "The \"firebase\" CLI is not installed (or not on PATH). Install it with `npm install -g firebase-tools`."
        } else {
            "Failed to run firebase CLI: ${e.message}"
        }
    }

    private companion object {
        const val LOGIN_TIMEOUT_SECONDS = 300L
        const val LIST_APPS_TIMEOUT_SECONDS = 60L
    }
}
