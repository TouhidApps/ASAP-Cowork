import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { browseWorkspace } from '@/features/workspace/api'
import type { WorkspaceBrowseResult } from '@/features/workspace/types'

/**
 * A server-driven folder browser (up / list subfolders / pick one) — not a
 * native OS file dialog, since the "filesystem" being browsed is whatever
 * the chat-gateway process can see, which only makes sense for a
 * local/single-developer deployment (PLAN.md §9's v1 decision).
 */
export function DirectoryPicker({
  chooseLabel,
  renderConfirmMessage,
  busy,
  disabled,
  onChoose,
}: {
  chooseLabel: string
  renderConfirmMessage: (path: string) => ReactNode
  busy?: boolean
  /** Disables starting a new pick — doesn't affect an already-open confirm step. */
  disabled?: boolean
  onChoose: (path: string) => void
}) {
  const [browsing, setBrowsing] = useState<WorkspaceBrowseResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pendingPath, setPendingPath] = useState<string | null>(null)
  const [pathInput, setPathInput] = useState('')

  useEffect(() => {
    browseWorkspace()
      .then((result) => {
        setBrowsing(result)
        setPathInput(result.path)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to browse directories'))
  }, [])

  const goTo = async (path?: string) => {
    setError(null)
    try {
      const result = await browseWorkspace(path)
      setBrowsing(result)
      setPathInput(result.path)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to browse directories')
    }
  }

  if (!browsing) return <p>{error ?? 'Loading…'}</p>

  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12 }}>
      {error && <p style={{ color: '#d94f4f', fontSize: 13, marginTop: 0 }}>{error}</p>}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <button disabled={!browsing.parent} onClick={() => goTo(browsing.parent ?? undefined)}>
          Up
        </button>
        {/* Reaching another disk/partition (e.g. macOS's /Volumes) by clicking
            "Up" alone means walking all the way to the filesystem root first,
            which isn't discoverable — typing or pasting an absolute path here
            jumps straight there. */}
        <input
          type="text"
          value={pathInput}
          onChange={(e) => setPathInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && goTo(pathInput)}
          style={{
            flex: 1,
            minWidth: 0,
            fontSize: 13,
            padding: '5px 8px',
            borderRadius: 6,
            border: '1px solid var(--border)',
            background: 'var(--bg)',
            color: 'var(--text-h)',
            font: 'inherit',
            boxSizing: 'border-box',
          }}
        />
        <button onClick={() => goTo(pathInput)}>Go</button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', maxHeight: 260, overflow: 'auto', marginBottom: 10 }}>
        {browsing.entries.length === 0 && (
          <div style={{ fontSize: 13, opacity: 0.7, padding: '6px 0' }}>No subfolders here.</div>
        )}
        {browsing.entries.map((entry) => (
          <button
            key={entry.path}
            onClick={() => goTo(entry.path)}
            style={{
              textAlign: 'left',
              padding: '6px 8px',
              borderRadius: 6,
              border: 'none',
              background: 'transparent',
              color: 'var(--text)',
              cursor: 'pointer',
            }}
          >
            📁 {entry.name}
          </button>
        ))}
      </div>

      {pendingPath === browsing.path ? (
        <div style={{ border: '1px solid var(--border)', borderRadius: 6, padding: 10 }}>
          <p style={{ margin: '0 0 8px', fontSize: 13 }}>{renderConfirmMessage(pendingPath)}</p>
          <div style={{ display: 'flex', gap: 8 }}>
            <button disabled={busy} onClick={() => onChoose(pendingPath)}>
              {busy ? 'Working…' : 'Confirm'}
            </button>
            <button disabled={busy} onClick={() => setPendingPath(null)}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <button disabled={disabled} onClick={() => setPendingPath(browsing.path)}>
          {chooseLabel}
        </button>
      )}
    </div>
  )
}
