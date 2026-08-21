import { useState } from 'react'
import { RefreshIcon } from '@/features/chat/icons'
import { ProjectFileDetail } from '@/features/project/components/ProjectFileDetail'
import { ProjectTree } from '@/features/project/components/ProjectTree'
import '@/features/project/project.css'

/**
 * Android Studio-style project navigator for the chat page — browses
 * whatever directory the agents are currently reading/writing (the
 * confirmed workspace root), lazily per folder, with a preview pane for
 * text and image files.
 */
export function ProjectExplorerPanel({ onClose }: { onClose: () => void }) {
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [showHidden, setShowHidden] = useState(false)

  return (
    <aside className="project-explorer-panel">
      <div className="chat-drawer-header">
        <h3>Project files</h3>
        <div className="project-explorer-header-actions">
          <button className="project-refresh-button" onClick={() => setRefreshKey((k) => k + 1)} aria-label="Refresh project files">
            <RefreshIcon />
          </button>
          <button className="chat-drawer-close" onClick={onClose} aria-label="Close project files">
            ×
          </button>
        </div>
      </div>

      <div className="project-explorer-body">
        <div className="project-explorer-tree">
          <ProjectTree
            selectedPath={selectedPath}
            onSelectFile={setSelectedPath}
            refreshKey={refreshKey}
            showHidden={showHidden}
            onShowHiddenChange={setShowHidden}
          />
        </div>
        <div className="project-explorer-detail">
          <ProjectFileDetail path={selectedPath} />
        </div>
      </div>
    </aside>
  )
}
