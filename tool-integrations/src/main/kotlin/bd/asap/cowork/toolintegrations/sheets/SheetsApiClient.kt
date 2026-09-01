package bd.asap.cowork.toolintegrations.sheets

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SpreadsheetCreated(val spreadsheetId: String, val url: String)

/**
 * Talks to the Google Sheets REST API directly — no browser involved. Every
 * call here requires a valid OAuth access token carrying
 * [bd.asap.cowork.toolintegrations.email.SHEETS_OAUTH_SCOPE], obtained the
 * same way [bd.asap.cowork.toolintegrations.email.GmailApiClient] gets its
 * Gmail token — via `GmailOAuthClient`, the caller's job, not this class's.
 *
 * Stub: read, create-blank-spreadsheet, overwrite-a-range, and append-rows
 * exist so far — nothing for cell formatting or sheet/tab management yet.
 * Formulas do already work, though: [updateValues]/[appendValues] write
 * with `valueInputOption=USER_ENTERED`, so any value string starting with
 * `=` is evaluated as a real formula, exactly as if typed into the sheet's
 * UI — no separate formula endpoint needed. Add formatting/tabs endpoints
 * here as agent tools need them, following
 * [bd.asap.cowork.toolintegrations.email.GmailApiClient]'s shape.
 */
object SheetsApiClient {
    private const val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    /** [range] is an A1 notation range, e.g. `"Sheet1!A1:D20"`. */
    suspend fun getValues(accessToken: String, spreadsheetId: String, range: String): Result<List<List<String>>> = runCatching {
        val response = http.get("$BASE_URL/$spreadsheetId/values/${urlEncode(range)}") { authorize(accessToken) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Sheets API request failed: $text")
        json.decodeFromString<ValueRangeDto>(text).values
    }

    /** Creates a new, blank spreadsheet titled [title] and returns its id and edit URL. */
    suspend fun createSpreadsheet(accessToken: String, title: String): Result<SpreadsheetCreated> = runCatching {
        val response = http.post(BASE_URL) {
            authorize(accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateSpreadsheetRequestDto.serializer(), CreateSpreadsheetRequestDto(SpreadsheetPropertiesDto(title))))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Sheets API request failed: $text")
        val dto = json.decodeFromString<SpreadsheetDto>(text)
        SpreadsheetCreated(dto.spreadsheetId, dto.spreadsheetUrl)
    }

    /** Overwrites every cell in [range] with [values] (row-major); cells past the end of a shorter row are left untouched. */
    suspend fun updateValues(accessToken: String, spreadsheetId: String, range: String, values: List<List<String>>): Result<Unit> = runCatching {
        val response = http.put("$BASE_URL/$spreadsheetId/values/${urlEncode(range)}?valueInputOption=USER_ENTERED") {
            authorize(accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ValueRangeBodyDto.serializer(), ValueRangeBodyDto(values)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Sheets API request failed: $text")
    }

    /** Appends [values] as new row(s) immediately after the last row with data in [range] (e.g. `"Sheet1"` or `"Sheet1!A:B"`) — doesn't touch existing rows. */
    suspend fun appendValues(accessToken: String, spreadsheetId: String, range: String, values: List<List<String>>): Result<Unit> = runCatching {
        val response = http.post("$BASE_URL/$spreadsheetId/values/${urlEncode(range)}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS") {
            authorize(accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ValueRangeBodyDto.serializer(), ValueRangeBodyDto(values)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Sheets API request failed: $text")
    }

    private fun HttpRequestBuilder.authorize(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
    }

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    @Serializable
    private data class ValueRangeDto(val values: List<List<String>> = emptyList())

    @Serializable
    private data class CreateSpreadsheetRequestDto(val properties: SpreadsheetPropertiesDto)

    @Serializable
    private data class SpreadsheetPropertiesDto(val title: String)

    @Serializable
    private data class SpreadsheetDto(val spreadsheetId: String, val spreadsheetUrl: String)

    @Serializable
    private data class ValueRangeBodyDto(val values: List<List<String>>)
}
