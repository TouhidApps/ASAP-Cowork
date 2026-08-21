import type { ProviderUsageTotal } from '@/features/usage/types'
import { providerColorVar, providerLabel } from '@/features/usage/types'

function formatTokens(n: number): string {
  return n >= 1_000_000 ? `${(n / 1_000_000).toFixed(1)}M` : n >= 1_000 ? `${(n / 1_000).toFixed(1)}K` : String(n)
}

/**
 * Ranked magnitude by provider — a horizontal bar per provider, each already carrying its own
 * name + color swatch as a direct label, so no separate legend box is needed here (unlike the
 * trend chart, where color is the only thing tying a line back to a provider).
 */
export function UsageProviderBreakdown({ totals }: { totals: ProviderUsageTotal[] }) {
  if (totals.length === 0) {
    return <p className="usage-chart-empty">No API calls in this range yet.</p>
  }

  const max = Math.max(...totals.map((t) => t.totalTokens), 1)

  return (
    <div>
      {totals.map((total) => (
        <div key={total.provider} className="usage-bar-row">
          <span className="usage-bar-name">
            <span
              className="usage-legend-dot"
              style={{ background: providerColorVar(total.provider) }}
              aria-hidden="true"
            />
            {providerLabel(total.provider)}
          </span>
          <div className="usage-bar-track">
            <div
              className="usage-bar-fill"
              style={{
                width: `${(total.totalTokens / max) * 100}%`,
                background: providerColorVar(total.provider),
              }}
            />
          </div>
          <span className="usage-bar-value">
            {formatTokens(total.totalTokens)} tok · ${total.costUsd.toFixed(2)} · {total.requestCount} req
          </span>
        </div>
      ))}
    </div>
  )
}
