import type { UsageSummary } from '@/features/usage/types'

function formatCompact(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(n >= 10_000 ? 0 : 1)}K`
  return String(n)
}

function formatCost(n: number): string {
  return n >= 1 ? `$${n.toFixed(2)}` : `$${n.toFixed(4)}`
}

export function UsageStatTiles({ summary }: { summary: UsageSummary }) {
  const tiles = [
    { label: 'Total tokens', value: formatCompact(summary.totalTokens) },
    { label: 'Input tokens', value: formatCompact(summary.totalInputTokens) },
    { label: 'Output tokens', value: formatCompact(summary.totalOutputTokens) },
    { label: 'Estimated cost', value: formatCost(summary.totalCostUsd) },
    { label: 'Requests', value: formatCompact(summary.requestCount) },
  ]

  return (
    <div className="usage-stat-grid">
      {tiles.map((tile) => (
        <div key={tile.label} className="usage-stat-tile">
          <span className="usage-stat-label">{tile.label}</span>
          <span className="usage-stat-value">{tile.value}</span>
        </div>
      ))}
    </div>
  )
}
