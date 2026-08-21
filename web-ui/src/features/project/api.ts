import { API_BASE_URL, apiGet } from '@/api/client'
import type { ProjectFileResult, ProjectTreeResult } from '@/features/project/types'

export function fetchProjectTree(path?: string): Promise<ProjectTreeResult> {
  const query = path ? `?path=${encodeURIComponent(path)}` : ''
  return apiGet<ProjectTreeResult>(`/api/v1/project/tree${query}`)
}

export function fetchProjectFile(path: string): Promise<ProjectFileResult> {
  return apiGet<ProjectFileResult>(`/api/v1/project/file?path=${encodeURIComponent(path)}`)
}

export function projectFileRawUrl(path: string): string {
  return `${API_BASE_URL}/api/v1/project/file/raw?path=${encodeURIComponent(path)}`
}
