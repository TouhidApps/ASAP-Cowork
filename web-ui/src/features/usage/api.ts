import { apiGet } from '@/api/client'
import type { UsageFilter, UsageSummary } from '@/features/usage/types'

export function fetchUsageSummary(filter: UsageFilter): Promise<UsageSummary> {
  const params = new URLSearchParams()
  if (filter.from != null) params.set('from', String(filter.from))
  if (filter.to != null) params.set('to', String(filter.to))
  if (filter.provider) params.set('provider', filter.provider)
  const query = params.toString()
  return apiGet<UsageSummary>(`/api/v1/admin/usage${query ? `?${query}` : ''}`)
}
