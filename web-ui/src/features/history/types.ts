/** Mirrors chat-gateway's WorkspaceHistoryService.CommitInfo — one entry per AI turn that changed files. */
export interface HistoryEntry {
  commitId: string
  label: string
  createdAt: number
  filesChanged: number
  conversationId?: string
  messageId?: string
}

/** Mirrors WorkspaceHistoryService.FileDiff — patch is a real git unified diff for one file. */
export interface FileDiff {
  path: string
  changeType: 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME' | 'COPY'
  patch: string
}
