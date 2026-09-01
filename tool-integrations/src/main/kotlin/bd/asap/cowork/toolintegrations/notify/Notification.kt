package bd.asap.cowork.toolintegrations.notify

enum class NotificationSeverity { INFO, IMPORTANT }

/** A cross-agent push notification — not email-specific. Any agent can build one and hand it to a [NotificationDispatcher]. */
data class Notification(
    val title: String,
    val body: String,
    val sourceAgentId: String,
    val severity: NotificationSeverity = NotificationSeverity.INFO,
)

/**
 * One delivery mechanism a [Notification] can go out through. Implementations
 * live wherever their dependency does: [DesktopNotificationChannel] here
 * (JDK-only), the in-app WebSocket channel in chat-gateway (needs live
 * connections), and — later — a Firebase Cloud Messaging channel in
 * firebase-integration for Android/iOS push, all satisfying this same
 * interface so no call site changes when a channel is added. [id] lets a
 * caller pick a subset by name (e.g. respecting a feature's own
 * on/off toggles) without the dispatcher knowing anything about that
 * feature's settings shape.
 */
interface NotificationChannel {
    val id: String
    suspend fun send(notification: Notification)
}

/**
 * Fans a [Notification] out to every configured [NotificationChannel] (or,
 * when [channelIds] is given, only the ones whose [NotificationChannel.id]
 * is in that set) — general-purpose and settings-unaware by design, so any
 * agent can reuse the same singleton. A channel that throws never blocks the
 * others from still receiving the notification.
 */
class NotificationDispatcher(private val channels: List<NotificationChannel>) {
    suspend fun dispatch(notification: Notification, channelIds: Set<String>? = null) {
        channels
            .filter { channelIds == null || it.id in channelIds }
            .forEach { channel -> runCatching { channel.send(notification) } }
    }
}
