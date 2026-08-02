import { apiGet, apiPostForm } from '@/api/client'
import type { Attachment, ChatMessage, Conversation } from '@/features/chat/types'

export function fetchConversations(): Promise<Conversation[]> {
  return apiGet<Conversation[]>('/api/v1/chat/conversations')
}

export function fetchConversationMessages(conversationId: string): Promise<ChatMessage[]> {
  return apiGet<ChatMessage[]>(`/api/v1/chat/conversations/${conversationId}/messages`)
}

/** Uploads one image from the chat box; the returned Attachment is then included on the next send call. */
export function uploadAttachment(file: File): Promise<Attachment> {
  const formData = new FormData()
  formData.append('file', file)
  return apiPostForm<Attachment>('/api/v1/chat/uploads', formData)
}
