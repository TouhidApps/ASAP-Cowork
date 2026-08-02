import { useEffect, useRef } from 'react'
import { NoteBubble } from '@/features/notes/components/NoteBubble'
import { NoteInput } from '@/features/notes/components/NoteInput'
import { useNotes } from '@/features/notes/useNotes'
import '@/features/notes/notes.css'

export function NotesWindow() {
  const { notes, loading, error, add, edit, remove } = useNotes()
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [notes.length])

  return (
    <div className="chat-window">
      <div className="chat-toolbar">
        <div className="chat-toolbar-left">
          <span className="notes-title">Notes</span>
        </div>
      </div>
      <div className="chat-scroll" ref={scrollRef}>
        {loading && <p className="chat-empty-state">Loading notes…</p>}
        {!loading && notes.length === 0 && (
          <p className="chat-empty-state">
            No notes yet — jot down a Jira ticket, a Slack channel, or anything else worth keeping around.
          </p>
        )}
        {!loading && notes.length > 0 && (
          <div className="chat-messages notes-list">
            {notes.map((note) => (
              <NoteBubble
                key={note.id}
                note={note}
                onEdit={(content) => edit(note.id, content)}
                onDelete={() => remove(note.id)}
              />
            ))}
          </div>
        )}
      </div>
      <div className="chat-composer">
        {error && <p className="chat-error">{error}</p>}
        <NoteInput onSend={add} />
      </div>
    </div>
  )
}
