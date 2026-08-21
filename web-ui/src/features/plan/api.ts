import { apiGet } from '@/api/client'
import type { MarkdownFileEntry } from '@/features/plan/types'

export function listMarkdownFiles(): Promise<MarkdownFileEntry[]> {
  return apiGet<MarkdownFileEntry[]>('/api/v1/plan/files')
}

export function getMarkdownContent(path: string): Promise<string> {
  return apiGet<string>(`/api/v1/plan/content?path=${encodeURIComponent(path)}`)
}
