import { useEffect, useState } from 'react'
import { CollapsibleSection } from '@/components/CollapsibleSection'
import {
  addAllowedHost,
  clearFirebaseCredentials,
  deleteOllamaModels,
  fetchAllowedHosts,
  fetchFirebaseStatus,
  fetchOllamaStatus,
  fetchToolchainStatus,
  generateFirebaseCiToken,
  installToolchainComponent,
  listFirebaseApps,
  removeAllowedHost,
  setFirebaseCredentials,
  setToolchainPaths,
} from '@/features/admin/api'
import type {
  AllowedHostsResponse,
  FirebaseAppInfo,
  FirebaseStatus,
  OllamaStatus,
  ToolchainComponentId,
  ToolchainPathInfo,
  ToolchainStatus,
} from '@/features/admin/types'
import { DirectoryPicker } from '@/features/workspace/components/DirectoryPicker'
import {
  backupWorkspace,
  cleanupStorage,
  confirmWorkspace,
  fetchBackupItems,
  fetchStorageStatus,
  fetchWorkspaceStatus,
} from '@/features/workspace/api'
import type { BackupItem, BackupResult, StorageStatus, WorkspaceStatus } from '@/features/workspace/types'

type ToolchainFieldId = 'flutter' | 'androidSdk' | 'java' | 'xcode' | 'xcodeGen'

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / 1024 ** exponent
  return `${exponent === 0 ? value : value.toFixed(1)} ${units[exponent]}`
}

