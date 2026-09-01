import { useCallback, useEffect, useState } from 'react'
import {
  disconnectEmailAccount,
  fetchEmailAccounts,
  fetchEmailSettings,
  fetchGmailAuthorizeUrl,
  fetchGmailOAuthStatus,
  setDefaultEmailAccount,
  updateEmailSettings,
  updateGmailOAuthCredentials,
} from '@/features/email/api'
import type { EmailAccount, EmailNotificationSettings, GmailOAuthStatus } from '@/features/email/types'

interface EmailSettingsState {
  accounts: EmailAccount[]
  settings: EmailNotificationSettings | null
  oauth: GmailOAuthStatus | null
  loading: boolean
  saving: boolean
  connecting: boolean
  error?: string
  notice?: string
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

/**
 * Backs the admin panel's Email tools page. `accounts` only ever grows via
 * the OAuth connect flow ([connectGmail] navigates the browser to Google's
 * consent screen; the account is added server-side once
 * `/api/v1/oauth/gmail/callback` completes and redirects back here) —
 * there's no direct "add account" call from this hook.
 */
export function useEmailSettings(active: boolean) {
  const [state, setState] = useState<EmailSettingsState>({
    accounts: [],
    settings: null,
    oauth: null,
    loading: true,
    saving: false,
    connecting: false,
  })

  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true }))
    Promise.all([fetchEmailAccounts(), fetchEmailSettings(), fetchGmailOAuthStatus()])
      .then(([accounts, settings, oauth]) => setState((s) => ({ ...s, accounts, settings, oauth, loading: false })))
      .catch((error: unknown) => setState((s) => ({ ...s, loading: false, error: errorMessage(error, 'Failed to load email settings') })))
  }, [])

  useEffect(() => {
    if (active) reload()
  }, [active, reload])

  const save = useCallback((next: EmailNotificationSettings) => {
    setState((s) => ({ ...s, saving: true, error: undefined }))
    updateEmailSettings(next)
      .then((settings) => setState((s) => ({ ...s, settings, saving: false })))
      .catch((error: unknown) => setState((s) => ({ ...s, saving: false, error: errorMessage(error, 'Failed to save settings') })))
  }, [])

  const setDefault = useCallback((id: string) => {
    setDefaultEmailAccount(id)
      .then(() => fetchEmailAccounts())
      .then((accounts) => setState((s) => ({ ...s, accounts, error: undefined })))
      .catch((error: unknown) => setState((s) => ({ ...s, error: errorMessage(error, 'Failed to set default account') })))
  }, [])

  const disconnect = useCallback((id: string) => {
    disconnectEmailAccount(id)
      .then(() => fetchEmailAccounts())
      .then((accounts) => setState((s) => ({ ...s, accounts, error: undefined })))
      .catch((error: unknown) => setState((s) => ({ ...s, error: errorMessage(error, 'Failed to disconnect account') })))
  }, [])

  const saveOAuthCredentials = useCallback((clientId: string, clientSecret: string) => {
    setState((s) => ({ ...s, saving: true, error: undefined }))
    updateGmailOAuthCredentials(clientId, clientSecret)
      .then((oauth) => setState((s) => ({ ...s, oauth, saving: false })))
      .catch((error: unknown) => setState((s) => ({ ...s, saving: false, error: errorMessage(error, 'Failed to save OAuth credentials') })))
  }, [])

  const connectGmail = useCallback(() => {
    setState((s) => ({ ...s, connecting: true, error: undefined }))
    fetchGmailAuthorizeUrl()
      .then(({ url }) => {
        window.location.href = url
      })
      .catch((error: unknown) => setState((s) => ({ ...s, connecting: false, error: errorMessage(error, 'Failed to start Google sign-in') })))
  }, [])

  const setNotice = useCallback((notice: string | undefined) => setState((s) => ({ ...s, notice })), [])
  const setError = useCallback((error: string | undefined) => setState((s) => ({ ...s, error })), [])

  return { ...state, save, setDefault, disconnect, saveOAuthCredentials, connectGmail, reload, setNotice, setError }
}
