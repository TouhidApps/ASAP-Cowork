/** Mirrors chat-gateway's features/project/ProjectFilesModels.kt. */
export type ProjectEntryType = 'DIRECTORY' | 'FILE'

export interface ProjectEntry {
  name: string
  path: string
  type: ProjectEntryType
  sizeBytes: number | null
}

export interface ProjectTreeResult {
  path: string
  entries: ProjectEntry[]
}

export type ProjectFileKind = 'TEXT' | 'IMAGE' | 'BINARY'

export interface ProjectFileResult {
  path: string
  kind: ProjectFileKind
  sizeBytes: number
  content: string | null
  truncated: boolean
  language: string | null
}
