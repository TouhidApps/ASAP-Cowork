export type DatePreset = 'today' | '7d' | '30d' | '90d' | 'all'

/** [from, to) epoch millis for a preset, `to` left undefined ("open end", i.e. now) except where a fixed end matters. */
export function presetRange(preset: DatePreset): { from?: number; to?: number } {
  if (preset === 'all') return {}
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const days = preset === 'today' ? 0 : preset === '7d' ? 6 : preset === '30d' ? 29 : 89
  return { from: startOfToday - days * 24 * 60 * 60 * 1000 }
}
