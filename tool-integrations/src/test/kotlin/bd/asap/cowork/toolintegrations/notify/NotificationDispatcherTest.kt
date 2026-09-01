package bd.asap.cowork.toolintegrations.notify

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingChannel(override val id: String) : NotificationChannel {
    val received = mutableListOf<Notification>()
    override suspend fun send(notification: Notification) {
        received += notification
    }
}

private class ThrowingChannel(override val id: String) : NotificationChannel {
    override suspend fun send(notification: Notification) = throw RuntimeException("boom")
}

class NotificationDispatcherTest {
    private val sample = Notification(title = "New email", body = "From Jane", sourceAgentId = "email-agent")

    @Test
    fun `dispatch sends to every channel`() = runBlocking {
        val a = RecordingChannel("a")
        val b = RecordingChannel("b")
        val dispatcher = NotificationDispatcher(listOf(a, b))

        dispatcher.dispatch(sample)

        assertEquals(listOf(sample), a.received)
        assertEquals(listOf(sample), b.received)
    }

    @Test
    fun `dispatch with channelIds only sends to the matching channels`() = runBlocking {
        val a = RecordingChannel("a")
        val b = RecordingChannel("b")
        val dispatcher = NotificationDispatcher(listOf(a, b))

        dispatcher.dispatch(sample, channelIds = setOf("a"))

        assertEquals(listOf(sample), a.received)
        assertEquals(emptyList(), b.received)
    }

    @Test
    fun `a throwing channel does not block delivery to the others`() = runBlocking {
        val recording = RecordingChannel("recording")
        val dispatcher = NotificationDispatcher(listOf(ThrowingChannel("throwing"), recording))

        dispatcher.dispatch(sample)

        assertEquals(listOf(sample), recording.received)
    }
}
