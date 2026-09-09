import { apiDelete, apiGet, apiPut } from '@/api/client'
import type { ImageAiStatus } from '@/features/imageAi/types'

// Mounted under the admin panel's Tools section (ADMIN_TOKEN-gated) — see
// AdminRoutes.kt's `/tools` block and ImageAiRoutes.kt's imageAiToolsRoutes().
const BASE = '/api/v1/admin/tools/image-ai'

export function fetchImageAiStatus(): Promise<ImageAiStatus> {
  return apiGet<ImageAiStatus>(BASE)
}

export function switchImageAiProvider(provider: string): Promise<ImageAiStatus> {
  return apiPut<ImageAiStatus>(`${BASE}/current`, { provider })
}

export function setImageAiCredential(providerId: string, apiKey: string): Promise<ImageAiStatus> {
  return apiPut<ImageAiStatus>(`${BASE}/${providerId}/credentials`, { apiKey })
}

export function clearImageAiCredential(providerId: string): Promise<ImageAiStatus> {
  return apiDelete<ImageAiStatus>(`${BASE}/${providerId}/credentials`)
}
