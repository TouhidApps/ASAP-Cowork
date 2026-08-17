import { apiGet, apiPost } from '@/api/client'
import type { FileDiff, HistoryEntry } from '@/features/history/types'

export function listHistory(): Promise<HistoryEntry[]> {
  return apiGet<HistoryEntry[]>('/api/v1/history')
}

export function getHistoryDiff(commitId: string, against: 'parent' | 'working' = 'parent'): Promise<FileDiff[]> {
  return apiGet<FileDiff[]>(`/api/v1/history/${commitId}/diff?against=${against}`)
}

export function revertHistory(commitId: string): Promise<HistoryEntry> {
  return apiPost<HistoryEntry>(`/api/v1/history/${commitId}/revert`, {})
}
