import { NotesWindow } from '@/features/notes/components/NotesWindow'

export function NotesPage({ active }: { active: boolean }) {
  return <NotesWindow active={active} />
}
