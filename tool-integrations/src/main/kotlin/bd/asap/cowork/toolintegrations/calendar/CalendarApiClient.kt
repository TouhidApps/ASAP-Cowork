package bd.asap.cowork.toolintegrations.calendar

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

data class CalendarEventSummary(
    val eventId: String,
    val title: String,
    val start: String,
    val end: String,
    val location: String?,
)

/**
 * Talks to the Google Calendar REST API directly — no browser involved.
 * Every call here requires a valid OAuth access token carrying
 * [bd.asap.cowork.toolintegrations.email.CALENDAR_OAUTH_SCOPE], obtained the
 * same way [bd.asap.cowork.toolintegrations.email.GmailApiClient] gets its
 * Gmail token — via `GmailOAuthClient`, the caller's job, not this class's.
 *
 * Stub: only the read path exists so far. Add create/update/delete-event
 * endpoints here as agent tools need them, following
 * [bd.asap.cowork.toolintegrations.email.GmailApiClient]'s shape.
 */
object CalendarApiClient {
    private const val BASE_URL = "https://www.googleapis.com/calendar/v3/calendars"
    private const val MAX_RESULTS = 25

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    suspend fun listUpcomingEvents(accessToken: String, calendarId: String = "primary"): Result<List<CalendarEventSummary>> = runCatching {
        val timeMin = urlEncode(Instant.now().toString())
        val url = "$BASE_URL/${urlEncode(calendarId)}/events" +
            "?maxResults=$MAX_RESULTS&singleEvents=true&orderBy=startTime&timeMin=$timeMin"
        val response = http.get(url) { authorize(accessToken) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Calendar API request failed: $text")
        json.decodeFromString<EventListDto>(text).items.map { it.toSummary() }
    }

    private fun HttpRequestBuilder.authorize(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
    }

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    @Serializable
    private data class EventListDto(val items: List<EventDto> = emptyList())

    @Serializable
    private data class EventDto(
        val id: String,
        val summary: String = "(no title)",
        val location: String? = null,
        val start: EventDateTimeDto = EventDateTimeDto(),
        val end: EventDateTimeDto = EventDateTimeDto(),
    ) {
        fun toSummary() = CalendarEventSummary(
            eventId = id,
            title = summary,
            start = start.dateTime ?: start.date.orEmpty(),
            end = end.dateTime ?: end.date.orEmpty(),
            location = location,
        )
    }

    @Serializable
    private data class EventDateTimeDto(val dateTime: String? = null, val date: String? = null)
}
