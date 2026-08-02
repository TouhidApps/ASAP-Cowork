import { apiGet } from '@/api/client'

export interface HealthStatus {
  status: string
  service: string
  timestamp: number
}

export function fetchHealth(): Promise<HealthStatus> {
  return apiGet<HealthStatus>('/health')
}
