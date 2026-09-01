import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useEmailSettings } from '@/features/email/useEmailSettings'
import type { EmailNotificationMode } from '@/features/email/types'
import '@/features/email/email.css'

export function EmailSettingsForm({ active }: { active: boolean }) {
  const { accounts, settings, oauth, loading, saving, connecting, error, notice, save, setDefault, disconnect, saveOAuthCredentials, connectGmail, reload, setNotice, setError } =
    useEmailSettings(active)

  const [searchParams, setSearchParams] = useSearchParams()
  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')

  // Reflects the currently-saved credentials into the form once loaded —
  // only when the fields are still untouched, so it doesn't clobber
  // whatever the user is mid-typing on a slow save.
  useEffect(() => {
    if (oauth && clientId === '' && clientSecret === '') {
      setClientId(oauth.clientId ?? '')
      setClientSecret(oauth.clientSecret ?? '')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [oauth])

  // Picks up the redirect from /api/v1/oauth/gmail/callback (connected=<email> or oauthError=<message>).
  useEffect(() => {
    const connected = searchParams.get('connected')
    const oauthError = searchParams.get('oauthError')
    if (connected) {
      setNotice(`Connected ${connected}.`)
      reload()
    } else if (oauthError) {
      setError(oauthError)
    }
    if (connected || oauthError) {
      searchParams.delete('connected')
      searchParams.delete('oauthError')
      setSearchParams(searchParams, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading || !settings) {
    return <p className="chat-empty-state">Loading email settings…</p>
  }

  const toggleAccount = (id: string, enabled: boolean) => {
    const enabledAccountIds = enabled
      ? [...settings.enabledAccountIds, id]
      : settings.enabledAccountIds.filter((accountId) => accountId !== id)
    save({ ...settings, enabledAccountIds })
  }

  return (
    <div className="email-settings">
      <h2 className="email-settings-title">Email</h2>
      <p className="email-settings-hint">
        Reads and sends Gmail via Google's own API — connect an account with "Sign in with Google" below. Never
        deletes, trashes, or archives anything.
      </p>

      <section className="email-settings-section">
        <h3>Connection</h3>
        <p className="email-settings-hint">Needs a Google Cloud OAuth client (Client ID/Secret) with the Gmail API enabled:</p>
        <ol className="email-settings-steps">
          <li>
            <a href="https://console.cloud.google.com/apis/library/gmail.googleapis.com" target="_blank" rel="noopener noreferrer">
              Enable the Gmail API
            </a>{' '}
            for your Google Cloud project.
          </li>
          <li>
            On the{' '}
            <a href="https://console.cloud.google.com/apis/credentials/consent" target="_blank" rel="noopener noreferrer">
              OAuth consent screen
            </a>{' '}
            page, keep <strong>Publishing status</strong> as <strong>Testing</strong> (don't click Publish) and add your own
            Gmail address under <strong>Test users</strong>. This is a personal, local-only connection — Testing mode
            skips the Application Homepage / Privacy Policy / domain-verification requirements entirely, which only
            apply once an app is published or submitted for Google's review.
          </li>
          <li>
            <a href="https://console.cloud.google.com/apis/credentials/oauthclient" target="_blank" rel="noopener noreferrer">
              Create an OAuth client ID
            </a>{' '}
            (type "Web application"), and add this exact Authorized redirect URI:
            <code className="email-settings-code">{oauth?.redirectUri}</code>
          </li>
          <li>Paste the resulting Client ID and Client Secret below, then click Connect Gmail Account.</li>
          <li>
            Google will show a <strong>"Google hasn't verified this app"</strong> warning — that's expected for an
            app only you use. Click <strong>Advanced → Go to (your app name) (unsafe)</strong> to continue.
          </li>
        </ol>
        <p className="email-settings-hint">
          Note: while the consent screen stays in Testing mode, Google expires the connection 7 days after each
          connect, regardless of use — reconnect from here if a check starts failing.
        </p>

        <label className="email-settings-field-column">
          Client ID
          <input
            type="text"
            className="email-settings-text"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="xxxxxxxxxx.apps.googleusercontent.com"
          />
        </label>
        <label className="email-settings-field-column">
          Client Secret
          <input
            type="password"
            className="email-settings-text"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
          />
        </label>

        <div className="email-settings-actions">
          <button
            type="button"
            className="note-action-button"
            disabled={saving || !clientId.trim() || !clientSecret.trim()}
            onClick={() => saveOAuthCredentials(clientId.trim(), clientSecret.trim())}
          >
            Save
          </button>
          <button type="button" className="note-action-button" disabled={!oauth?.configured || connecting} onClick={connectGmail}>
            {connecting ? 'Redirecting…' : 'Connect Gmail Account'}
          </button>
        </div>
      </section>

      <section className="email-settings-section">
        <h3>Accounts</h3>
        {accounts.length === 0 && <p className="chat-empty-state">No Gmail account connected yet.</p>}
        {accounts.map((account) => (
          <div key={account.id} className="email-account-row">
            <label className="email-account-checkbox">
              <input
                type="checkbox"
                checked={settings.enabledAccountIds.includes(account.id)}
                onChange={(e) => toggleAccount(account.id, e.target.checked)}
              />
              {account.emailAddress}
            </label>
            <div className="email-settings-actions">
              {account.isDefault ? (
                <span className="email-account-default-badge">Default</span>
              ) : (
                <button type="button" className="note-link-button" onClick={() => setDefault(account.id)}>
                  Set as default
                </button>
              )}
              <button type="button" className="note-link-button note-link-button--danger" onClick={() => disconnect(account.id)}>
                Disconnect
              </button>
            </div>
          </div>
        ))}
      </section>

      <section className="email-settings-section">
        <h3>Notifications</h3>
        <label className="email-settings-radio">
          <input
            type="radio"
            name="email-notification-mode"
            checked={settings.mode === 'ALL'}
            onChange={() => save({ ...settings, mode: 'ALL' as EmailNotificationMode })}
          />
          Notify on every new email
        </label>
        <label className="email-settings-radio">
          <input
            type="radio"
            name="email-notification-mode"
            checked={settings.mode === 'IMPORTANT_ONLY'}
            onChange={() => save({ ...settings, mode: 'IMPORTANT_ONLY' as EmailNotificationMode })}
          />
          Notify only on important email
        </label>

        <label className="email-settings-field">
          Check every
          <input
            type="number"
            min={1}
            className="email-settings-number"
            value={settings.pollIntervalMinutes}
            onChange={(e) => save({ ...settings, pollIntervalMinutes: Math.max(1, Number(e.target.value) || 1) })}
          />
          minute(s)
        </label>

        <label className="email-settings-checkbox">
          <input
            type="checkbox"
            checked={settings.inAppEnabled}
            onChange={(e) => save({ ...settings, inAppEnabled: e.target.checked })}
          />
          In-app notification
        </label>
        <label className="email-settings-checkbox">
          <input
            type="checkbox"
            checked={settings.osEnabled}
            onChange={(e) => save({ ...settings, osEnabled: e.target.checked })}
          />
          Desktop (OS) notification
        </label>
      </section>

      {saving && <p className="email-settings-hint">Saving…</p>}
      {notice && <p className="email-settings-notice">{notice}</p>}
      {error && <p className="chat-error">{error}</p>}
    </div>
  )
}
