/** Mirrors chat-gateway's features/admin/WorkspaceModels.kt. */
export interface WorkspaceStatus {
  configured: boolean
  root: string
}

export interface WorkspaceBrowseEntry {
  name: string
  path: string
}

export interface WorkspaceBrowseResult {
  path: string
  parent: string | null
  entries: WorkspaceBrowseEntry[]
}

export interface StorageCategory {
  name: string
  label: string
  fileCount: number
  totalBytes: number
}

export interface StorageStatus {
  categories: StorageCategory[]
}

export interface BackupItem {
  name: string
  label: string
}

export interface BackupResult {
  zipPath: string
  fileCount: number
  totalBytes: number
}
