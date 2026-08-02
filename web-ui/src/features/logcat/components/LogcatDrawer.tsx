import { useEffect, useRef, useState } from 'react'
import type { LogcatLevel, LogcatScope } from '@/features/logcat/useLogcat'
import { useLogcat } from '@/features/logcat/useLogcat'

// How close to the bottom (in px) the user has to be for new lines to
// auto-scroll the view — lets someone scroll up to read history without
// getting yanked back down by every new log line.
const AUTO_SCROLL_THRESHOLD_PX = 48

const LEVEL_OPTIONS: { value: LogcatLevel | ''; label: string }[] = [
  { value: '', label: 'Verbose' },
  { value: 'D', label: 'Debug+' },
  { value: 'I', label: 'Info+' },
  { value: 'W', label: 'Warn+' },
  { value: 'E', label: 'Error+' },
]

export function LogcatDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [scope, setScope] = useState<LogcatScope>('app')
  const [level, setLevel] = useState<LogcatLevel | ''>('')
  const [search, setSearch] = useState('')
  const { connected, error, lines, deviceName, packageName, clear } = useLogcat(open, scope, level || null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const stickToBottomRef = useRef(true)

  // Plain substring match, client-side — unlike scope/level (adb filterspecs
  // that need the stream reopened), this just narrows what's already
  // buffered, so it updates instantly as the user types with no reconnect.
  const needle = search.trim().toLowerCase()
  const filteredLines = needle ? lines.filter((line) => line.toLowerCase().includes(needle)) : lines

  useEffect(() => {
    const el = scrollRef.current
    if (!el || !stickToBottomRef.current) return
    el.scrollTop = el.scrollHeight
  }, [filteredLines])

  if (!open) return null

  const handleScroll = () => {
    const el = scrollRef.current
    if (!el) return
    stickToBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < AUTO_SCROLL_THRESHOLD_PX
  }

  return (
    <>
      <div className="chat-drawer-backdrop" onClick={onClose} />
      <aside className="chat-drawer logcat-drawer">
        <div className="chat-drawer-header">
          <h3>Device logs</h3>
          <button className="chat-drawer-close" onClick={onClose} aria-label="Close device logs">
            ×
          </button>
        </div>

        {deviceName && (
          <p className="logcat-device-info">
            {deviceName}
            {packageName ? ` · ${packageName}` : ''}
          </p>
        )}

        <div className="logcat-status">
          <span className={`logcat-status-dot${connected ? ' logcat-status-dot--live' : ''}`} aria-hidden="true" />
          {connected ? 'Live' : error ? 'Disconnected' : 'Connecting…'}
          <button className="logcat-clear-button" onClick={clear} disabled={lines.length === 0}>
            Clear
          </button>
        </div>

        <div className="logcat-filters">
          <select
            className="logcat-filter-select"
            value={scope}
            onChange={(e) => setScope(e.target.value as LogcatScope)}
            aria-label="Which process to show logs for"
          >
            <option value="app">App only</option>
            <option value="all">All processes</option>
          </select>
          <select
            className="logcat-filter-select"
            value={level}
            onChange={(e) => setLevel(e.target.value as LogcatLevel | '')}
            aria-label="Minimum log level"
          >
            {LEVEL_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            type="text"
            className="logcat-filter-search"
            placeholder="Filter (e.g. http, OkHttp, exception)"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Filter log lines by text"
          />
        </div>

        {error && <p className="chat-error logcat-error">{error}</p>}

        <div className="logcat-view" ref={scrollRef} onScroll={handleScroll}>
          {lines.length === 0 && !error ? (
            <p className="chat-drawer-empty">Waiting for log output…</p>
          ) : filteredLines.length === 0 ? (
            <p className="chat-drawer-empty">No lines match "{search.trim()}".</p>
          ) : (
            filteredLines.map((line, index) => (
              <div key={index} className="logcat-line">
                {line}
              </div>
            ))
          )}
        </div>
      </aside>
    </>
  )
}
