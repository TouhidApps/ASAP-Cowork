import { create } from 'zustand'

export interface NotificationItem {
  id: string
  title: string
  body: string
  severity: 'info' | 'important'
  createdAt: number
}

interface NotificationsState {
  items: NotificationItem[]
  push: (notification: Omit<NotificationItem, 'id' | 'createdAt'>) => void
  dismiss: (id: string) => void
}

/**
 * In-memory only (no persist middleware, unlike useAdminAuth) — a toast is
 * meant to be seen once and go away, not survive a page refresh. Fed by
 * useChat's `notification` WebSocket event handler, which is how a
 * background agent action (e.g. the email agent's poll finding new mail)
 * reaches the UI outside of any chat message/response cycle.
 */
export const useNotifications = create<NotificationsState>((set) => ({
  items: [],
  push: (notification) =>
    set((s) => ({ items: [...s.items, { ...notification, id: `notif-${Date.now()}-${Math.random().toString(36).slice(2)}`, createdAt: Date.now() }] })),
  dismiss: (id) => set((s) => ({ items: s.items.filter((item) => item.id !== id) })),
}))
