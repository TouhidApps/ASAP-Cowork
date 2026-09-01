/** Mirrors context-store's EmailAccounts.kt#EmailAccount. */
export interface EmailAccount {
  id: string
  provider: string
  emailAddress: string
  displayLabel: string | null
  isDefault: boolean
  lastSeenMessageId: string | null
  lastSeenAt: number | null
  createdAt: number
  updatedAt: number
}

export type EmailNotificationMode = 'ALL' | 'IMPORTANT_ONLY'

/** Mirrors context-store's EmailSettings.kt#EmailNotificationSettings. */
export interface EmailNotificationSettings {
  mode: EmailNotificationMode
  pollIntervalMinutes: number
  inAppEnabled: boolean
  osEnabled: boolean
  defaultAccountId: string | null
  enabledAccountIds: string[]
}

/** Mirrors chat-gateway's EmailModels.kt#GmailOAuthStatus. */
export interface GmailOAuthStatus {
  configured: boolean
  clientId: string | null
  clientSecret: string | null
  redirectUri: string
}

/** Mirrors chat-gateway's EmailModels.kt#GmailOAuthAuthorizeUrl. */
export interface GmailOAuthAuthorizeUrl {
  url: string
}
