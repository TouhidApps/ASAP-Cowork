import { useEffect, useState } from 'react'
import {
  clearImageAiCredential,
  fetchImageAiStatus,
  setImageAiCredential,
  switchImageAiProvider,
} from '@/features/imageAi/api'
import type { ImageAiStatus } from '@/features/imageAi/types'

export function ImageAiPage() {
  const [state, setState] = useState<ImageAiStatus | null>(null)
  const [switching, setSwitching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [keyInputs, setKeyInputs] = useState<Record<string, string>>({})
  const [busyProvider, setBusyProvider] = useState<string | null>(null)

  useEffect(() => {
    fetchImageAiStatus()
      .then(setState)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load Image AI settings'))
  }, [])

  const handleSwitch = async (provider: string) => {
    setSwitching(true)
    setError(null)
    try {
      setState(await switchImageAiProvider(provider))
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
      setState(await setImageAiCredential(providerId, trimmed))
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
      setState(await clearImageAiCredential(providerId))
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
      <h2>Image AI</h2>
      <p style={{ fontSize: 13, opacity: 0.7, marginBottom: 16, maxWidth: 460 }}>
        Backs the Imaging Agent's image generation and editing — a separate key from the chat LLM providers above,
        since you may want different billing for images. A key saved here is written straight into the backend's{' '}
        <code>.env</code> file and takes effect immediately, no restart needed.
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
                    name="image-ai-provider"
                    checked={state.current === provider.id}
                    disabled={switching}
                    onChange={() => handleSwitch(provider.id)}
                  />
                  {provider.id}
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
                </label>

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
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
