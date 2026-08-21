import { useEffect, useState } from 'react'
import { getMarkdownContent, listMarkdownFiles } from '@/features/plan/api'
import { MarkdownView } from '@/features/plan/components/MarkdownView'
import type { MarkdownFileEntry } from '@/features/plan/types'
import '@/features/plan/plan.css'

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

export function PlanPreviewPanel({ onClose }: { onClose: () => void }) {
  const [files, setFiles] = useState<MarkdownFileEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [content, setContent] = useState<string | null>(null)
  const [contentLoading, setContentLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    setError(null)
    listMarkdownFiles()
      .then((data) => {
        setFiles(data)
        setSelectedPath((current) => current ?? data[0]?.path ?? null)
      })
      .catch(() => setError('Could not load Markdown files.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!selectedPath) {
      setContent(null)
      return
    }
    setContentLoading(true)
    setContent(null)
    getMarkdownContent(selectedPath)
      .then(setContent)
      .catch(() => setError('Could not load this file.'))
      .finally(() => setContentLoading(false))
  }, [selectedPath])

  return (
    <aside className="plan-preview-panel">
      <div className="chat-drawer-header">
        <h3>Plan preview</h3>
        <button className="chat-drawer-close" onClick={onClose} aria-label="Close plan preview">
          ×
        </button>
      </div>

      {error && <p className="chat-error">{error}</p>}

      <div className="plan-preview-body">
        <ul className="chat-drawer-list plan-preview-list">
          {!loading && files.length === 0 && (
            <li className="chat-drawer-empty">No Markdown files yet — plans and docs the AI writes will show up here.</li>
          )}
          {files.map((file) => (
            <li key={file.path}>
              <button
                className={`chat-drawer-item${file.path === selectedPath ? ' active' : ''}`}
                onClick={() => setSelectedPath(file.path)}
              >
                <span className="chat-drawer-item-title">{file.name}</span>
                <span className="chat-drawer-item-time">
                  {file.path} · {formatRelativeTime(file.updatedAt)}
                </span>
              </button>
            </li>
          ))}
        </ul>

        <div className="plan-preview-detail">
          {!selectedPath && <p className="chat-drawer-empty">Select a Markdown file to preview it.</p>}
          {selectedPath && contentLoading && <p className="chat-drawer-empty">Loading…</p>}
          {selectedPath && !contentLoading && content !== null && <MarkdownView content={content} />}
        </div>
      </div>
    </aside>
  )
}