export function AdminWorkspacePage() {
  const [status, setStatus] = useState<WorkspaceStatus | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmedMessage, setConfirmedMessage] = useState<string | null>(null)

  const [storage, setStorage] = useState<StorageStatus | null>(null)
  const [storageError, setStorageError] = useState<string | null>(null)
  const [cleaningUp, setCleaningUp] = useState<string | null>(null)

  const [ollamaStatus, setOllamaStatus] = useState<OllamaStatus | null>(null)
  const [ollamaError, setOllamaError] = useState<string | null>(null)
  const [selectedOllamaModels, setSelectedOllamaModels] = useState<Set<string>>(new Set())
  const [deletingOllamaModels, setDeletingOllamaModels] = useState(false)

  const [backing, setBacking] = useState(false)
  const [backupError, setBackupError] = useState<string | null>(null)
  const [backupResult, setBackupResult] = useState<BackupResult | null>(null)
  const [backupItems, setBackupItems] = useState<BackupItem[] | null>(null)
  const [selectedBackupItems, setSelectedBackupItems] = useState<Set<string>>(new Set())

  const [toolchainStatus, setToolchainStatus] = useState<ToolchainStatus | null>(null)
  const [flutterSdkPath, setFlutterSdkPath] = useState('')
  const [androidSdkPath, setAndroidSdkPath] = useState('')
  const [javaHomePath, setJavaHomePath] = useState('')
  const [xcodePath, setXcodePath] = useState('')
  const [xcodeGenPath, setXcodeGenPath] = useState('')
  const [toolchainBusy, setToolchainBusy] = useState(false)
  const [toolchainError, setToolchainError] = useState<string | null>(null)
  const [installingComponent, setInstallingComponent] = useState<ToolchainComponentId | null>(null)
  const [dismissedSuggestions, setDismissedSuggestions] = useState<Set<ToolchainComponentId>>(new Set())

  const [allowedHosts, setAllowedHosts] = useState<AllowedHostsResponse | null>(null)
  const [allowedHostsError, setAllowedHostsError] = useState<string | null>(null)
  const [newAllowedHost, setNewAllowedHost] = useState('')
  const [allowedHostsBusy, setAllowedHostsBusy] = useState(false)

  const [firebaseStatus, setFirebaseStatus] = useState<FirebaseStatus | null>(null)
  const [firebaseAppId, setFirebaseAppId] = useState('')
  const [firebaseCiToken, setFirebaseCiToken] = useState('')
  const [firebaseTesterGroups, setFirebaseTesterGroups] = useState('')
  const [firebaseReleaseNotes, setFirebaseReleaseNotes] = useState('')
  const [firebaseBusy, setFirebaseBusy] = useState(false)
  const [firebaseError, setFirebaseError] = useState<string | null>(null)
  const [generatingCiToken, setGeneratingCiToken] = useState(false)
  const [generatingAppId, setGeneratingAppId] = useState(false)
  const [firebaseAppOptions, setFirebaseAppOptions] = useState<FirebaseAppInfo[] | null>(null)

  // Pre-fills all four fields from whatever's currently saved, so reopening
  // this section (or saving again) shows the existing values instead of
  // blank inputs — this admin panel is already fully gated by ADMIN_TOKEN,
  // the same trust boundary as everything else under /admin.
  const applyFirebaseStatus = (result: FirebaseStatus) => {
    setFirebaseStatus(result)
    setFirebaseAppId(result.appId ?? '')
    setFirebaseCiToken(result.ciToken ?? '')
    setFirebaseTesterGroups(result.testerGroups ?? '')
    setFirebaseReleaseNotes(result.releaseNotes ?? '')
  }

  // Pre-fills each input from whatever's explicitly *saved* (configuredPath)
  // — never from detectedPath, which is just a suggestion the user hasn't
  // accepted yet and shouldn't silently end up in the input as if it were.
  const applyToolchainStatus = (result: ToolchainStatus) => {
    setToolchainStatus(result)
    setFlutterSdkPath(result.flutter.configuredPath ?? '')
    setAndroidSdkPath(result.androidSdk.configuredPath ?? '')
    setJavaHomePath(result.java.configuredPath ?? '')
    setXcodePath(result.xcode.configuredPath ?? '')
    setXcodeGenPath(result.xcodeGen.configuredPath ?? '')
  }

  const loadStorage = () => {
    fetchStorageStatus()
      .then(setStorage)
      .catch((e: unknown) => setStorageError(e instanceof Error ? e.message : 'Failed to load storage usage'))
  }

  const loadOllama = () => {
    fetchOllamaStatus()
      .then((result) => {
        setOllamaStatus(result)
        // Drop selections for models that no longer exist (e.g. deleted elsewhere).
        setSelectedOllamaModels((prev) => {
          const stillInstalled = new Set(result.installedModels.map((m) => m.name))
          return new Set(Array.from(prev).filter((name) => stillInstalled.has(name)))
        })
      })
      .catch((e: unknown) => setOllamaError(e instanceof Error ? e.message : 'Failed to load Ollama models'))
  }

  useEffect(() => {
    fetchWorkspaceStatus()
      .then(setStatus)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load workspace status'))
    loadStorage()
    loadOllama()
    fetchBackupItems()
      .then(({ items }) => {
        setBackupItems(items)
        // Default to everything selected — the common case is "back up all of it".
        setSelectedBackupItems(new Set(items.map((item) => item.name)))
      })
      .catch((e: unknown) => setBackupError(e instanceof Error ? e.message : 'Failed to load backup options'))
    fetchToolchainStatus()
      .then(applyToolchainStatus)
      .catch((e: unknown) => setToolchainError(e instanceof Error ? e.message : 'Failed to load toolchain status'))
    fetchAllowedHosts()
      .then(setAllowedHosts)
      .catch((e: unknown) => setAllowedHostsError(e instanceof Error ? e.message : 'Failed to load allowed hosts'))
    fetchFirebaseStatus()
      .then(applyFirebaseStatus)
      .catch((e: unknown) => setFirebaseError(e instanceof Error ? e.message : 'Failed to load Firebase status'))
  }, [])

  const toggleBackupItem = (name: string) => {
    setSelectedBackupItems((prev) => {
      const next = new Set(prev)
      if (next.has(name)) {
        next.delete(name)
      } else {
        next.add(name)
      }
      return next
    })
  }

  const toggleOllamaModel = (name: string) => {
    setSelectedOllamaModels((prev) => {
      const next = new Set(prev)
      if (next.has(name)) {
        next.delete(name)
      } else {
        next.add(name)
      }
      return next
    })
  }

  const handleDeleteOllamaModels = async () => {
    const models = Array.from(selectedOllamaModels)
    if (models.length === 0) return
    if (!window.confirm(`Delete ${models.length} model${models.length === 1 ? '' : 's'}? This can't be undone.`)) return

    setDeletingOllamaModels(true)
    setOllamaError(null)
    try {
      const result = await deleteOllamaModels(models)
      setOllamaStatus(result)
      setSelectedOllamaModels(new Set())
    } catch (e) {
      setOllamaError(e instanceof Error ? e.message : 'Failed to delete models')
    } finally {
      setDeletingOllamaModels(false)
    }
  }

  const handleConfirmWorkspace = async (path: string) => {
    setConfirming(true)
    setError(null)
    try {
      const result = await confirmWorkspace(path)
      setStatus(result)
      setConfirmedMessage(`Workspace set to ${result.root}.`)
      loadStorage()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to set workspace directory')
    } finally {
      setConfirming(false)
    }
  }

  const handleCleanup = async (target: string, label: string) => {
    if (!window.confirm(`Delete all ${label.toLowerCase()}? This can't be undone.`)) return
    setCleaningUp(target)
    setStorageError(null)
    try {
      setStorage(await cleanupStorage(target))
    } catch (e) {
      setStorageError(e instanceof Error ? e.message : 'Failed to clean up storage')
    } finally {
      setCleaningUp(null)
    }
  }

  const handleBackup = async (destination: string) => {
    setBacking(true)
    setBackupError(null)
    setBackupResult(null)
    try {
      setBackupResult(await backupWorkspace(destination, Array.from(selectedBackupItems)))
    } catch (e) {
      setBackupError(e instanceof Error ? e.message : 'Failed to create backup')
    } finally {
      setBacking(false)
    }
  }

  const handleSaveToolchain = async () => {
    setToolchainBusy(true)
    setToolchainError(null)
    try {
      applyToolchainStatus(
        await setToolchainPaths(
          flutterSdkPath.trim(),
          androidSdkPath.trim(),
          javaHomePath.trim(),
          xcodePath.trim(),
          xcodeGenPath.trim(),
        ),
      )
    } catch (e) {
      setToolchainError(e instanceof Error ? e.message : 'Failed to save toolchain paths')
    } finally {
      setToolchainBusy(false)
    }
  }

  // "Use this path?" for something ToolchainDetector found but that isn't
  // explicitly saved yet — saves immediately with the other fields left as
  // whatever's currently in their inputs.
  const handleUseDetectedPath = async (field: ToolchainFieldId, path: string) => {
    setToolchainBusy(true)
    setToolchainError(null)
    try {
      applyToolchainStatus(
        await setToolchainPaths(
          field === 'flutter' ? path : flutterSdkPath.trim(),
          field === 'androidSdk' ? path : androidSdkPath.trim(),
          field === 'java' ? path : javaHomePath.trim(),
          field === 'xcode' ? path : xcodePath.trim(),
          field === 'xcodeGen' ? path : xcodeGenPath.trim(),
        ),
      )
    } catch (e) {
      setToolchainError(e instanceof Error ? e.message : 'Failed to save toolchain path')
    } finally {
      setToolchainBusy(false)
    }
  }

  const handleDismissSuggestion = (component: ToolchainComponentId) => {
    setDismissedSuggestions((prev) => new Set(prev).add(component))
  }

  const handleInstallToolchain = async (component: ToolchainComponentId) => {
    setInstallingComponent(component)
    setToolchainError(null)
    try {
      applyToolchainStatus(await installToolchainComponent(component))
    } catch (e) {
      setToolchainError(e instanceof Error ? e.message : `Failed to install ${component}`)
    } finally {
      setInstallingComponent(null)
    }
  }

  const handleAddAllowedHost = async () => {
    const host = newAllowedHost.trim()
    if (!host) return
    setAllowedHostsBusy(true)
    setAllowedHostsError(null)
    try {
      setAllowedHosts(await addAllowedHost(host))
      setNewAllowedHost('')
    } catch (e) {
      setAllowedHostsError(e instanceof Error ? e.message : 'Failed to add host')
    } finally {
      setAllowedHostsBusy(false)
    }
  }

  const handleRemoveAllowedHost = async (host: string) => {
    setAllowedHostsBusy(true)
    setAllowedHostsError(null)
    try {
      setAllowedHosts(await removeAllowedHost(host))
    } catch (e) {
      setAllowedHostsError(e instanceof Error ? e.message : 'Failed to remove host')
    } finally {
      setAllowedHostsBusy(false)
    }
  }

  const handleSaveFirebase = async () => {
    if (!firebaseAppId.trim() || !firebaseCiToken.trim()) return
    setFirebaseBusy(true)
    setFirebaseError(null)
    try {
      applyFirebaseStatus(
        await setFirebaseCredentials(
          firebaseAppId.trim(),
          firebaseCiToken.trim(),
          firebaseTesterGroups.trim(),
          firebaseReleaseNotes.trim(),
        ),
      )
    } catch (e) {
      setFirebaseError(e instanceof Error ? e.message : 'Failed to save Firebase credentials')
    } finally {
      setFirebaseBusy(false)
    }
  }

  const handleClearFirebase = async () => {
    if (!window.confirm('Remove the saved Firebase credentials?')) return
    setFirebaseBusy(true)
    setFirebaseError(null)
    try {
      applyFirebaseStatus(await clearFirebaseCredentials())
    } catch (e) {
      setFirebaseError(e instanceof Error ? e.message : 'Failed to clear Firebase credentials')
    } finally {
      setFirebaseBusy(false)
    }
  }

  const handleGenerateCiToken = async () => {
    setGeneratingCiToken(true)
    setFirebaseError(null)
    try {
      const { token } = await generateFirebaseCiToken()
      setFirebaseCiToken(token)
    } catch (e) {
      setFirebaseError(e instanceof Error ? e.message : 'Failed to generate a CI token')
    } finally {
      setGeneratingCiToken(false)
    }
  }

  const handleGenerateAppId = async () => {
    const projectId = window.prompt('Firebase project ID (e.g. my-app-12345):')
    if (!projectId?.trim()) return
    setGeneratingAppId(true)
    setFirebaseError(null)
    setFirebaseAppOptions(null)
    try {
      const { apps } = await listFirebaseApps(projectId.trim(), firebaseCiToken.trim() || undefined)
      if (apps.length === 0) {
        setFirebaseError(`No Android apps found in Firebase project "${projectId.trim()}".`)
      } else if (apps.length === 1) {
        setFirebaseAppId(apps[0].appId)
      } else {
        setFirebaseAppOptions(apps)
      }
    } catch (e) {
      setFirebaseError(e instanceof Error ? e.message : 'Failed to list Firebase apps')
    } finally {
      setGeneratingAppId(false)
    }
  }

  const totalCleanupBytes = storage?.categories.reduce((sum, c) => sum + c.totalBytes, 0) ?? 0

  return (
    <div>
      <h2>Settings</h2>

      <div style={{ maxWidth: 640 }}>
        <CollapsibleSection title="Workspace">
          <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12, marginBottom: 16 }}>
            <div style={{ fontSize: 14, color: 'var(--text-h)', marginBottom: 6 }}>Current workspace</div>
            {!status ? (
              <p>Loading…</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
                <div>
                  Root: <code>{status.root}</code>
                </div>
                {!status.configured && (
                  <div style={{ color: '#d94f4f', marginTop: 4 }}>
                    Not yet explicitly confirmed — using the default <code>workspace/</code> fallback.
                  </div>
                )}
                <div style={{ opacity: 0.7, marginTop: 4 }}>Agents read, write, and build only inside this directory.</div>
              </div>
            )}
          </div>

          <div style={{ fontSize: 14, color: 'var(--text-h)', marginBottom: 6 }}>Change workspace directory</div>
          {error && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{error}</p>}
          {confirmedMessage && <p style={{ color: '#3fb950', marginBottom: 12 }}>✓ {confirmedMessage}</p>}
          <DirectoryPicker
            chooseLabel="Use this directory"
            busy={confirming}
            renderConfirmMessage={(path) => <>Set "{path}" as the workspace? Agents will read/write/build here.</>}
            onChoose={handleConfirmWorkspace}
          />
        </CollapsibleSection>

        <CollapsibleSection title="Toolchain (SDK paths)">
          <p style={{ opacity: 0.7, fontSize: 13, marginTop: 0 }}>
            Root directories for the Flutter SDK, Android SDK, a JDK, Xcode, and XcodeGen — only needed if the
            agent's build/emulator/simulator tools can't already find them on this server's own
            PATH/ANDROID_HOME/JAVA_HOME/DEVELOPER_DIR. Detected automatically below; leave a field blank to fall back
            to PATH.
          </p>
          {toolchainError && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{toolchainError}</p>}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <ToolchainField
              label="Flutter SDK path"
              placeholder="e.g. /Users/you/flutter (leave blank to use PATH)"
              value={flutterSdkPath}
              onChange={setFlutterSdkPath}
              info={toolchainStatus?.flutter ?? null}
              component="flutter"
              field="flutter"
              busy={toolchainBusy}
              installing={installingComponent === 'flutter'}
              dismissed={dismissedSuggestions.has('flutter')}
              onUseDetected={handleUseDetectedPath}
              onDismiss={handleDismissSuggestion}
              onInstall={handleInstallToolchain}
            />
            <ToolchainField
              label="Android SDK path"
              placeholder="e.g. /Users/you/Library/Android/sdk (leave blank to use ANDROID_HOME/PATH)"
              value={androidSdkPath}
              onChange={setAndroidSdkPath}
              info={toolchainStatus?.androidSdk ?? null}
              component="android-sdk"
              field="androidSdk"
              busy={toolchainBusy}
              installing={installingComponent === 'android-sdk'}
              dismissed={dismissedSuggestions.has('android-sdk')}
              onUseDetected={handleUseDetectedPath}
              onDismiss={handleDismissSuggestion}
              onInstall={handleInstallToolchain}
            />
            <ToolchainField
              label="Java home (JDK) path"
              placeholder="e.g. /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home (leave blank to use JAVA_HOME/PATH)"
              value={javaHomePath}
              onChange={setJavaHomePath}
              info={toolchainStatus?.java ?? null}
              component="java"
              field="java"
              busy={toolchainBusy}
              installing={installingComponent === 'java'}
              dismissed={dismissedSuggestions.has('java')}
              onUseDetected={handleUseDetectedPath}
              onDismiss={handleDismissSuggestion}
              onInstall={handleInstallToolchain}
            />
            <ToolchainField
              label="Xcode path"
              placeholder="e.g. /Applications/Xcode.app (leave blank to use xcode-select's default)"
              value={xcodePath}
              onChange={setXcodePath}
              info={toolchainStatus?.xcode ?? null}
              component="xcode"
              field="xcode"
              busy={toolchainBusy}
              installing={installingComponent === 'xcode'}
              dismissed={dismissedSuggestions.has('xcode')}
              onUseDetected={handleUseDetectedPath}
              onDismiss={handleDismissSuggestion}
              onInstall={handleInstallToolchain}
              notInstallableHint="Not installable automatically — install Xcode from the App Store or developer.apple.com, then paste its path here (the Command Line Tools alone can't build/run against the Simulator)."
            />
            <ToolchainField
              label="XcodeGen path"
              placeholder="e.g. /opt/homebrew/bin (leave blank to use PATH)"
              value={xcodeGenPath}
              onChange={setXcodeGenPath}
              info={toolchainStatus?.xcodeGen ?? null}
              component="xcodegen"
              field="xcodeGen"
              busy={toolchainBusy}
              installing={installingComponent === 'xcodegen'}
              dismissed={dismissedSuggestions.has('xcodegen')}
              onUseDetected={handleUseDetectedPath}
              onDismiss={handleDismissSuggestion}
              onInstall={handleInstallToolchain}
            />

            <div>
              <button disabled={toolchainBusy || !toolchainStatus} onClick={handleSaveToolchain}>
                {toolchainBusy ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Dev server allowed hosts">
          <p style={{ opacity: 0.7, fontSize: 13, marginTop: 0 }}>
            Vite's dev server (<code>npm run dev</code>) rejects requests whose Host header isn't on this list, and
            chat-gateway's own CORS check separately rejects requests whose Origin isn't allowed — add a hostname
            here (a Tailscale device, an ngrok tunnel, ...) to cover both instead of hand-editing config files. The
            Vite side takes effect on its very next request; the chat-gateway/CORS side needs a chat-gateway restart
            to pick it up. Without both, requests (including the chat WebSocket) fail closed, which just looks like
            the composer never finishes connecting.
          </p>
          <p style={{ opacity: 0.7, fontSize: 13, marginTop: 0 }}>
            Add <strong>two</strong> entries per device if you use both access paths: <code>host:8080</code> for
            direct LAN/Tailscale access, and bare <code>host</code> (no port) for Tailscale Funnel/HTTPS access —
            Funnel strips the port before the request ever reaches this server, so an entry with <code>:8080</code>{' '}
            won't match it.
          </p>
          {allowedHostsError && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{allowedHostsError}</p>}

          {!allowedHosts ? (
            <p>Loading…</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}>
              {allowedHosts.hosts.length === 0 ? (
                <p style={{ opacity: 0.7, color: 'var(--text-h)', margin: 0 }}>
                  No extra hosts allowed yet — only localhost/127.0.0.1/the LAN IP work.
                </p>
              ) : (
                allowedHosts.hosts.map((host) => (
                  <div
                    key={host}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      border: '1px solid var(--border)',
                      borderRadius: 8,
                      padding: '8px 12px',
                    }}
                  >
                    <code style={{ fontSize: 13 }}>{host}</code>
                    <button disabled={allowedHostsBusy} onClick={() => handleRemoveAllowedHost(host)}>
                      Remove
                    </button>
                  </div>
                ))
              )}
            </div>
          )}

          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="text"
              autoComplete="off"
              placeholder="e.g. my-device.tailnet-name.ts.net"
              value={newAllowedHost}
              onChange={(e) => setNewAllowedHost(e.target.value)}
              disabled={allowedHostsBusy}
              onKeyDown={(e) => e.key === 'Enter' && handleAddAllowedHost()}
              style={{ ...textInputStyle, flex: 1, minWidth: 0 }}
            />
            <button disabled={allowedHostsBusy || !newAllowedHost.trim()} onClick={handleAddAllowedHost}>
              Add
            </button>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Firebase">
          <p style={{ opacity: 0.7, fontSize: 13, marginTop: 0 }}>
            Used by the Publishing Agent's distribute_apk tool ("upload/share the app" in chat) to upload built APKs
            to Firebase App Distribution. Get a CI token by running <code>firebase login:ci</code> on this machine
            (needs <code>firebase-tools</code> installed), and find the App ID in the Firebase console under Project
            settings → General → Your apps.
          </p>
          {firebaseError && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{firebaseError}</p>}

          <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12, marginBottom: 16 }}>
            <div style={{ fontSize: 14, color: 'var(--text-h)', marginBottom: 6 }}>Status</div>
            {!firebaseStatus ? (
              <p>Loading…</p>
            ) : firebaseStatus.configured ? (
              <p style={{ color: '#3fb950', margin: 0 }}>✓ Configured</p>
            ) : (
              <p style={{ color: '#d94f4f', margin: 0 }}>Not configured yet.</p>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                type="text"
                autoComplete="off"
                placeholder="Firebase App ID, e.g. 1:1234567890:android:abc123"
                value={firebaseAppId}
                onChange={(e) => setFirebaseAppId(e.target.value)}
                disabled={firebaseBusy}
                style={{ ...textInputStyle, flex: 1, minWidth: 0 }}
              />
              <button type="button" disabled={firebaseBusy || generatingAppId} onClick={handleGenerateAppId}>
                {generatingAppId ? 'Listing…' : 'Generate'}
              </button>
            </div>
            {firebaseAppOptions && (
              <div
                style={{
                  border: '1px solid var(--border)',
                  borderRadius: 6,
                  padding: 8,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                }}
              >
                <div style={{ fontSize: 12, opacity: 0.7 }}>Choose an app:</div>
                {firebaseAppOptions.map((app) => (
                  <button
                    key={app.appId}
                    type="button"
                    onClick={() => {
                      setFirebaseAppId(app.appId)
                      setFirebaseAppOptions(null)
                    }}
                    style={{
                      textAlign: 'left',
                      padding: '6px 8px',
                      borderRadius: 6,
                      border: 'none',
                      background: 'transparent',
                      color: 'var(--text)',
                      cursor: 'pointer',
                      fontSize: 13,
                    }}
                  >
                    {app.displayName ?? app.appId} — <code>{app.appId}</code>
                  </button>
                ))}
              </div>
            )}
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                type="password"
                autoComplete="off"
                placeholder="CI token (from `firebase login:ci`)"
                value={firebaseCiToken}
                onChange={(e) => setFirebaseCiToken(e.target.value)}
                disabled={firebaseBusy}
                style={{ ...textInputStyle, flex: 1, minWidth: 0 }}
              />
              <button type="button" disabled={firebaseBusy || generatingCiToken} onClick={handleGenerateCiToken}>
                {generatingCiToken ? 'Waiting…' : 'Generate'}
              </button>
            </div>
            {generatingCiToken && (
              <p style={{ fontSize: 12, opacity: 0.7, margin: 0 }}>
                A browser window should open for Google sign-in — complete it there, this will fill in automatically.
              </p>
            )}
            <input
              type="text"
              autoComplete="off"
              placeholder="Default tester group name, e.g. qa-team (optional)"
              value={firebaseTesterGroups}
              onChange={(e) => setFirebaseTesterGroups(e.target.value)}
              disabled={firebaseBusy}
              style={textInputStyle}
            />
            <textarea
              autoComplete="off"
              placeholder="Default release notes shown to testers (optional)"
              value={firebaseReleaseNotes}
              onChange={(e) => setFirebaseReleaseNotes(e.target.value)}
              disabled={firebaseBusy}
              rows={3}
              style={{ ...textInputStyle, resize: 'vertical' }}
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                disabled={firebaseBusy || !firebaseAppId.trim() || !firebaseCiToken.trim()}
                onClick={handleSaveFirebase}
              >
                Save
              </button>
              {firebaseStatus?.configured && (
                <button disabled={firebaseBusy} onClick={handleClearFirebase}>
                  Clear
                </button>
              )}
            </div>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Storage cleanup">
          <p style={{ opacity: 0.7, fontSize: 13, marginTop: 0 }}>
            Screenshots the Android agent captures (capture_device_screenshot) accumulate here — nothing deletes them
            automatically. Videos and built APKs will show up here once those tools exist.
          </p>
          {storageError && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{storageError}</p>}
          {!storage ? (
            <p>Loading…</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {storage.categories.map((category) => (
                <div
                  key={category.name}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    border: '1px solid var(--border)',
                    borderRadius: 8,
                    padding: '10px 14px',
                  }}
                >
                  <div style={{ fontSize: 13 }}>
                    <strong style={{ color: 'var(--text-h)' }}>{category.label}</strong>
                    <span style={{ opacity: 0.7 }}>
                      {' '}
                      — {category.fileCount} file{category.fileCount === 1 ? '' : 's'}, {formatBytes(category.totalBytes)}
                    </span>
                  </div>
                  <button
                    disabled={category.fileCount === 0 || cleaningUp !== null}
                    onClick={() => handleCleanup(category.name, category.label)}
                  >
                    {cleaningUp === category.name ? 'Cleaning…' : 'Clean up'}
                  </button>
                </div>
              ))}
              <div>
                <button
                  disabled={totalCleanupBytes === 0 || cleaningUp !== null}
                  onClick={() => handleCleanup('all', 'screenshots and videos')}
                >
                  {cleaningUp === 'all' ? 'Cleaning…' : 'Clean up everything'}
                </button>
              </div>
            </div>
          )}

          <div style={{ marginTop: 20 }}>
            <div style={{ fontSize: 13, opacity: 0.7, marginBottom: 6 }}>
              Ollama models — these can be several GB each
            </div>
            {ollamaError && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{ollamaError}</p>}
            {!ollamaStatus ? (
              <p>Loading…</p>
            ) : !ollamaStatus.installed ? (
              <p style={{ opacity: 0.7, color: 'var(--text-h)' }}>Ollama isn't reachable right now.</p>
            ) : ollamaStatus.installedModels.length === 0 ? (
              <p style={{ opacity: 0.7, color: 'var(--text-h)' }}>No models pulled yet.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {ollamaStatus.installedModels.map((m) => (
                    <label
                      key={m.name}
                      style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}
                    >
                      <input
                        type="checkbox"
                        checked={selectedOllamaModels.has(m.name)}
                        disabled={deletingOllamaModels}
                        onChange={() => toggleOllamaModel(m.name)}
                      />
                      {m.name}
                      <span style={{ opacity: 0.6, fontSize: 12 }}>({(m.sizeBytes / 1e9).toFixed(1)} GB)</span>
                    </label>
                  ))}
                </div>
                <div>
                  <button
                    disabled={selectedOllamaModels.size === 0 || deletingOllamaModels}
                    onClick={handleDeleteOllamaModels}
                  >
                    {deletingOllamaModels
                      ? 'Deleting…'
                      : `Delete selected${selectedOllamaModels.size > 0 ? ` (${selectedOllamaModels.size})` : ''}`}
                  </button>
                </div>
              </div>
            )}
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Backup">
          <p style={{ opacity: 0.7, fontSize: 13, marginTop: 0 }}>
            Zips whatever you select below into a timestamped .zip file in a folder you choose. The destination
            can't be inside the workspace itself.
          </p>
          {backupError && <p style={{ color: '#d94f4f', marginBottom: 12 }}>{backupError}</p>}
          {backupResult && (
            <div style={{ color: '#3fb950', marginBottom: 12, fontSize: 13 }}>
              <p style={{ margin: 0 }}>
                ✓ Backed up {backupResult.fileCount} files ({formatBytes(backupResult.totalBytes)}) to:
              </p>
              <p style={{ margin: 0 }}>
                <code>{backupResult.zipPath}</code>
              </p>
            </div>
          )}

          <div style={{ fontSize: 14, color: 'var(--text-h)', marginBottom: 6 }}>What to back up</div>
          {!backupItems ? (
            <p>Loading…</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 16 }}>
              {backupItems.map((item) => (
                <label key={item.name} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={selectedBackupItems.has(item.name)}
                    onChange={() => toggleBackupItem(item.name)}
                  />
                  {item.label}
                </label>
              ))}
            </div>
          )}

          <DirectoryPicker
            chooseLabel="Back up here"
            busy={backing}
            disabled={selectedBackupItems.size === 0}
            renderConfirmMessage={(path) => (
              <>
                Create a backup zip in <code>{path}</code>?
              </>
            )}
            onChoose={handleBackup}
          />
        </CollapsibleSection>
      </div>
    </div>
  )
}

