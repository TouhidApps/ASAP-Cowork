export interface ImageAiProviderInfo {
  id: string
  hasApiKey: boolean
  apiKey?: string | null
}

export interface ImageAiStatus {
  available: ImageAiProviderInfo[]
  current: string | null
}
