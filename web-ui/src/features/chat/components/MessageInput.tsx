import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent, KeyboardEvent } from 'react'
import { uploadAttachment } from '@/features/chat/api'
import { resizeImageIfNeeded } from '@/features/chat/imageResize'
import { AttachIcon, CloseIcon, SendIcon } from '@/features/chat/icons'
import type { Attachment } from '@/features/chat/types'

const MAX_TEXTAREA_HEIGHT = 200
const ACCEPTED_IMAGE_TYPES = 'image/png,image/jpeg,image/gif,image/webp'

interface PendingAttachment {
  key: string
  previewUrl: string
  uploading: boolean
  attachment?: Attachment
  error?: string
}

export function MessageInput({
  sendDisabled,
  onSend,
  prefill,
}: {
  /** Blocks *sending* (not typing) — true while disconnected or a reply is still streaming in. Typing/editing/attaching should never be blocked by connection state; only the actual send needs a live socket. */
  sendDisabled: boolean
  onSend: (content: string, attachments: Attachment[]) => void
  /** Set from outside (e.g. clicking a starter prompt) to fill the composer without sending it — the user still reviews/edits and hits send themselves. `nonce` (not the text) is the effect's dependency, so picking the same prompt twice in a row still refills it. */
  prefill?: { text: string; nonce: number } | null
}) {
  const [value, setValue] = useState('')
  const [pending, setPending] = useState<PendingAttachment[]>([])
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, MAX_TEXTAREA_HEIGHT)}px`
  }, [value])

  useEffect(() => {
    if (!prefill) return
    setValue(prefill.text)
    textareaRef.current?.focus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prefill?.nonce])

  const isUploading = pending.some((item) => item.uploading)
  const readyAttachments = pending.filter((item): item is PendingAttachment & { attachment: Attachment } =>
    Boolean(item.attachment),
  )

  const submit = () => {
    if ((!value.trim() && readyAttachments.length === 0) || sendDisabled || isUploading) return
    onSend(
      value,
      readyAttachments.map((item) => item.attachment),
    )
    setValue('')
    pending.forEach((item) => URL.revokeObjectURL(item.previewUrl))
    setPending([])
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

  const handleFilesSelected = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files
    if (!files || files.length === 0) {
      event.target.value = ''
      return
    }
    // Snapshot into a plain array before clearing the input — `files` is a
    // live FileList backed by the input's own selection, so resetting
    // `value` first (to allow re-selecting the same file later) can empty
    // this same reference before it's ever read.
    const selected = Array.from(files)
    event.target.value = ''

    selected.forEach((file) => {
      const key = `${Date.now()}-${Math.random()}`
      const previewUrl = URL.createObjectURL(file)
      setPending((prev) => [...prev, { key, previewUrl, uploading: true }])

      resizeImageIfNeeded(file)
        .then((processed) => uploadAttachment(processed))
        .then((attachment) => {
          setPending((prev) => prev.map((item) => (item.key === key ? { ...item, uploading: false, attachment } : item)))
        })
        .catch((error: unknown) => {
          const message = error instanceof Error ? error.message : 'Upload failed'
          setPending((prev) => prev.map((item) => (item.key === key ? { ...item, uploading: false, error: message } : item)))
        })
    })
  }

  const removeAttachment = (key: string) => {
    setPending((prev) => {
      const item = prev.find((i) => i.key === key)
      if (item) URL.revokeObjectURL(item.previewUrl)
      return prev.filter((i) => i.key !== key)
    })
  }

  return (
    <form className="chat-input-bar" onSubmit={handleSubmit}>
      {pending.length > 0 && (
        <div className="chat-attachments-preview">
          {pending.map((item) => (
            <div className="chat-attachment-thumb" key={item.key}>
              <img src={item.previewUrl} alt="" />
              {item.uploading && <span className="chat-attachment-spinner" aria-label="Uploading" />}
              {item.error && (
                <span className="chat-attachment-error" title={item.error} aria-label={item.error}>
                  !
                </span>
              )}
              <button
                type="button"
                className="chat-attachment-remove"
                onClick={() => removeAttachment(item.key)}
                aria-label="Remove attachment"
              >
                <CloseIcon />
              </button>
            </div>
          ))}
        </div>
      )}
      <div className="chat-input-shell">
        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_IMAGE_TYPES}
          multiple
          hidden
          onChange={handleFilesSelected}
        />
        <button
          type="button"
          className="chat-attach-button"
          onClick={() => fileInputRef.current?.click()}
          aria-label="Attach image"
        >
          <AttachIcon />
        </button>
        <textarea
          ref={textareaRef}
          className="chat-input"
          rows={1}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Describe the app you want to build..."
          autoFocus
        />
        <button
          className="chat-send-button"
          type="submit"
          disabled={sendDisabled || isUploading || (!value.trim() && readyAttachments.length === 0)}
          aria-label="Send message"
        >
          <SendIcon />
        </button>
      </div>
      <p className="chat-input-hint">Press Enter to send, Shift + Enter for a new line.</p>
    </form>
  )
}
