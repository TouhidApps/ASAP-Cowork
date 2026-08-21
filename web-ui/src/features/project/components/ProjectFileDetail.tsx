import { useEffect, useState } from 'react'
import { fetchProjectFile, projectFileRawUrl } from '@/features/project/api'
import { CodeBlock } from '@/features/project/components/CodeBlock'
import type { ProjectFileResult } from '@/features/project/types'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** Detail pane for whatever file is selected in the ProjectTree — text preview, image preview, or a binary-file fallback. */
export function ProjectFileDetail({ path }: { path: string | null }) {
  const [file, setFile] = useState<ProjectFileResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!path) {
      setFile(null)
      setError(null)
      return
    }
    setLoading(true)
    setError(null)
    setFile(null)
    fetchProjectFile(path)
      .then(setFile)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load file'))
      .finally(() => setLoading(false))
  }, [path])

  if (!path) return <p className="chat-drawer-empty">Select a file to preview it.</p>
  if (loading) return <p className="chat-drawer-empty">Loading…</p>
  if (error) return <p className="chat-error">{error}</p>
  if (!file) return null

  return (
    <div className="project-file-detail">
      <div className="project-file-detail-header">
        <span className="project-file-detail-path">{file.path}</span>
        <span className="project-file-detail-meta">
          {file.language ? `${file.language} · ` : ''}
          {formatSize(file.sizeBytes)}
        </span>
      </div>

      {file.kind === 'IMAGE' && (
        <div className="project-file-detail-image">
          <img src={projectFileRawUrl(file.path)} alt={file.path} />
        </div>
      )}

      {file.kind === 'TEXT' && (
        <>
          {file.truncated && (
            <p className="project-file-detail-truncated">
              Showing the first {formatSize(file.sizeBytes)} of a larger file.
            </p>
          )}
          <CodeBlock code={file.content ?? ''} language={file.language} />
        </>
      )}

      {file.kind === 'BINARY' && (
        <div className="project-file-detail-binary">
          <p>Binary file · {formatSize(file.sizeBytes)}</p>
          <a href={projectFileRawUrl(file.path)} target="_blank" rel="noreferrer">
            Open in new tab
          </a>
        </div>
      )}
    </div>
  )
}
