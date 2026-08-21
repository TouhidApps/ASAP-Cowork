/** Mirrors chat-gateway's features/admin/UsageModels.kt. */
export interface UsageEntry {
  id: string
  provider: string
  model: string
  inputTokens: number
  outputTokens: number
  totalTokens: number
  costUsd: number
  createdAt: number
}

export interface ProviderUsageTotal {
  provider: string
  inputTokens: number
  outputTokens: number
  totalTokens: number
  costUsd: number
  requestCount: number
}

export interface DailyProviderUsage {
  date: string
  provider: string
  inputTokens: number
  outputTokens: number
  totalTokens: number
  costUsd: number
  requestCount: number
}

export interface UsageSummary {
  totalInputTokens: number
  totalOutputTokens: number
  totalTokens: number
  totalCostUsd: number
  requestCount: number
  byProvider: ProviderUsageTotal[]
  byDay: DailyProviderUsage[]
  recent: UsageEntry[]
}

export interface UsageFilter {
  from?: number
  to?: number
  provider?: string
}

export const KNOWN_PROVIDERS = ['anthropic', 'openai', 'gemini', 'ollama'] as const

/** Fixed identity order (never reflowed by which providers actually have data) — see features/usage/usage.css for the matching --usage-* color tokens. */
export function providerColorVar(provider: string): string {
  return KNOWN_PROVIDERS.includes(provider as (typeof KNOWN_PROVIDERS)[number])
    ? `var(--usage-${provider})`
    : 'var(--usage-other)'
}

export function providerLabel(provider: string): string {
  switch (provider) {
    case 'openai':
      return 'OpenAI'
    case 'anthropic':
      return 'Anthropic'
    case 'gemini':
      return 'Gemini'
    case 'ollama':
      return 'Ollama'
    default:
      return provider
  }
}
