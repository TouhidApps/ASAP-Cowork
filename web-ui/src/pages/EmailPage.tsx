import { EmailSettingsForm } from '@/features/email/components/EmailSettingsForm'

/** Lives under the admin panel's Tools nav section (see AdminLayout.tsx) — a fresh mount per navigation, so `active` is always true here, unlike ChatPage/NotesPage which stay mounted side by side. */
export function EmailPage() {
  return <EmailSettingsForm active />
}
