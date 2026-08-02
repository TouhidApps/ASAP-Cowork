import { useEffect, useState } from 'react'
import { deleteConversation, fetchConversationMessages, fetchConversations } from '@/features/admin/api'
import type { Conversation, ConversationMessage } from '@/features/admin/types'
import '@/pages/adminConversationPage.css'

export function AdminConversationPage() {
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ConversationMessage[]>([])
  const [loadingList, setLoadingList] = useState(true)
  const [loadingMessages, setLoadingMessages] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [listOpen, setListOpen] = useState(false)

  const loadConversations = () => {
    setLoadingList(true)
    fetchConversations()
      .then((list) => {
        setConversations(list)
        setSelectedId((current) => current ?? list[0]?.id ?? null)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load conversations'))
      .finally(() => setLoadingList(false))
  }

  useEffect(() => {
    loadConversations()
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setMessages([])
      return
    }
    setLoadingMessages(true)
    fetchConversationMessages(selectedId)
      .then(setMessages)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load messages'))
      .finally(() => setLoadingMessages(false))
  }, [selectedId])

  const handleDelete = async (id: string, title: string) => {
    if (!window.confirm(`Delete conversation "${title}"? This can't be undone.`)) return

    setDeletingId(id)
    try {
      await deleteConversation(id)
      if (selectedId === id) setSelectedId(null)
      loadConversations()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete conversation')
    } finally {
      setDeletingId(null)
    }
  }

  const selectedTitle = conversations.find((c) => c.id === selectedId)?.title

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>Conversations</h2>
      {error && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{error}</p>}

      <button
        className="admin-conv-toggle"
        aria-expanded={listOpen}
        onClick={() => setListOpen((open) => !open)}
      >
        <span>{selectedTitle ?? 'Conversations'} {conversations.length > 0 && `(${conversations.length})`}</span>
        <svg
          className="admin-conv-toggle-chevron"
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          aria-hidden="true"
        >
          <path d="M9 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      <div className="admin-conv-body">
        <div className={`admin-conv-list${listOpen ? ' open' : ''}`}>
          {loadingList ? (
            <p>Loading…</p>
          ) : conversations.length === 0 ? (
            <p style={{ opacity: 0.7, fontSize: 14 }}>No conversations yet. Say something on the Chat page.</p>
          ) : (
            conversations.map((conversation) => (
              <div
                key={conversation.id}
                className={`admin-conv-item${conversation.id === selectedId ? ' selected' : ''}`}
              >
                <button
                  className="admin-conv-item-button"
                  onClick={() => {
                    setSelectedId(conversation.id)
                    setListOpen(false)
                  }}
                >
                  <div className="admin-conv-item-title">{conversation.title}</div>
                  <div className="admin-conv-item-date">{new Date(conversation.updatedAt).toLocaleString()}</div>
                </button>
                <button
                  className="admin-conv-item-delete"
                  onClick={() => handleDelete(conversation.id, conversation.title)}
                  disabled={deletingId === conversation.id}
                >
                  {deletingId === conversation.id ? '…' : 'Delete'}
                </button>
              </div>
            ))
          )}
        </div>

        <div className="admin-conv-messages">
          {loadingMessages ? (
            <p>Loading…</p>
          ) : !selectedId ? (
            <p style={{ opacity: 0.7 }}>Select a conversation to view its messages.</p>
          ) : messages.length === 0 ? (
            <p style={{ opacity: 0.7 }}>No messages yet.</p>
          ) : (
            <ul className="admin-conv-message-list">
              {messages.map((message) => (
                <li key={message.id} className="admin-conv-message">
                  <div className="admin-conv-message-meta">
                    {message.role} · {new Date(message.createdAt).toLocaleString()}
                  </div>
                  <div className="admin-conv-message-content">{message.content}</div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
