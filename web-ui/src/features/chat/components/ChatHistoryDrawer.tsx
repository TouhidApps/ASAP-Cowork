import { PlusIcon } from '@/features/chat/icons'
import type { Conversation } from '@/features/chat/types'

function formatRelativeTime(timestamp: number): string {
  const diffMinutes = Math.round((Date.now() - timestamp) / 60_000)
  if (diffMinutes < 1) return 'just now'
  if (diffMinutes < 60) return `${diffMinutes}m ago`
  const diffHours = Math.round(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  const diffDays = Math.round(diffHours / 24)
  if (diffDays < 7) return `${diffDays}d ago`
  return new Date(timestamp).toLocaleDateString()
}

export function ChatHistoryDrawer({
  open,
  conversations,
  currentConversationId,
  disabled,
  onClose,
  onSelect,
  onNewChat,
}: {
  open: boolean
  conversations: Conversation[]
  currentConversationId: string | null
  disabled: boolean
  onClose: () => void
  onSelect: (conversationId: string) => void
  onNewChat: () => void
}) {
  if (!open) return null

  return (
    <>
      <div className="chat-drawer-backdrop" onClick={onClose} />
      <aside className="chat-drawer">
        <div className="chat-drawer-header">
          <h3>Chat history</h3>
          <button className="chat-drawer-close" onClick={onClose} aria-label="Close history">
            ×
          </button>
        </div>

        <button className="chat-drawer-new" onClick={onNewChat} disabled={disabled}>
          <PlusIcon />
          New chat
        </button>

        <ul className="chat-drawer-list">
          {conversations.length === 0 && <li className="chat-drawer-empty">No conversations yet.</li>}
          {conversations.map((conversation) => (
            <li key={conversation.id}>
              <button
                className={`chat-drawer-item${conversation.id === currentConversationId ? ' active' : ''}`}
                onClick={() => onSelect(conversation.id)}
                disabled={disabled || conversation.id === currentConversationId}
              >
                <span className="chat-drawer-item-title">{conversation.title}</span>
                <span className="chat-drawer-item-time">{formatRelativeTime(conversation.updatedAt)}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>
    </>
  )
}
