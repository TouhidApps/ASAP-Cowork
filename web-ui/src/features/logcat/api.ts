import { apiGet } from '@/api/client'

/** Mirrors chat-gateway's LogcatRoutes.kt#LogcatInfo. */
export interface LogcatInfo {
  deviceName: string
  packageName: string | null
}

export function fetchLogcatInfo(): Promise<LogcatInfo> {
  return apiGet<LogcatInfo>('/api/v1/logcat/info')
}
