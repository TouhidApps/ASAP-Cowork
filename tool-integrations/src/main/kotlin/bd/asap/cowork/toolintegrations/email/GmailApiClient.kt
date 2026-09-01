package bd.asap.cowork.toolintegrations.email

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

data class GmailMessageSummary(
    val messageId: String,
    val threadId: String,
    val sender: String,
    val subject: String,
    val snippet: String,
    val unread: Boolean,
)

data class GmailMessageDetail(
    val threadId: String,
    val subject: String,
    val sender: String,
    val body: String,
    /** The RFC822 `Message-Id` header — needed for `In-Reply-To`/`References` when replying, distinct from [GmailMessageSummary.messageId] (Gmail's own internal id). */
    val rfc822MessageId: String?,
)

/**
 * Talks to Gmail's REST API directly — no browser involved. Every call here
 * requires a valid OAuth access token (obtained/refreshed via
 * [GmailOAuthClient], the caller's job — this client doesn't know about
 * token storage or refresh). Scoped to `gmail.modify` ([GMAIL_OAUTH_SCOPE]),
 * which — per Google's own documentation — covers everything below except
 * *permanent* deletion; this class simply never calls the trash/delete
 * endpoints that scope would technically still allow, the same structural
 * guarantee the old browser-automation tools made by never defining such a
 * tool at all.
 */
object GmailApiClient {
    private const val BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me"
    private const val MAX_RESULTS = 25

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    suspend fun getProfile(accessToken: String): Result<String> = runCatching {
        val response = authorizedGet(accessToken, "$BASE_URL/profile")
        decode<ProfileDto>(response).emailAddress
    }

    suspend fun listInbox(accessToken: String, query: String? = null): Result<List<GmailMessageSummary>> = runCatching {
        val q = if (query.isNullOrBlank()) "in:inbox" else "in:inbox $query"
        val listResponse = authorizedGet(accessToken, "$BASE_URL/messages?maxResults=$MAX_RESULTS&q=${urlEncode(q)}")
        val ids = decode<MessageListDto>(listResponse).messages
        ids.map { ref ->
            val detail = authorizedGet(accessToken, "$BASE_URL/messages/${ref.id}?format=metadata&metadataHeaders=From&metadataHeaders=Subject")
            val dto = decode<MessageDto>(detail)
            dto.toSummary()
        }
    }

    suspend fun getMessage(accessToken: String, messageId: String): Result<GmailMessageDetail> = runCatching {
        val response = authorizedGet(accessToken, "$BASE_URL/messages/$messageId?format=full")
        val dto = decode<MessageDto>(response)
        val headers = dto.payload?.headers.orEmpty()
        GmailMessageDetail(
            threadId = dto.threadId,
            subject = headers.value("Subject").orEmpty(),
            sender = headers.value("From").orEmpty(),
            body = dto.payload?.let { extractBody(it) }.orEmpty().ifBlank { dto.snippet },
            rfc822MessageId = headers.value("Message-Id") ?: headers.value("Message-ID"),
        )
    }

    suspend fun markRead(accessToken: String, messageId: String): Result<Unit> = runCatching {
        val response = http.post("$BASE_URL/messages/$messageId/modify") {
            authorize(accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"removeLabelIds":["UNREAD"]}""")
        }
        requireSuccess(response.status.isSuccess(), response.bodyAsText())
    }

    suspend fun sendNew(accessToken: String, to: String, subject: String, body: String): Result<Unit> = runCatching {
        val raw = encodeMime(to = to, subject = subject, body = body)
        send(accessToken, raw, threadId = null)
    }

    suspend fun sendReply(accessToken: String, original: GmailMessageDetail, replyToAddress: String, body: String): Result<Unit> = runCatching {
        val subject = if (original.subject.startsWith("Re:", ignoreCase = true)) original.subject else "Re: ${original.subject}"
        val raw = encodeMime(
            to = replyToAddress,
            subject = subject,
            body = body,
            inReplyTo = original.rfc822MessageId,
            references = original.rfc822MessageId,
        )
        send(accessToken, raw, threadId = original.threadId)
    }

    private suspend fun send(accessToken: String, raw: String, threadId: String?) {
        val bodyJson = buildString {
            append("{\"raw\":\"").append(raw).append('"')
            if (threadId != null) append(",\"threadId\":\"").append(threadId).append('"')
            append('}')
        }
        val response = http.post("$BASE_URL/messages/send") {
            authorize(accessToken)
            contentType(ContentType.Application.Json)
            setBody(bodyJson)
        }
        requireSuccess(response.status.isSuccess(), response.bodyAsText())
    }

    private fun encodeMime(to: String, subject: String, body: String, inReplyTo: String? = null, references: String? = null): String {
        val message = buildString {
            append("To: ").append(to).append("\r\n")
            append("Subject: ").append(subject).append("\r\n")
            inReplyTo?.let { append("In-Reply-To: ").append(it).append("\r\n") }
            references?.let { append("References: ").append(it).append("\r\n") }
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("\r\n")
            append(body)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(message.toByteArray(Charsets.UTF_8))
    }

    /** Prefers the first `text/plain` part, falling back to a crude tag-strip of `text/html` when that's all a message has. */
    private fun extractBody(payload: PayloadDto): String {
        fun search(node: PayloadDto, wantMime: String): String? {
            if (node.mimeType == wantMime && node.body?.data != null) return decodeBase64Url(node.body.data)
            for (part in node.parts) search(part, wantMime)?.let { return it }
            return null
        }
        search(payload, "text/plain")?.let { return it }
        return search(payload, "text/html")?.let { it.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim() }.orEmpty()
    }

    private fun decodeBase64Url(data: String): String {
        val padded = data.let { if (it.length % 4 == 0) it else it + "=".repeat(4 - it.length % 4) }
        return String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
    }

    private suspend fun authorizedGet(accessToken: String, url: String): String {
        val response = http.get(url) { authorize(accessToken) }
        requireSuccess(response.status.isSuccess(), response.bodyAsText())
        return response.bodyAsText()
    }

    private fun HttpRequestBuilder.authorize(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
    }

    private fun requireSuccess(success: Boolean, body: String) {
        if (!success) error("Gmail API request failed: $body")
    }

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    private fun List<HeaderDto>.value(name: String): String? = firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    @Serializable
    private data class ProfileDto(val emailAddress: String)

    @Serializable
    private data class MessageListDto(val messages: List<MessageRefDto> = emptyList())

    @Serializable
    private data class MessageRefDto(val id: String, val threadId: String)

    @Serializable
    private data class MessageDto(
        val id: String,
        val threadId: String,
        val snippet: String = "",
        val labelIds: List<String> = emptyList(),
        val payload: PayloadDto? = null,
    ) {
        fun toSummary() = GmailMessageSummary(
            messageId = id,
            threadId = threadId,
            sender = payload?.headers.orEmpty().value("From").orEmpty(),
            subject = payload?.headers.orEmpty().value("Subject").orEmpty(),
            snippet = snippet,
            unread = "UNREAD" in labelIds,
        )
    }

    @Serializable
    private data class PayloadDto(
        val mimeType: String? = null,
        val headers: List<HeaderDto> = emptyList(),
        val body: BodyDto? = null,
        val parts: List<PayloadDto> = emptyList(),
    )

    @Serializable
    private data class HeaderDto(val name: String, val value: String)

    @Serializable
    private data class BodyDto(val data: String? = null)
}
