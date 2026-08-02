/** Mirrors the Capability ids the four Phase 1/2 agents register (agent-sdk's Capability.kt). */
export type ChatStage = 'requirements' | 'architecture' | 'tech_stack' | 'scaffolding'

export interface StageOption {
  id: ChatStage
  label: string
}

/** Display labels for the badge shown on assistant messages once the orchestrator picks an agent. */
export const STAGES: StageOption[] = [
  { id: 'requirements', label: 'Requirements' },
  { id: 'architecture', label: 'Architecture' },
  { id: 'tech_stack', label: 'Tech Stack' },
  { id: 'scaffolding', label: 'Scaffolding' },
]

/** Mirrors context-store's Conversations.kt#StoredAttachment. */
export interface Attachment {
  id: string
  url: string
  mimeType: string
}

/** A tool call starting/progressing/finishing mid-reply — mirrors AgentEvent.ToolActivity. Client-only, never persisted. */
export interface ToolActivity {
  tool: string
  label: string
  status: 'started' | 'finished' | 'failed'
}

/** A screenshot/video (or attached image) opened full-size in MediaLightbox, tracked by MessageList. */
export interface OpenMedia {
  kind: 'image' | 'video'
  src: string
  alt?: string
}

/** Client-side chat message shown in MessageList/MessageBubble. */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
  /** Which agent produced this — unknown until the server's `agent_activated` event arrives. */
  stage?: ChatStage
  /** True while this message's text is still arriving over the WebSocket. */
  streaming?: boolean
  /** Files a code-gen agent wrote to disk, from `file_changed` events. */
  files?: { path: string; summary: string }[]
  /** Images the user attached to this message, uploaded via /api/v1/chat/uploads before sending. */
  attachments?: Attachment[]
  /** Tool calls seen so far while this message streams (e.g. "Running Gradle: assembleDebug"), from `tool_activity` events. */
  toolActivity?: ToolActivity[]
}

/** Wire shape chat-gateway streams over `/ws/chat` — mirrors ChatEvent (WireEvents.kt). */
export interface ChatEvent {
  type:
    | 'agent_activated'
    | 'progress'
    | 'text_delta'
    | 'file_changed'
    | 'tool_activity'
    | 'result'
    | 'error'
    | 'conversation_started'
  message?: string
  text?: string
  path?: string
  agentId?: string
  capability?: string
  conversationId?: string
  tool?: string
  status?: 'started' | 'finished' | 'failed'
}

/** Mirrors context-store's Conversation.kt#Conversation. */
export interface Conversation {
  id: string
  title: string
  createdAt: number
  updatedAt: number
}