const textInputStyle = {
  width: '100%',
  padding: '8px 10px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--bg)',
  color: 'var(--text-h)',
  font: 'inherit',
  boxSizing: 'border-box' as const,
}

/**
 * One SDK path field: a manual text input (always editable, for a custom
 * path) plus, underneath, whatever ToolchainDetector found — a "use this
 * path?" prompt for something detected but not yet saved, an Install
 * button when nothing usable was found at all, or just a quiet
 * confirmation once a path is explicitly configured.
 */
function ToolchainField({
  label,
  placeholder,
  value,
  onChange,
  info,
  component,
  field,
  busy,
  installing,
  dismissed,
  onUseDetected,
  onDismiss,
  onInstall,
  notInstallableHint,
}: {
  label: string
  placeholder: string
  value: string
  onChange: (value: string) => void
  info: ToolchainPathInfo | null
  component: ToolchainComponentId
  field: ToolchainFieldId
  busy: boolean
  installing: boolean
  dismissed: boolean
  onUseDetected: (field: ToolchainFieldId, path: string) => void
  onDismiss: (component: ToolchainComponentId) => void
  onInstall: (component: ToolchainComponentId) => void
  /** Overrides the generic "needs macOS + Homebrew" copy for a component (Xcode) that can never be auto-installed at all, regardless of platform. */
  notInstallableHint?: string
}) {
  const suggestionPath = info && !info.configuredPath && info.detectedPath ? info.detectedPath : null

  return (
    <div>
      <label style={{ fontSize: 13 }}>
        <div style={{ marginBottom: 4, color: 'var(--text-h)' }}>{label}</div>
        <input
          type="text"
          autoComplete="off"
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={busy}
          style={{
            width: '100%',
            padding: '8px 10px',
            borderRadius: 6,
            border: '1px solid var(--border)',
            background: 'var(--bg)',
            color: 'var(--text-h)',
            font: 'inherit',
            boxSizing: 'border-box',
          }}
        />
      </label>

      {info && info.configuredPath && (
        <p style={{ margin: '4px 0 0', fontSize: 12, color: info.available ? '#3fb950' : '#d94f4f' }}>
          {info.available ? '✓ Configured' : '✗ Configured, but not found at this path anymore'}
        </p>
      )}

      {info && suggestionPath && !dismissed && (
        <div
          style={{
            marginTop: 6,
            padding: '8px 10px',
            borderRadius: 6,
            border: '1px solid var(--border)',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
            fontSize: 12,
          }}
        >
          <span style={{ opacity: 0.85 }}>
            Detected at <code>{suggestionPath}</code> — use this path?
          </span>
          <div style={{ display: 'flex', gap: 6, marginLeft: 'auto' }}>
            <button disabled={busy} onClick={() => onUseDetected(field, suggestionPath)}>
              Use this path
            </button>
            <button disabled={busy} onClick={() => onDismiss(component)}>
              Not now
            </button>
          </div>
        </div>
      )}

      {info && !info.configuredPath && !info.available && (
        <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', fontSize: 12 }}>
          <span style={{ color: '#d94f4f' }}>Not found.</span>
          {info.installable ? (
            <button disabled={installing} onClick={() => onInstall(component)}>
              {installing ? 'Installing…' : 'Install'}
            </button>
          ) : (
            <span style={{ opacity: 0.7 }}>
              {notInstallableHint ?? 'Automatic install needs macOS + Homebrew — install it manually.'}
            </span>
          )}
        </div>
      )}
    </div>
  )
}
