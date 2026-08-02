import { useEffect, useState } from 'react'
import { clearProviderCredential, fetchProviders, setProviderCredential, switchProvider } from '@/features/admin/api'
import { OllamaPanel } from '@/features/admin/components/OllamaPanel'
import type { ProvidersState } from '@/features/admin/types'

export function AdminProvidersPage() {
  const [state, setState] = useState<ProvidersState | null>(null)
  const [switching, setSwitching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [keyInputs, setKeyInputs] = useState<Record<string, string>>({})
  const [busyProvider, setBusyProvider] = useState<string | null>(null)

  useEffect(() => {
    fetchProviders()
      .then(setState)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load providers'))
  }, [])

  const handleSwitch = async (provider: string) => {
    setSwitching(true)
    setError(null)
    try {
      setState(await switchProvider(provider))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to switch provider')
    } finally {
      setSwitching(false)
    }
  }

  const handleSaveKey = async (providerId: string, apiKey: string) => {
    const trimmed = apiKey.trim()
    if (!trimmed) return
    setBusyProvider(providerId)
    setError(null)
    try {
      setState(await setProviderCredential(providerId, trimmed))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save API key')
    } finally {
      setBusyProvider(null)
    }
  }

  const handleRemoveKey = async (providerId: string) => {
    setBusyProvider(providerId)
    setError(null)
    try {
      setState(await clearProviderCredential(providerId))
      // Otherwise a value typed (but not saved) before hitting Remove would
      // keep showing in the box, no longer reflecting the just-cleared state.
      setKeyInputs((s) => ({ ...s, [providerId]: '' }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove API key')
    } finally {
      setBusyProvider(null)
    }
  }

  return (
    <div>
      <h2>LLM Provider</h2>
      <p style={{ fontSize: 13, opacity: 0.7, marginBottom: 16, maxWidth: 460 }}>
        A key saved here is written straight into the backend's <code>.env</code> file and takes effect immediately,
        no restart needed. Ollama runs locally and needs no key — see below to pick or download a model.
      </p>
      {error && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{error}</p>}
      {!state ? (
        <p>Loading…</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 460 }}>
          {state.available.map((provider) => {
            // Falls back to the fetched value until the user actually types
            // something this session — lets a saved key show up pre-filled
            // (so it can be reviewed/copied) without that fallback fighting
            // whatever the user is mid-typing.
            const value = keyInputs[provider.id] ?? provider.apiKey ?? ''
            return (
              <div key={provider.id} style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12 }}>
                <label
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    cursor: switching ? 'default' : 'pointer',
                    color: 'var(--text-h)',
                  }}
                >
                  <input
                    type="radio"
                    name="provider"
                    checked={state.current === provider.id}
                    disabled={switching}
                    onChange={() => handleSwitch(provider.id)}
                  />
                  {provider.id}
                  {provider.requiresApiKey && (
                    <span
                      style={{
                        marginLeft: 'auto',
                        fontSize: 12,
                        opacity: provider.hasApiKey ? 0.7 : 1,
                        color: provider.hasApiKey ? 'var(--text-h)' : '#d94f4f',
                      }}
                    >
                      {provider.hasApiKey ? 'Key configured' : 'No key set'}
                    </span>
                  )}
                </label>

                {provider.requiresApiKey && (
                  <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                    <input
                      type="password"
                      autoComplete="off"
                      placeholder="Enter API key…"
                      value={value}
                      onChange={(e) => setKeyInputs((s) => ({ ...s, [provider.id]: e.target.value }))}
                      disabled={busyProvider === provider.id}
                      style={{
                        flex: 1,
                        minWidth: 0,
                        padding: '8px 10px',
                        borderRadius: 6,
                        border: '1px solid var(--border)',
                        background: 'var(--bg)',
                        color: 'var(--text-h)',
                        font: 'inherit',
                      }}
                    />
                    <button
                      disabled={busyProvider === provider.id || !value.trim()}
                      onClick={() => handleSaveKey(provider.id, value)}
                    >
                      Save
                    </button>
                    {provider.hasApiKey && (
                      <button disabled={busyProvider === provider.id} onClick={() => handleRemoveKey(provider.id)}>
                        Remove
                      </button>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
      {state?.current === 'ollama' && <OllamaPanel />}
    </div>
  )
}
