import { useEffect } from 'react'
import { useNotifications } from '@/features/notifications/useNotifications'
import '@/features/notifications/notifications.css'

const AUTO_DISMISS_MS = 8000

/** Mounted once at the app-layout level (not inside ChatPage) so a toast fires no matter which tab is active. */
export function NotificationToast() {
  const items = useNotifications((s) => s.items)
  const dismiss = useNotifications((s) => s.dismiss)

  useEffect(() => {
    const timers = items.map((item) => setTimeout(() => dismiss(item.id), AUTO_DISMISS_MS))
    return () => timers.forEach(clearTimeout)
  }, [items, dismiss])

  if (items.length === 0) return null

  return (
    <div className="notification-toast-stack">
      {items.map((item) => (
        <div key={item.id} className={`notification-toast${item.severity === 'important' ? ' notification-toast--important' : ''}`}>
          <div className="notification-toast-body">
            <strong className="notification-toast-title">{item.title}</strong>
            <span className="notification-toast-text">{item.body}</span>
          </div>
          <button type="button" className="notification-toast-close" onClick={() => dismiss(item.id)} aria-label="Dismiss">
            ×
          </button>
        </div>
      ))}
    </div>
  )
}
