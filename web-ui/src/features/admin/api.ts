import { apiDelete, apiGet, apiPost, apiPut } from '@/api/client'
import type {
  AllowedHostsResponse,
  Conversation,
  ConversationMessage,
  FirebaseStatus,
  GenerateCiTokenResult,
  ListFirebaseAppsResult,
  OllamaStatus,
  ProvidersState,
  SystemStatus,
  ToolchainComponentId,
  ToolchainStatus,
} from '@/features/admin/types'

export function fetchStatus(): Promise<SystemStatus> {
  return apiGet<SystemStatus>('/api/v1/admin/status')
}

export function fetchConversations(): Promise<Conversation[]> {
  return apiGet<Conversation[]>('/api/v1/admin/conversations')
}

export function fetchConversationMessages(conversationId: string): Promise<ConversationMessage[]> {
  return apiGet<ConversationMessage[]>(`/api/v1/admin/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId: string): Promise<void> {
  return apiDelete<void>(`/api/v1/admin/conversations/${conversationId}`)
}

export function fetchProviders(): Promise<ProvidersState> {
  return apiGet<ProvidersState>('/api/v1/admin/providers')
}

export function switchProvider(provider: string): Promise<ProvidersState> {
  return apiPut<ProvidersState>('/api/v1/admin/providers/current', { provider })
}

export function setProviderCredential(providerId: string, apiKey: string): Promise<ProvidersState> {
  return apiPut<ProvidersState>(`/api/v1/admin/providers/${providerId}/credentials`, { apiKey })
}

export function clearProviderCredential(providerId: string): Promise<ProvidersState> {
  return apiDelete<ProvidersState>(`/api/v1/admin/providers/${providerId}/credentials`)
}

export function fetchToolchainStatus(): Promise<ToolchainStatus> {
  return apiGet<ToolchainStatus>('/api/v1/admin/toolchain')
}

export function setToolchainPaths(
  flutterSdkPath: string,
  androidSdkPath: string,
  javaHomePath: string,
  xcodePath: string,
  xcodeGenPath: string,
): Promise<ToolchainStatus> {
  return apiPut<ToolchainStatus>('/api/v1/admin/toolchain', {
    flutterSdkPath,
    androidSdkPath,
    javaHomePath,
    xcodePath,
    xcodeGenPath,
  })
}

export function installToolchainComponent(component: ToolchainComponentId): Promise<ToolchainStatus> {
  return apiPost<ToolchainStatus>(`/api/v1/admin/toolchain/install/${component}`, {})
}

export function fetchAllowedHosts(): Promise<AllowedHostsResponse> {
  return apiGet<AllowedHostsResponse>('/api/v1/admin/allowed-hosts')
}

export function addAllowedHost(host: string): Promise<AllowedHostsResponse> {
  return apiPost<AllowedHostsResponse>('/api/v1/admin/allowed-hosts', { host })
}

export function removeAllowedHost(host: string): Promise<AllowedHostsResponse> {
  return apiDelete<AllowedHostsResponse>(`/api/v1/admin/allowed-hosts/${encodeURIComponent(host)}`)
}

export function fetchFirebaseStatus(): Promise<FirebaseStatus> {
  return apiGet<FirebaseStatus>('/api/v1/admin/firebase')
}

export function setFirebaseCredentials(
  appId: string,
  ciToken: string,
  testerGroups: string,
  releaseNotes: string,
): Promise<FirebaseStatus> {
  return apiPut<FirebaseStatus>('/api/v1/admin/firebase', { appId, ciToken, testerGroups, releaseNotes })
}

export function clearFirebaseCredentials(): Promise<FirebaseStatus> {
  return apiDelete<FirebaseStatus>('/api/v1/admin/firebase')
}

/** Runs `firebase login:ci` on the backend — opens the user's browser for Google OAuth and blocks until they finish. */
export function generateFirebaseCiToken(): Promise<GenerateCiTokenResult> {
  return apiPost<GenerateCiTokenResult>('/api/v1/admin/firebase/generate-ci-token', {})
}

export function listFirebaseApps(projectId: string, ciToken?: string): Promise<ListFirebaseAppsResult> {
  return apiPost<ListFirebaseAppsResult>('/api/v1/admin/firebase/apps', { projectId, ciToken })
}

export function fetchOllamaStatus(): Promise<OllamaStatus> {
  return apiGet<OllamaStatus>('/api/v1/admin/providers/ollama/status')
}

export function installOllama(): Promise<OllamaStatus> {
  return apiPost<OllamaStatus>('/api/v1/admin/providers/ollama/install', {})
}

export function setOllamaModel(model: string): Promise<OllamaStatus> {
  return apiPut<OllamaStatus>('/api/v1/admin/providers/ollama/model', { model })
}

export function pullOllamaModel(model: string): Promise<OllamaStatus> {
  return apiPost<OllamaStatus>('/api/v1/admin/providers/ollama/pull', { model })
}

export function deleteOllamaModel(model: string): Promise<OllamaStatus> {
  return apiDelete<OllamaStatus>(`/api/v1/admin/providers/ollama/models/${encodeURIComponent(model)}`)
}

export function deleteOllamaModels(models: string[]): Promise<OllamaStatus> {
  return apiPost<OllamaStatus>('/api/v1/admin/providers/ollama/models/delete', { models })
}
