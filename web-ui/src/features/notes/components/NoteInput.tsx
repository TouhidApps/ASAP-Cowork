import { useLayoutEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { SendIcon } from '@/features/chat/icons'

const MAX_TEXTAREA_HEIGHT = 200

/** Same auto-growing-textarea/Enter-to-submit mechanic as MessageInput, minus the attachment pipeline — notes are plain text. */
export function NoteInput({ onSend }: { onSend: (content: string) => void }) {
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    const el = textareaRef.current
    if (!el) return
    const resize = () => {
      el.style.height = 'auto'
      el.style.height = `${Math.min(el.scrollHeight, MAX_TEXTAREA_HEIGHT)}px`
    }
    resize()
    // Chat and Notes stay mounted simultaneously and are only toggled via
    // the `hidden` attribute (see AppLayout) — a textarea that mounts while
    // its tab is hidden measures a 0 scrollHeight, and `value` alone never
    // changes when the tab is later revealed. Re-measure on any resize,
    // which includes the hidden -> visible transition.
    const observer = new ResizeObserver(resize)
    observer.observe(el)
    return () => observer.disconnect()
  }, [value])

  const submit = () => {
    if (!value.trim()) return
    onSend(value)
    setValue('')
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    submit()
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form className="chat-input-bar" onSubmit={handleSubmit}>
      <div className="chat-input-shell">
        <textarea
          ref={textareaRef}
          className="chat-input"
          rows={1}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Write a note…"
          autoFocus
        />
        <button className="chat-send-button" type="submit" disabled={!value.trim()} aria-label="Add note">
          <SendIcon />
        </button>
      </div>
      <p className="chat-input-hint">Press Enter to save, Shift + Enter for a new line.</p>
    </form>
  )
}
