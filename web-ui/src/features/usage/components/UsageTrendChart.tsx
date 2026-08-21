import { useMemo, useRef, useState } from 'react'
import type { DailyProviderUsage } from '@/features/usage/types'
import { KNOWN_PROVIDERS, providerColorVar, providerLabel } from '@/features/usage/types'

const VIEW_W = 640
const VIEW_H = 260
const MARGIN = { top: 12, right: 12, bottom: 28, left: 44 }
const PLOT_W = VIEW_W - MARGIN.left - MARGIN.right
const PLOT_H = VIEW_H - MARGIN.top - MARGIN.bottom

/** Rounds up to a "clean" axis step (1/2/5 × 10^k) so y-axis ticks read as round numbers. */
function niceStep(roughStep: number): number {
  if (roughStep <= 0) return 1
  const magnitude = 10 ** Math.floor(Math.log10(roughStep))
  const normalized = roughStep / magnitude
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return step * magnitude
}

function formatTicks(n: number): string {
  if (n >= 1_000_000) return `${n / 1_000_000}M`
  if (n >= 1_000) return `${n / 1_000}K`
  return String(n)
}

interface Point {
  date: string
  values: Record<string, number>
}

/**
 * Daily token trend by provider — a multi-line chart, since "trend over time, broken out by
 * category" is a line chart's job (see dataviz skill's choosing-a-form). Color is the only
 * identity channel across the lines, so unlike the provider breakdown bars, a legend is
 * mandatory here.
 */
export function UsageTrendChart({ byDay }: { byDay: DailyProviderUsage[] }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [hover, setHover] = useState<{ index: number; x: number; containerWidth: number } | null>(null)

  const providers = useMemo(() => {
    const present = new Set(byDay.map((d) => d.provider))
    const known = KNOWN_PROVIDERS.filter((p) => present.has(p))
    const other = [...present].filter((p) => !KNOWN_PROVIDERS.includes(p as (typeof KNOWN_PROVIDERS)[number]))
    return [...known, ...other]
  }, [byDay])

  const points = useMemo<Point[]>(() => {
    const byDate = new Map<string, Record<string, number>>()
    for (const row of byDay) {
      const values = byDate.get(row.date) ?? {}
      values[row.provider] = row.totalTokens
      byDate.set(row.date, values)
    }
    return [...byDate.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, values]) => ({ date, values }))
  }, [byDay])

  if (points.length === 0) {
    return <p className="usage-chart-empty">No API calls in this range yet.</p>
  }

  const yMax = Math.max(...points.flatMap((p) => providers.map((prov) => p.values[prov] ?? 0)), 1)
  const step = niceStep(yMax / 4)
  const axisMax = step * Math.ceil(yMax / step)
  const yTicks = Array.from({ length: Math.round(axisMax / step) + 1 }, (_, i) => i * step)

  const xScale = (i: number) => (points.length === 1 ? MARGIN.left + PLOT_W / 2 : MARGIN.left + (i / (points.length - 1)) * PLOT_W)
  const yScale = (v: number) => MARGIN.top + PLOT_H - (v / axisMax) * PLOT_H

  // Skips labels so at most ~7 render, however many days are in range.
  const labelStride = Math.max(1, Math.ceil(points.length / 7))

  function handleMouseMove(e: React.MouseEvent) {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect) return
    const relX = e.clientX - rect.left
    const svgX = (relX / rect.width) * VIEW_W
    let nearest = 0
    let nearestDist = Infinity
    points.forEach((_, i) => {
      const dist = Math.abs(xScale(i) - svgX)
      if (dist < nearestDist) {
        nearestDist = dist
        nearest = i
      }
    })
    setHover({ index: nearest, x: relX, containerWidth: rect.width })
  }

  const hoverPoint = hover ? points[hover.index] : null
  const tooltipLeft = hover ? Math.min(hover.x + 12, hover.containerWidth - 168) : 0

  return (
    <div>
      {providers.length > 1 && (
        <div className="usage-legend">
          {providers.map((p) => (
            <span key={p} className="usage-legend-item">
              <span className="usage-legend-dot" style={{ background: providerColorVar(p) }} aria-hidden="true" />
              {providerLabel(p)}
            </span>
          ))}
        </div>
      )}

      <div ref={containerRef} style={{ position: 'relative' }}>
        <svg
          viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
          width="100%"
          height={VIEW_H}
          role="img"
          aria-label="Daily token usage by provider"
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHover(null)}
        >
          {yTicks.map((tick) => (
            <g key={tick}>
              <line
                x1={MARGIN.left}
                x2={VIEW_W - MARGIN.right}
                y1={yScale(tick)}
                y2={yScale(tick)}
                stroke="var(--usage-grid)"
                strokeWidth={1}
              />
              <text x={MARGIN.left - 8} y={yScale(tick) + 3} textAnchor="end" fontSize={11} fill="var(--text)" opacity={0.7}>
                {formatTicks(tick)}
              </text>
            </g>
          ))}

          {points.map((p, i) =>
            i % labelStride === 0 || i === points.length - 1 ? (
              <text
                key={p.date}
                x={xScale(i)}
                y={VIEW_H - MARGIN.bottom + 18}
                textAnchor="middle"
                fontSize={11}
                fill="var(--text)"
                opacity={0.7}
              >
                {p.date.slice(5)}
              </text>
            ) : null,
          )}

          {providers.map((provider) => {
            const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${xScale(i)},${yScale(p.values[provider] ?? 0)}`).join(' ')
            const last = points[points.length - 1]
            return (
              <g key={provider}>
                <path d={d} fill="none" stroke={providerColorVar(provider)} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                <circle
                  cx={xScale(points.length - 1)}
                  cy={yScale(last.values[provider] ?? 0)}
                  r={4}
                  fill={providerColorVar(provider)}
                  stroke="var(--bg)"
                  strokeWidth={2}
                />
              </g>
            )
          })}

          {hover && (
            <line
              x1={xScale(hover.index)}
              x2={xScale(hover.index)}
              y1={MARGIN.top}
              y2={MARGIN.top + PLOT_H}
              stroke="var(--usage-axis)"
              strokeWidth={1}
            />
          )}
          {hover &&
            providers.map((provider) => (
              <circle
                key={provider}
                cx={xScale(hover.index)}
                cy={yScale(hoverPoint?.values[provider] ?? 0)}
                r={4}
                fill={providerColorVar(provider)}
                stroke="var(--bg)"
                strokeWidth={2}
              />
            ))}
        </svg>

        {hover && hoverPoint && (
          <div className="usage-tooltip" style={{ left: tooltipLeft, top: 8 }}>
            <div className="usage-tooltip-date">{hoverPoint.date}</div>
            {providers.map((provider) => (
              <div key={provider} className="usage-tooltip-row">
                <span className="usage-tooltip-key">
                  <span className="usage-tooltip-swatch" style={{ background: providerColorVar(provider) }} aria-hidden="true" />
                  {providerLabel(provider)}
                </span>
                <span className="usage-tooltip-value">{(hoverPoint.values[provider] ?? 0).toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
