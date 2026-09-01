package bd.asap.cowork.toolintegrations.docs

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to the Google Docs REST API directly — no browser involved. Every
 * call here requires a valid OAuth access token carrying
 * [bd.asap.cowork.toolintegrations.email.DOCS_OAUTH_SCOPE], obtained the
 * same way [bd.asap.cowork.toolintegrations.email.GmailApiClient] gets its
 * Gmail token — via `GmailOAuthClient`, the caller's job, not this class's.
 *
 * Stub: only the read path exists so far. Add create/batchUpdate endpoints
 * here as agent tools need them, following
 * [bd.asap.cowork.toolintegrations.email.GmailApiClient]'s shape.
 */
object DocsApiClient {
    private const val BASE_URL = "https://docs.googleapis.com/v1/documents"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    /** Plain-text body, in reading order — structural formatting (headings, tables) is flattened. */
    suspend fun getDocument(accessToken: String, documentId: String): Result<String> = runCatching {
        val response = http.get("$BASE_URL/$documentId") { authorize(accessToken) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Docs API request failed: $text")
        val dto = json.decodeFromString<DocumentDto>(text)
        dto.body?.content.orEmpty().joinToString("") { it.extractText() }.trim()
    }

    private fun StructuralElementDto.extractText(): String =
        paragraph?.elements.orEmpty().joinToString("") { it.textRun?.content.orEmpty() }

    private fun HttpRequestBuilder.authorize(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
    }

    @Serializable
    private data class DocumentDto(val body: BodyDto? = null)

    @Serializable
    private data class BodyDto(val content: List<StructuralElementDto> = emptyList())

    @Serializable
    private data class StructuralElementDto(val paragraph: ParagraphDto? = null)

    @Serializable
    private data class ParagraphDto(val elements: List<ParagraphElementDto> = emptyList())

    @Serializable
    private data class ParagraphElementDto(val textRun: TextRunDto? = null)

    @Serializable
    private data class TextRunDto(val content: String? = null)
}
