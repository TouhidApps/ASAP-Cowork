package bd.asap.cowork.toolintegrations.email

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/** Read/write access to Gmail (list, read, label changes, send) but never permanent deletion — see [GmailApiClient]'s doc comment. */
const val GMAIL_OAUTH_SCOPE = "https://www.googleapis.com/auth/gmail.modify"

/** Read/write access to Sheets — see [bd.asap.cowork.toolintegrations.sheets.SheetsApiClient]. */
const val SHEETS_OAUTH_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

/** Read/write access to Docs — see [bd.asap.cowork.toolintegrations.docs.DocsApiClient]. */
const val DOCS_OAUTH_SCOPE = "https://www.googleapis.com/auth/documents"

/** Read/write access to Calendar — see [bd.asap.cowork.toolintegrations.calendar.CalendarApiClient]. */
const val CALENDAR_OAUTH_SCOPE = "https://www.googleapis.com/auth/calendar"

/**
 * Read-only access to Drive file *metadata* (name, id, type, timestamps) —
 * deliberately not the broader `drive`/`drive.readonly` scopes, which also
 * grant file *content* access this app has no use for. Sheets/Docs each
 * have their own scope for content; this one exists only so
 * [bd.asap.cowork.toolintegrations.drive.DriveApiClient] can list which
 * spreadsheets/docs even exist, which neither the Sheets nor Docs API
 * itself can answer.
 */
const val DRIVE_METADATA_OAUTH_SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly"

/**
 * Every scope the single Google OAuth client requests on connect. One
 * connected account backs Gmail, Sheets, Docs, Calendar and Drive listing
 * alike — there's no per-service reconnect flow, so a new scope added here
 * means existing accounts must reauthorize (handled by `prompt=consent`
 * below) before the matching API client will get a token that carries it.
 */
val GOOGLE_OAUTH_SCOPES = listOf(GMAIL_OAUTH_SCOPE, SHEETS_OAUTH_SCOPE, DOCS_OAUTH_SCOPE, CALENDAR_OAUTH_SCOPE, DRIVE_METADATA_OAUTH_SCOPE)

data class GmailOAuthCredentials(val clientId: String, val clientSecret: String)

/**
 * In-memory view of the currently-configured Google OAuth client
 * credentials, read directly by [GmailOAuthClient] — same pattern as
 * `bd.asap.cowork.firebase.FirebaseCredentialsRegistry`. chat-gateway's
 * `GmailOAuthCredentialsStore` is the only writer, seeded at DI startup.
 */
object GmailOAuthCredentialsRegistry {
    @Volatile private var current: GmailOAuthCredentials? = null

    fun current(): GmailOAuthCredentials? = current

    fun set(value: GmailOAuthCredentials?) {
        current = value
    }
}

data class GmailTokenResponse(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long)

/**
 * The two Google OAuth endpoints the email connection flow needs: build the
 * consent-screen URL the admin panel redirects the browser to, and exchange
 * either an authorization code (first connect) or a refresh token
 * (thereafter) for an access token. Never talks to Gmail's own API — that's
 * [GmailApiClient].
 */
object GmailOAuthClient {
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    /** [state] is an opaque nonce the caller generates and later verifies on callback, as CSRF protection for the redirect. */
    fun authorizationUrl(credentials: GmailOAuthCredentials, redirectUri: String, state: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "$AUTH_ENDPOINT?client_id=${enc(credentials.clientId)}" +
            "&redirect_uri=${enc(redirectUri)}" +
            "&response_type=code" +
            "&scope=${enc(GOOGLE_OAUTH_SCOPES.joinToString(" "))}" +
            // offline + consent: without both, Google only returns a
            // refresh_token on a user's very first-ever consent — the
            // background poller needs one on every (re)connect.
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=${enc(state)}"
    }

    suspend fun exchangeCode(credentials: GmailOAuthCredentials, code: String, redirectUri: String): Result<GmailTokenResponse> =
        tokenRequest(
            Parameters.build {
                append("client_id", credentials.clientId)
                append("client_secret", credentials.clientSecret)
                append("code", code)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
            },
        )

    suspend fun refreshAccessToken(credentials: GmailOAuthCredentials, refreshToken: String): Result<GmailTokenResponse> =
        tokenRequest(
            Parameters.build {
                append("client_id", credentials.clientId)
                append("client_secret", credentials.clientSecret)
                append("refresh_token", refreshToken)
                append("grant_type", "refresh_token")
            },
        )

    private suspend fun tokenRequest(parameters: Parameters): Result<GmailTokenResponse> = runCatching {
        val response = http.submitForm(TOKEN_ENDPOINT, formParameters = parameters)
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Google token endpoint returned ${response.status.value}: $text")
        }
        val dto = json.decodeFromString<TokenResponseDto>(text)
        GmailTokenResponse(dto.accessToken, dto.refreshToken, dto.expiresIn)
    }

    @Serializable
    private data class TokenResponseDto(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long,
    )
}
