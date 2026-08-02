import type { StorageStatus } from '@/features/workspace/types'

/** Mirrors chat-gateway's features/admin/AdminModels.kt. */
export interface SystemStatus {
  status: string
  uptimeSeconds: number
  memoryUsedMb: number
  memoryMaxMb: number
  activeProvider: string
  conversationMessageCount: number
  storage: StorageStatus
  databaseSizeBytes: number
}

export interface ProviderInfo {
  id: string
  requiresApiKey: boolean
  hasApiKey: boolean
  /** The key as saved via the admin panel — null if unset, or only set via .env. */
  apiKey?: string | null
}

export interface ProvidersState {
  available: ProviderInfo[]
  current: string
}

/** Mirrors chat-gateway's conversation/ConversationStore.kt#StoredMessage. */
export interface ConversationMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
}

/** Mirrors chat-gateway's conversation/ConversationStore.kt#Conversation. One WebSocket connection = one conversation. */
export interface Conversation {
  id: string
  title: string
  createdAt: number
  updatedAt: number
}

export type ToolchainComponentId = 'flutter' | 'android-sdk' | 'java' | 'xcode' | 'xcodegen'

/** Mirrors chat-gateway's features/admin/ToolchainModels.kt. */
export interface ToolchainPathInfo {
  configuredPath: string | null
  detectedPath: string | null
  available: boolean
  installable: boolean
}

export interface ToolchainStatus {
  flutter: ToolchainPathInfo
  androidSdk: ToolchainPathInfo
  java: ToolchainPathInfo
  xcode: ToolchainPathInfo
  xcodeGen: ToolchainPathInfo
}

/** Mirrors chat-gateway's features/admin/FirebaseModels.kt. */
export interface FirebaseStatus {
  configured: boolean
  appId: string | null
  ciToken: string | null
  testerGroups: string | null
  releaseNotes: string | null
}

export interface GenerateCiTokenResult {
  token: string
}

export interface FirebaseAppInfo {
  appId: string
  displayName: string | null
  platform: string
}

export interface ListFirebaseAppsResult {
  apps: FirebaseAppInfo[]
}

/** Mirrors chat-gateway's features/admin/OllamaModels.kt. */
export interface OllamaModelInfo {
  name: string
  sizeBytes: number
}

export interface SuggestedOllamaModel {
  name: string
  approxSizeGb: number
  minRamGb: number
  recommended: boolean
  fitsSystemMemory: boolean
}

export interface OllamaPullProgress {
  model: string
  status: 'pulling' | 'done' | 'error'
  percent: number | null
  message: string | null
}

/** Progress of running the Ollama install script itself — separate from OllamaPullProgress, which is for a model download and only applies once Ollama is already installed. */
export interface OllamaInstallProgress {
  status: 'installing' | 'done' | 'error'
  message: string | null
}

export interface OllamaStatus {
  installed: boolean
  systemMemoryGb: number
  installedModels: OllamaModelInfo[]
  currentModel: string
  suggestedModels: SuggestedOllamaModel[]
  installInstructions: string[] | null
  canAutoInstall: boolean
  install: OllamaInstallProgress | null
  pull: OllamaPullProgress | null
}

export interface AllowedHostsResponse {
  hosts: string[]
}
