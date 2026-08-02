import { apiDelete, apiGet, apiPost, apiPut } from '@/api/client'
import type { Note } from '@/features/notes/types'

export function fetchNotes(): Promise<Note[]> {
  return apiGet<Note[]>('/api/v1/notes')
}

export function createNote(content: string): Promise<Note> {
  return apiPost<Note>('/api/v1/notes', { content })
}

export function updateNote(id: string, content: string): Promise<Note> {
  return apiPut<Note>(`/api/v1/notes/${id}`, { content })
}

export function deleteNote(id: string): Promise<void> {
  return apiDelete<void>(`/api/v1/notes/${id}`)
}
