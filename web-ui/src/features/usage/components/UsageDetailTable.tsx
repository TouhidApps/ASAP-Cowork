import { useEffect, useState } from 'react'
import type { UsageEntry } from '@/features/usage/types'
import { providerColorVar, providerLabel } from '@/features/usage/types'

const PAGE_SIZE = 25

/** Raw per-call rows — the table view every value in the charts above is also reachable through, per the dataviz skill's "tooltips enhance, never gate" rule. */
export function UsageDetailTable({ entries }: { entries: UsageEntry[] }) {
  const [page, setPage] = useState(0)

  // The filters (date range/provider) that produced `entries` changed — start back on page 1
  // rather than stranding the user on a page index that may no longer exist.
  useEffect(() => {
    setPage(0)
  }, [entries])

  if (entries.length === 0) {
    return <p className="usage-chart-empty">No API calls in this range yet.</p>
  }

  const pageCount = Math.ceil(entries.length / PAGE_SIZE)
  const currentPage = Math.min(page, pageCount - 1)
  const pageEntries = entries.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE)

  return (
    <div>
      <div className="usage-table-wrap">
        <table className="usage-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Provider</th>
              <th>Model</th>
              <th>Input</th>
              <th>Output</th>
              <th>Total</th>
              <th>Cost</th>
            </tr>
          </thead>
          <tbody>
            {pageEntries.map((entry) => (
              <tr key={entry.id}>
                <td>{new Date(entry.createdAt).toLocaleString()}</td>
                <td>
                  <span className="usage-table-provider">
                    <span
                      className="usage-legend-dot"
                      style={{ background: providerColorVar(entry.provider) }}
                      aria-hidden="true"
                    />
                    {providerLabel(entry.provider)}
                  </span>
                </td>
                <td>{entry.model}</td>
                <td>{entry.inputTokens.toLocaleString()}</td>
                <td>{entry.outputTokens.toLocaleString()}</td>
                <td>{entry.totalTokens.toLocaleString()}</td>
                <td>${entry.costUsd.toFixed(4)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {pageCount > 1 && (
        <div className="usage-table-pagination">
          <button
            type="button"
            className="usage-table-page-btn"
            disabled={currentPage === 0}
            onClick={() => setPage(currentPage - 1)}
          >
            Prev
          </button>
          <span className="usage-table-page-info">
            Page {currentPage + 1} of {pageCount}
          </span>
          <button
            type="button"
            className="usage-table-page-btn"
            disabled={currentPage === pageCount - 1}
            onClick={() => setPage(currentPage + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
