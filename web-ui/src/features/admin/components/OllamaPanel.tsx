import { useEffect, useState } from 'react'
import { deleteOllamaModel, fetchOllamaStatus, installOllama, pullOllamaModel, setOllamaModel } from '@/features/admin/api'
import type { OllamaStatus } from '@/features/admin/types'

function formatGb(gb: number): string {
  return `${gb.toFixed(1)} GB`
}

/** Highlighted chip for an in-progress action (install/download) — an accent-tinted banner rather than plain body text, with an optional bar when a numeric percent is known. */
function ProgressBanner({ label, percent }: { label: string; percent?: number | null }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        padding: '10px 12px',
        borderRadius: 8,
        background: 'var(--accent-bg)',
        border: '1px solid var(--accent-border)',
      }}
    >
      <p style={{ margin: 0, color: 'var(--accent)', fontWeight: 500, fontSize: 13 }}>{label}</p>
      {percent != null && (
        <div style={{ height: 6, borderRadius: 999, background: 'var(--border)', overflow: 'hidden' }}>
          <div
            style={{
              height: '100%',
              width: `${percent}%`,
              background: 'var(--accent)',
              borderRadius: 999,
              transition: 'width 300ms ease',
            }}
          />
        </div>
      )}
    </div>
  )
}

export function OllamaPanel() {
  const [status, setStatus] = useState<OllamaStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyModel, setBusyModel] = useState<string | null>(null)
  const [installing, setInstalling] = useState(false)

  const load = () =>
    fetchOllamaStatus()
      .then((s) => {
        setStatus(s)
        setError(null)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load Ollama status'))

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    if (status?.pull?.status !== 'pulling') return
    const id = setInterval(load, 1500)
    return () => clearInterval(id)
  }, [status?.pull?.status])

  // Keeps polling through 'done' too (unlike the pull effect above) — once
  // the install script finishes, Ollama's own server may take a moment to
  // come up, and re-checking is what flips `status.installed` and swaps
  // this whole section over to the installed view.
  useEffect(() => {
    if (status?.install == null || status.installed) return
    const id = setInterval(load, 1500)
    return () => clearInterval(id)
  }, [status?.install, status?.installed])

  const handleInstall = async () => {
    setInstalling(true)
    setError(null)
    try {
      setStatus(await installOllama())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start install')
    } finally {
      setInstalling(false)
    }
  }

  const handleSelect = async (name: string) => {
    setBusyModel(name)
    try {
      setStatus(await setOllamaModel(name))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to switch model')
    } finally {
      setBusyModel(null)
    }
  }

  const handlePull = async (name: string) => {
    setBusyModel(name)
    try {
      setStatus(await pullOllamaModel(name))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start download')
    } finally {
      setBusyModel(null)
    }
  }

  const handleDelete = async (name: string) => {
    if (!window.confirm(`Delete "${name}"? This removes it from disk and can't be undone.`)) return
    setBusyModel(name)
    try {
      setStatus(await deleteOllamaModel(name))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete model')
    } finally {
      setBusyModel(null)
    }
  }

  if (!status && !error) {
    return <p>Checking Ollama…</p>
  }

  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 16, marginTop: 16 }}>
      <h3 style={{ marginTop: 0 }}>Local AI model (Ollama)</h3>
      {error && <p style={{ color: '#d94f4f' }}>{error}</p>}

      {status && !status.installed && (
        <div>
          <p style={{ color: 'var(--text-h)' }}>
            {status.canAutoInstall
              ? "Ollama isn't reachable. Install it below, or run these in a terminal yourself:"
              : "Ollama isn't reachable. Run these in a terminal to get set up, then re-check:"}
          </p>
          <pre
            style={{
              background: 'var(--code-bg)',
              color: 'var(--text-h)',
              padding: 12,
              borderRadius: 6,
              overflowX: 'auto',
              fontSize: 13,
              whiteSpace: 'pre-wrap',
            }}
          >
            {status.installInstructions?.join('\n')}
          </pre>

          {status.install?.status === 'installing' && (
            <div style={{ marginBottom: 12 }}>
              <ProgressBanner label={`Installing… ${status.install.message ?? ''}`} />
            </div>
          )}
          {status.install?.status === 'done' && (
            <p style={{ margin: '0 0 12px', color: 'var(--text-h)' }}>{status.install.message}</p>
          )}
          {status.install?.status === 'error' && (
            <p style={{ margin: '0 0 12px', color: '#d94f4f' }}>Install failed: {status.install.message}</p>
          )}

          <div style={{ display: 'flex', gap: 8 }}>
            {status.canAutoInstall && (
              <button onClick={handleInstall} disabled={installing || status.install?.status === 'installing'}>
                {status.install?.status === 'installing' ? 'Installing…' : 'Install Ollama'}
              </button>
            )}
            <button onClick={load}>Re-check</button>
          </div>
        </div>
      )}

      {status && status.installed && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <p style={{ margin: 0, opacity: 0.8, color: 'var(--text-h)' }}>
            System memory: {formatGb(status.systemMemoryGb)} · Active model:{' '}
            <strong>{status.currentModel}</strong>
          </p>

          {status.pull?.status === 'pulling' && (
            <ProgressBanner
              label={`Downloading ${status.pull.model}${status.pull.percent != null ? ` — ${status.pull.percent}%` : ''}${status.pull.message ? ` (${status.pull.message})` : ''}`}
              percent={status.pull.percent}
            />
          )}
          {status.pull?.status === 'error' && (
            <p style={{ margin: 0, color: '#d94f4f' }}>
              Download of {status.pull.model} failed: {status.pull.message}
            </p>
          )}

          <div>
            <div style={{ fontSize: 13, opacity: 0.7, marginBottom: 6 }}>Installed models</div>
            {status.installedModels.length === 0 ? (
              <p style={{ opacity: 0.7, color: 'var(--text-h)' }}>No models pulled yet — download one below.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 420 }}>
                {status.installedModels.map((m) => (
                  <div
                    key={m.name}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      border: '1px solid var(--border)',
                      borderRadius: 8,
                      padding: 12,
                    }}
                  >
                    <label
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        flex: 1,
                        minWidth: 0,
                        cursor: busyModel ? 'default' : 'pointer',
                        color: 'var(--text-h)',
                      }}
                    >
                      <input
                        type="radio"
                        name="ollama-model"
                        checked={status.currentModel === m.name}
                        disabled={busyModel !== null}
                        onChange={() => handleSelect(m.name)}
                      />
                      {m.name}
                      <span style={{ opacity: 0.6, fontSize: 12 }}>({(m.sizeBytes / 1e9).toFixed(1)} GB)</span>
                    </label>
                    <button
                      onClick={() => handleDelete(m.name)}
                      disabled={busyModel !== null}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        color: '#d94f4f',
                        cursor: 'pointer',
                        fontSize: 12,
                        flexShrink: 0,
                      }}
                    >
                      {busyModel === m.name ? '…' : 'Delete'}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div>
            <div style={{ fontSize: 13, opacity: 0.7, marginBottom: 6 }}>Suggested models (sized to your machine)</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 460 }}>
              {status.suggestedModels.map((m) => {
                const alreadyInstalled = status.installedModels.some((im) => im.name === m.name)
                return (
                  <div
                    key={m.name}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 10,
                      border: '1px solid var(--border)',
                      borderRadius: 8,
                      padding: 12,
                      color: 'var(--text-h)',
                      opacity: m.fitsSystemMemory ? 1 : 0.6,
                    }}
                  >
                    <span>
                      {m.name}
                      {m.recommended ? ' ⭐' : ''}
                      <span style={{ opacity: 0.6, fontSize: 12, marginLeft: 8 }}>
                        ~{formatGb(m.approxSizeGb)} download, needs {formatGb(m.minRamGb)}+ RAM
                        {!m.fitsSystemMemory ? ' — may be too much for this machine' : ''}
                      </span>
                    </span>
                    {alreadyInstalled ? (
                      <span style={{ fontSize: 12, opacity: 0.7 }}>Installed</span>
                    ) : (
                      <button disabled={busyModel !== null} onClick={() => handlePull(m.name)}>
                        {busyModel === m.name ? 'Starting…' : 'Download'}
                      </button>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
