import { useEffect, useState } from 'react'
import { ChevronRightIcon, FileTextIcon, FolderIcon, ImageFileIcon } from '@/features/chat/icons'
import { fetchProjectTree } from '@/features/project/api'
import type { ProjectEntry } from '@/features/project/types'

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'ico', 'svg'])

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function iconFor(entry: ProjectEntry) {
  if (entry.type === 'DIRECTORY') return <FolderIcon />
  const ext = entry.name.split('.').pop()?.toLowerCase()
  return ext && IMAGE_EXTENSIONS.has(ext) ? <ImageFileIcon /> : <FileTextIcon />
}

function visibleEntries(entries: ProjectEntry[], showHidden: boolean): ProjectEntry[] {
  return showHidden ? entries : entries.filter((entry) => !entry.name.startsWith('.'))
}

function TreeNode({
  entry,
  depth,
  selectedPath,
  showHidden,
  onSelectFile,
}: {
  entry: ProjectEntry
  depth: number
  selectedPath: string | null
  showHidden: boolean
  onSelectFile: (path: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const [children, setChildren] = useState<ProjectEntry[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isDir = entry.type === 'DIRECTORY'

  const toggle = () => {
    if (!isDir) {
      onSelectFile(entry.path)
      return
    }
    if (!expanded && children === null) {
      setLoading(true)
      setError(null)
      fetchProjectTree(entry.path)
        .then((result) => setChildren(result.entries))
        .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load folder'))
        .finally(() => setLoading(false))
    }
    setExpanded((v) => !v)
  }

  const visibleChildren = children ? visibleEntries(children, showHidden) : []

  return (
    <div className="project-tree-node">
      <button
        className={`project-tree-row${!isDir && selectedPath === entry.path ? ' active' : ''}`}
        style={{ paddingLeft: 8 + depth * 16 }}
        onClick={toggle}
        title={entry.path}
      >
        {isDir ? (
          <span className={`project-tree-chevron${expanded ? ' expanded' : ''}`} aria-hidden="true">
            <ChevronRightIcon />
          </span>
        ) : (
          <span className="project-tree-chevron-spacer" aria-hidden="true" />
        )}
        <span className="project-tree-icon">{iconFor(entry)}</span>
        <span className="project-tree-name">{entry.name}</span>
        {!isDir && entry.sizeBytes != null && <span className="project-tree-size">{formatSize(entry.sizeBytes)}</span>}
      </button>

      {isDir && expanded && (
        <div className="project-tree-children">
          {loading && <div className="project-tree-empty">Loading…</div>}
          {error && <div className="project-tree-error">{error}</div>}
          {!loading && !error && children?.length === 0 && <div className="project-tree-empty">Empty folder</div>}
          {!loading &&
            !error &&
            children &&
            children.length > 0 &&
            visibleChildren.length === 0 && <div className="project-tree-empty">Only hidden files here</div>}
          {!loading &&
            visibleChildren.map((child) => (
              <TreeNode
                key={child.path}
                entry={child}
                depth={depth + 1}
                selectedPath={selectedPath}
                showHidden={showHidden}
                onSelectFile={onSelectFile}
              />
            ))}
        </div>
      )}
    </div>
  )
}

export function ProjectTree({
  selectedPath,
  onSelectFile,
  refreshKey,
  showHidden,
  onShowHiddenChange,
}: {
  selectedPath: string | null
  onSelectFile: (path: string) => void
  refreshKey: number
  showHidden: boolean
  onShowHiddenChange: (value: boolean) => void
}) {
  const [entries, setEntries] = useState<ProjectEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setEntries(null)
    setError(null)
    fetchProjectTree()
      .then((result) => setEntries(result.entries))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load project files'))
  }, [refreshKey])

  const visibleRoot = entries ? visibleEntries(entries, showHidden) : []

  return (
    <div className="project-tree-pane">
      <label className="project-tree-hidden-toggle">
        <input type="checkbox" checked={showHidden} onChange={(e) => onShowHiddenChange(e.target.checked)} />
        Show hidden files
      </label>

      {error && <p className="chat-error">{error}</p>}
      {!error && !entries && <p className="chat-drawer-empty">Loading project…</p>}
      {!error && entries && entries.length === 0 && <p className="chat-drawer-empty">Workspace is empty.</p>}
      {!error && entries && entries.length > 0 && visibleRoot.length === 0 && (
        <p className="chat-drawer-empty">Only hidden files here — check "Show hidden files".</p>
      )}

      {/* Keyed on refreshKey so a manual refresh remounts every node, clearing
          stale cached children/expand-state in already-opened subfolders too. */}
      {visibleRoot.length > 0 && (
        <div className="project-tree" key={refreshKey}>
          {visibleRoot.map((entry) => (
            <TreeNode
              key={entry.path}
              entry={entry}
              depth={0}
              selectedPath={selectedPath}
              showHidden={showHidden}
              onSelectFile={onSelectFile}
            />
          ))}
        </div>
      )}
    </div>
  )
}
