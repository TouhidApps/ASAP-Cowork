import { apiDelete, apiGet, apiPost, apiPut } from '@/api/client'
import type { EmailAccount, EmailNotificationSettings, GmailOAuthAuthorizeUrl, GmailOAuthStatus } from '@/features/email/types'

// Mounted under the admin panel's Tools section (ADMIN_TOKEN-gated) — see
// AdminRoutes.kt's `/tools` block and EmailRoutes.kt's emailToolsRoutes().
const BASE = '/api/v1/admin/tools/email'

export function fetchEmailAccounts(): Promise<EmailAccount[]> {
  return apiGet<EmailAccount[]>(`${BASE}/accounts`)
}

export function setDefaultEmailAccount(id: string): Promise<void> {
  return apiPost<void>(`${BASE}/accounts/${id}/default`, {})
}

export function disconnectEmailAccount(id: string): Promise<void> {
  return apiDelete<void>(`${BASE}/accounts/${id}`)
}

export function fetchEmailSettings(): Promise<EmailNotificationSettings> {
  return apiGet<EmailNotificationSettings>(`${BASE}/settings`)
}

export function updateEmailSettings(settings: EmailNotificationSettings): Promise<EmailNotificationSettings> {
  return apiPut<EmailNotificationSettings>(`${BASE}/settings`, settings)
}

export function fetchGmailOAuthStatus(): Promise<GmailOAuthStatus> {
  return apiGet<GmailOAuthStatus>(`${BASE}/oauth/config`)
}

export function updateGmailOAuthCredentials(clientId: string, clientSecret: string): Promise<GmailOAuthStatus> {
  return apiPut<GmailOAuthStatus>(`${BASE}/oauth/config`, { clientId, clientSecret })
}

export function fetchGmailAuthorizeUrl(): Promise<GmailOAuthAuthorizeUrl> {
  return apiGet<GmailOAuthAuthorizeUrl>(`${BASE}/oauth/authorize-url`)
}
