package bd.asap.cowork.toolintegrations.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DriveFileSummary(val fileId: String, val name: String, val modifiedTime: String?, val url: String?)

/**
 * Talks to the Google Drive REST API directly — no browser involved. Every
 * call here requires a valid OAuth access token carrying
 * [bd.asap.cowork.toolintegrations.email.DRIVE_METADATA_OAUTH_SCOPE], obtained
 * the same way [bd.asap.cowork.toolintegrations.email.GmailApiClient] gets its
 * Gmail token — via `GmailOAuthClient`, the caller's job, not this class's.
 *
 * Exists solely to answer "what spreadsheets/docs does this account even
 * have" — the Sheets and Docs APIs both require an id up front and have no
 * listing endpoint of their own. Metadata only (name, id, timestamps); never
 * reads file content, which the metadata-only scope above wouldn't permit
 * anyway.
 */
object DriveApiClient {
    private const val BASE_URL = "https://www.googleapis.com/drive/v3/files"
    private const val SPREADSHEET_MIME_TYPE = "application/vnd.google-apps.spreadsheet"
    private const val PAGE_SIZE = 25

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    /** Most-recently-modified spreadsheets first, optionally filtered to those whose name contains [nameContains]. */
    suspend fun listSpreadsheets(accessToken: String, nameContains: String? = null): Result<List<DriveFileSummary>> = runCatching {
        val query = buildString {
            append("mimeType='$SPREADSHEET_MIME_TYPE' and trashed=false")
            if (!nameContains.isNullOrBlank()) append(" and name contains '${nameContains.replace("'", "\\'")}'")
        }
        val url = "$BASE_URL?q=${urlEncode(query)}" +
            "&fields=${urlEncode("files(id,name,modifiedTime,webViewLink)")}" +
            "&orderBy=${urlEncode("modifiedTime desc")}&pageSize=$PAGE_SIZE"
        val response = http.get(url) { authorize(accessToken) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Drive API request failed: $text")
        json.decodeFromString<FileListDto>(text).files.map { it.toSummary() }
    }

    private fun HttpRequestBuilder.authorize(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
    }

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    @Serializable
    private data class FileListDto(val files: List<FileDto> = emptyList())

    @Serializable
    private data class FileDto(val id: String, val name: String, val modifiedTime: String? = null, val webViewLink: String? = null) {
        fun toSummary() = DriveFileSummary(fileId = id, name = name, modifiedTime = modifiedTime, url = webViewLink)
    }
}
