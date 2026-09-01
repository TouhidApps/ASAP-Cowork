package bd.asap.cowork.toolintegrations.notify

import java.awt.Color
import java.awt.Graphics2D
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * OS-level notification via `java.awt.SystemTray` — JDK-builtin, no new
 * dependency, and the right fit for this app's local-first, single desktop
 * process deployment (chat-gateway runs directly on the developer's
 * machine; there's no separate desktop shell to notify through). A tray
 * icon is created lazily on first use and reused for every notification
 * after that. No-ops (with a log line) when the platform doesn't support a
 * system tray at all — e.g. some headless Linux setups — rather than
 * throwing, since [bd.asap.cowork.toolintegrations.notify.NotificationDispatcher]
 * already isolates one failing channel from the rest.
 */
class DesktopNotificationChannel : NotificationChannel {
    override val id: String = "desktop"

    private val trayIcon: TrayIcon? by lazy { createTrayIcon() }

    override suspend fun send(notification: Notification) {
        val icon = trayIcon ?: return
        icon.displayMessage(notification.title, notification.body, TrayIcon.MessageType.INFO)
    }

    private fun createTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) {
            println("[DesktopNotificationChannel] SystemTray isn't supported on this platform — desktop notifications are disabled.")
            return null
        }
        return try {
            // A plain solid-color square — no bundled icon asset needed, and
            // the tray icon's image is barely visible anyway compared to the
            // popup text that actually carries the notification.
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
                val g: Graphics2D = createGraphics()
                g.color = Color(0x4C6EF5)
                g.fillRect(0, 0, 16, 16)
                g.dispose()
            }
            TrayIcon(image, "ASAP-Cowork").apply {
                isImageAutoSize = true
                SystemTray.getSystemTray().add(this)
            }
        } catch (e: Exception) {
            println("[DesktopNotificationChannel] Failed to create a system tray icon: ${e.message}")
            null
        }
    }
}
