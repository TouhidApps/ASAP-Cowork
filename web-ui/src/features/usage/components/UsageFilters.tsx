import type { DatePreset } from '@/features/usage/dateRange'
import { KNOWN_PROVIDERS, providerLabel } from '@/features/usage/types'

const PRESETS: { id: DatePreset; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: '7d', label: '7 days' },
  { id: '30d', label: '30 days' },
  { id: '90d', label: '90 days' },
  { id: 'all', label: 'All time' },
]

interface UsageFiltersProps {
  preset: DatePreset
  provider: string
  onPresetChange: (preset: DatePreset) => void
  onProviderChange: (provider: string) => void
}

/** Filters live in one row above every chart/stat/table on the page — they all re-render against the same slice, so the numbers never disagree with each other. */
export function UsageFilters({ preset, provider, onPresetChange, onProviderChange }: UsageFiltersProps) {
  return (
    <div className="usage-filters">
      <div className="usage-filter-presets">
        {PRESETS.map((p) => (
          <button
            key={p.id}
            className={`usage-filter-preset${preset === p.id ? ' active' : ''}`}
            onClick={() => onPresetChange(p.id)}
          >
            {p.label}
          </button>
        ))}
      </div>
      <select className="usage-filter-select" value={provider} onChange={(e) => onProviderChange(e.target.value)}>
        <option value="">All providers</option>
        {KNOWN_PROVIDERS.map((id) => (
          <option key={id} value={id}>
            {providerLabel(id)}
          </option>
        ))}
      </select>
    </div>
  )
}
