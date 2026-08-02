import { useState } from 'react'
import type { KeyboardEvent } from 'react'
import { CheckIcon, CloseIcon } from '@/features/chat/icons'
import type { Note } from '@/features/notes/types'

function formatTimestamp(timestamp: number): string {
  const diffMinutes = Math.round((Date.now() - timestamp) / 60_000)
  if (diffMinutes < 1) return 'just now'
  if (diffMinutes < 60) return `${diffMinutes}m ago`
  const diffHours = Math.round(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  return new Date(timestamp).toLocaleString()
}

export function NoteBubble({
  note,
  onEdit,
  onDelete,
}: {
  note: Note
  onEdit: (content: string) => void
  onDelete: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(note.content)

  const startEditing = () => {
    setDraft(note.content)
    setEditing(true)
  }

  const save = () => {
    if (draft.trim()) onEdit(draft)
    setEditing(false)
  }

  const cancel = () => setEditing(false)

  const confirmDelete = () => {
    if (window.confirm("Delete this note? This can't be undone.")) {
      onDelete()
    }
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      save()
    } else if (event.key === 'Escape') {
      cancel()
    }
  }

  return (
    <div className="note-row">
      <div className="note-row-content">
        {editing ? (
          <div className="note-edit">
            <textarea
              className="note-edit-textarea"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleKeyDown}
              autoFocus
            />
            <div className="note-edit-actions">
              <button className="note-action-button" onClick={save} aria-label="Save note">
                <CheckIcon /> Save
              </button>
              <button className="note-action-button" onClick={cancel} aria-label="Cancel edit">
                <CloseIcon /> Cancel
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="note-text">{note.content}</div>
            <div className="note-meta">
              <span className="note-time">{formatTimestamp(note.updatedAt)}</span>
              <button className="note-link-button" onClick={startEditing}>
                Edit
              </button>
              <button className="note-link-button note-link-button--danger" onClick={confirmDelete}>
                Delete
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
