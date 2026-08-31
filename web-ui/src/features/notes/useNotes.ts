import { useCallback, useEffect, useState } from 'react'
import { createNote, deleteNote, fetchNotes, updateNote } from '@/features/notes/api'
import type { Note } from '@/features/notes/types'

interface NotesState {
  notes: Note[]
  loading: boolean
  error?: string
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

/**
 * A personal scratchpad — like useChat, but no streaming/assistant turn: every action is a plain CRUD call.
 *
 * `active` should track whether the Notes tab is actually visible: Chat and
 * Notes both stay mounted for the app's lifetime (AppLayout toggles them via
 * the `hidden` attribute rather than mounting/unmounting), so a plain
 * mount-only fetch would never see a note the chat agent's add_note tool
 * saved server-side while the user was on the Chat tab. Refetching on every
 * true transition (including the very first) picks that up as soon as the
 * tab is switched to, instead of showing whatever was loaded once at mount.
 */
export function useNotes(active: boolean) {
  const [state, setState] = useState<NotesState>({ notes: [], loading: true })

  useEffect(() => {
    if (!active) return
    setState((s) => ({ ...s, loading: true }))
    fetchNotes()
      .then((notes) => setState({ notes, loading: false }))
      .catch((error: unknown) => setState({ notes: [], loading: false, error: errorMessage(error, 'Failed to load notes') }))
  }, [active])

  const add = useCallback((content: string) => {
    const trimmed = content.trim()
    if (!trimmed) return
    createNote(trimmed)
      .then((note) => setState((s) => ({ ...s, notes: [...s.notes, note], error: undefined })))
      .catch((error: unknown) => setState((s) => ({ ...s, error: errorMessage(error, 'Failed to save note') })))
  }, [])

  const edit = useCallback((id: string, content: string) => {
    const trimmed = content.trim()
    if (!trimmed) return
    updateNote(id, trimmed)
      .then((updated) => setState((s) => ({ ...s, notes: s.notes.map((n) => (n.id === id ? updated : n)), error: undefined })))
      .catch((error: unknown) => setState((s) => ({ ...s, error: errorMessage(error, 'Failed to update note') })))
  }, [])

  const remove = useCallback((id: string) => {
    deleteNote(id)
      .then(() => setState((s) => ({ ...s, notes: s.notes.filter((n) => n.id !== id), error: undefined })))
      .catch((error: unknown) => setState((s) => ({ ...s, error: errorMessage(error, 'Failed to delete note') })))
  }, [])

  return { ...state, add, edit, remove }
}
