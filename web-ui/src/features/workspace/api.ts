import { apiGet, apiPost } from '@/api/client'
import type {
  BackupItem,
  BackupResult,
  StorageStatus,
  WorkspaceBrowseResult,
  WorkspaceStatus,
} from '@/features/workspace/types'

export function fetchWorkspaceStatus(): Promise<WorkspaceStatus> {
  return apiGet<WorkspaceStatus>('/api/v1/admin/workspace')
}

export function browseWorkspace(path?: string): Promise<WorkspaceBrowseResult> {
  const query = path ? `?path=${encodeURIComponent(path)}` : ''
  return apiGet<WorkspaceBrowseResult>(`/api/v1/admin/workspace/browse${query}`)
}

export function confirmWorkspace(path: string): Promise<WorkspaceStatus> {
  return apiPost<WorkspaceStatus>('/api/v1/admin/workspace/confirm', { path })
}

export function fetchStorageStatus(): Promise<StorageStatus> {
  return apiGet<StorageStatus>('/api/v1/admin/workspace/storage')
}

export function cleanupStorage(target: string): Promise<StorageStatus> {
  return apiPost<StorageStatus>('/api/v1/admin/workspace/storage/cleanup', { target })
}

export function fetchBackupItems(): Promise<{ items: BackupItem[] }> {
  return apiGet<{ items: BackupItem[] }>('/api/v1/admin/workspace/backup/items')
}

export function backupWorkspace(destination: string, items: string[]): Promise<BackupResult> {
  return apiPost<BackupResult>('/api/v1/admin/workspace/backup', { destination, items })
}
