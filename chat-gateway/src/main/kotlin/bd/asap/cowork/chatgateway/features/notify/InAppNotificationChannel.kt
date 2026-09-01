package bd.asap.cowork.chatgateway.features.notify

import bd.asap.cowork.chatgateway.ChatEvent
import bd.asap.cowork.chatgateway.plugins.ChatSocketRegistry
import bd.asap.cowork.toolintegrations.notify.Notification
import bd.asap.cowork.toolintegrations.notify.NotificationChannel

/**
 * Delivers a [Notification] to every open chat connection as a `notification`
 * [ChatEvent] over the WebSocket — lives here rather than in
 * `tool-integrations` because it needs [ChatSocketRegistry]'s live
 * connections, which that module (used by plain agent code with no HTTP
 * server) must not depend on.
 */
class InAppNotificationChannel : NotificationChannel {
    override val id: String = "in_app"

    override suspend fun send(notification: Notification) {
        ChatSocketRegistry.broadcast(
            ChatEvent(
                type = "notification",
                title = notification.title,
                message = notification.body,
                agentId = notification.sourceAgentId,
                severity = notification.severity.name.lowercase(),
            ),
        )
    }
}
