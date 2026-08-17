import { useEffect, useState } from 'react'
import { DiffIcon } from '@/features/chat/icons'
import { getHistoryDiff, listHistory, revertHistory } from '@/features/history/api'
import { FileDiffView } from '@/features/history/components/FileDiffView'
import type { FileDiff, HistoryEntry } from '@/features/history/types'
import '@/features/history/history.css'

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

export function CodeChangesPanel({ onClose }: { onClose: () => void }) {
  const [entries, setEntries] = useState<HistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [diff, setDiff] = useState<FileDiff[] | null>(null)
  const [diffLoading, setDiffLoading] = useState(false)
  const [confirmingRevert, setConfirmingRevert] = useState<string | null>(null)
  const [reverting, setReverting] = useState(false)

  useEffect(() => {
    setLoading(true)
    setError(null)
    listHistory()
      .then((data) => {
        setEntries(data)
        setSelectedId((current) => current ?? data[0]?.commitId ?? null)
      })
      .catch(() => setError('Could not load change history.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setDiff(null)
      return
    }
    setDiffLoading(true)
    setDiff(null)
    getHistoryDiff(selectedId)
      .then(setDiff)
      .catch(() => setError('Could not load diff for this change.'))
      .finally(() => setDiffLoading(false))
  }, [selectedId])

  const handleRevert = async (commitId: string) => {
    setReverting(true)
    setError(null)
    try {
      const reverted = await revertHistory(commitId)
      setConfirmingRevert(null)
      setEntries((current) => [reverted, ...current])
      setSelectedId(reverted.commitId)
    } catch {
      setError('Revert failed — files were left unchanged.')
    } finally {
      setReverting(false)
    }
  }

  return (
    <aside className="code-changes-panel">
      <div className="chat-drawer-header">
        <h3>Code changes</h3>
        <button className="chat-drawer-close" onClick={onClose} aria-label="Close code changes">
          ×
        </button>
      </div>

      {error && <p className="chat-error">{error}</p>}

      <div className="code-changes-body">
        <ul className="chat-drawer-list code-changes-list">
          {!loading && entries.length === 0 && <li className="chat-drawer-empty">No changes yet — this fills in as the AI edits files.</li>}
          {entries.map((entry) => (
            <li key={entry.commitId}>
              <button
                className={`chat-drawer-item${entry.commitId === selectedId ? ' active' : ''}`}
                onClick={() => setSelectedId(entry.commitId)}
              >
                <span className="chat-drawer-item-title">{entry.label}</span>
                <span className="chat-drawer-item-time">
                  {formatRelativeTime(entry.createdAt)} · {entry.filesChanged} file{entry.filesChanged === 1 ? '' : 's'}
                </span>
              </button>
            </li>
          ))}
        </ul>

        <div className="code-changes-detail">
          {!selectedId && <p className="chat-drawer-empty">Select a change to see its diff.</p>}
          {selectedId && (
            <>
              <div className="code-changes-detail-actions">
                {confirmingRevert === selectedId ? (
                  <>
                    <span className="code-changes-confirm-label">Restore files to this point?</span>
                    <button
                      className="code-changes-revert-confirm"
                      onClick={() => handleRevert(selectedId)}
                      disabled={reverting}
                    >
                      {reverting ? 'Reverting…' : 'Yes, revert'}
                    </button>
                    <button
                      className="code-changes-revert-cancel"
                      onClick={() => setConfirmingRevert(null)}
                      disabled={reverting}
                    >
                      Cancel
                    </button>
                  </>
                ) : (
                  <button className="code-changes-revert-button" onClick={() => setConfirmingRevert(selectedId)}>
                    <DiffIcon />
                    Revert to here
                  </button>
                )}
                {diff && diff.length > 0 && (
                  <div className="code-changes-legend">
                    <span className="code-changes-legend-item code-changes-legend-item--add">
                      <span className="code-changes-legend-swatch" />
                      Added
                    </span>
                    <span className="code-changes-legend-item code-changes-legend-item--delete">
                      <span className="code-changes-legend-swatch" />
                      Removed
                    </span>
                  </div>
                )}
              </div>

              {diffLoading && <p className="chat-drawer-empty">Loading diff…</p>}
              {!diffLoading && diff && diff.length === 0 && (
                <p className="chat-drawer-empty">No file changes in this entry.</p>
              )}
              {!diffLoading &&
                diff?.map((file) => (
                  <div key={file.path} className="code-changes-file">
                    <div className="code-changes-file-header">
                      <span className={`code-changes-file-badge code-changes-file-badge--${file.changeType.toLowerCase()}`}>
                        {file.changeType}
                      </span>
                      <span className="code-changes-file-path">{file.path}</span>
                    </div>
                    <FileDiffView file={file} />
                  </div>
                ))}
            </>
          )}
        </div>
      </div>
    </aside>
  )
}
