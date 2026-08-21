import { useEffect, useState } from 'react'
import { fetchUsageSummary } from '@/features/usage/api'
import { UsageDetailTable } from '@/features/usage/components/UsageDetailTable'
import { UsageFilters } from '@/features/usage/components/UsageFilters'
import { UsageProviderBreakdown } from '@/features/usage/components/UsageProviderBreakdown'
import { UsageStatTiles } from '@/features/usage/components/UsageStatTiles'
import { UsageTrendChart } from '@/features/usage/components/UsageTrendChart'
import type { DatePreset } from '@/features/usage/dateRange'
import { presetRange } from '@/features/usage/dateRange'
import '@/features/usage/usage.css'
import type { UsageSummary } from '@/features/usage/types'

export function AdminUsagePage() {
  const [preset, setPreset] = useState<DatePreset>('30d')
  const [provider, setProvider] = useState('')
  const [summary, setSummary] = useState<UsageSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const { from, to } = presetRange(preset)
    // Keeps whatever's currently rendered on screen while the new slice loads, rather than
    // flashing back to a loading state — filters re-scope the same page, not navigate to a new one.
    fetchUsageSummary({ from, to, provider: provider || undefined })
      .then((data) => {
        if (!cancelled) {
          setSummary(data)
          setError(null)
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load usage data')
      })
    return () => {
      cancelled = true
    }
  }, [preset, provider])

  return (
    <div className="usage-page">
      <h2>API Usage</h2>

      <UsageFilters preset={preset} provider={provider} onPresetChange={setPreset} onProviderChange={setProvider} />

      {error && <p style={{ color: '#d94f4f' }}>{error}</p>}

      {!summary ? (
        <p>Loading…</p>
      ) : (
        <>
          <UsageStatTiles summary={summary} />

          <div className="usage-card">
            <h3 className="usage-card-title">Daily tokens by provider</h3>
            <UsageTrendChart byDay={summary.byDay} />
          </div>

          <div className="usage-card">
            <h3 className="usage-card-title">By provider</h3>
            <UsageProviderBreakdown totals={summary.byProvider} />
          </div>

          <div className="usage-card">
            <h3 className="usage-card-title">Recent calls</h3>
            <UsageDetailTable entries={summary.recent} />
          </div>
        </>
      )}
    </div>
  )
}
