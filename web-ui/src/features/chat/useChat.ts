import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchConversationMessages, fetchConversations } from '@/features/chat/api'
import type { Attachment, ChatEvent, ChatMessage, ChatStage, Conversation, NoteUsed, ToolActivity } from '@/features/chat/types'

interface ChatState {
  conversations: Conversation[]
  currentConversationId: string | null
  messages: ChatMessage[]
  loading: boolean
  connected: boolean
  sending: boolean
  loadError?: string
  sendError?: string
}

/** What the WebSocket effect should (re)connect as — a resumed conversation, or a fresh one. */
interface ConnectionTarget {
  conversationId: string | null
  // Bumped on every explicit navigation (selectConversation/newChat) so the
  // effect reconnects even when conversationId repeats, e.g. clicking "New
  // chat" twice in a row before sending anything both times resolve to null.
  nonce: number
}

const WS_URL =
  import.meta.env.VITE_WS_URL ??
  `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`

/** How long to wait before retrying a dropped/failed WebSocket connection. */
const RECONNECT_DELAY_MS = 1500

// Remembers the user's last explicit navigation (a conversation id, or the
// "new chat" sentinel below) across page refreshes. Without this, a refresh
// has no way to tell "user clicked New chat" apart from "app just opened",
// so it always fell back to loading the most recent conversation.
const LAST_CONVERSATION_KEY = 'asap-cowork:last-conversation-id'
const NEW_CHAT_SENTINEL = '__new__'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

/**
 * Drives the chat-gateway WebSocket plus the REST history endpoints
 * (ChatRoutes.kt) backing the history drawer. One WS connection is one
 * conversation — switching conversations or starting a new one closes the
 * current socket and opens another, optionally resuming a specific
 * conversation via `?conversationId=`. A brand-new conversation's id isn't
 * known until the server creates it (on the first message, to avoid saving
 * empty "New chat" rows) and reports it back via a `conversation_started`
 * event — that event updates `currentConversationId` and the drawer list
 * directly, without going through [ConnectionTarget], so learning an id
 * never itself triggers a reconnect of the socket that just reported it.
 *
 * Routing is automatic: the server's `Orchestrator.route()` classifies each
 * message and emits an `agent_activated` event naming the agent it picked,
 * which this hook attaches to the resulting assistant message as `stage`.
 */
export function useChat() {
  const [state, setState] = useState<ChatState>({
    conversations: [],
    currentConversationId: null,
    messages: [],
    loading: true,
    connected: false,
    sending: false,
  })
  const [connectionTarget, setConnectionTarget] = useState<ConnectionTarget | null>(null)

  const socketRef = useRef<WebSocket | null>(null)
  const streamingIdRef = useRef<string | null>(null)
  const streamingStageRef = useRef<ChatStage | undefined>(undefined)

  const loadMessages = useCallback((conversationId: string) => {
    setState((s) => ({ ...s, loading: true, messages: [] }))
    fetchConversationMessages(conversationId)
      .then((messages) => setState((s) => ({ ...s, messages, loading: false })))
      .catch((error: unknown) =>
        setState((s) => ({ ...s, loading: false, loadError: errorMessage(error, 'Failed to load chat history') })),
      )
  }, [])

  const hasInitialized = useRef(false)

  useEffect(() => {
    // Guards against React StrictMode's dev-only double-invoke firing two
    // concurrent fetches, which could otherwise pick two different "most
    // recent" conversations and race on which one wins.
    if (hasInitialized.current) return
    hasInitialized.current = true

    const lastId = localStorage.getItem(LAST_CONVERSATION_KEY)

    fetchConversations()
      .then((conversations) => {
        if (conversations.length === 0 || lastId === NEW_CHAT_SENTINEL) {
          // Nothing yet, or the user's last action was explicitly "New
          // chat" — connect fresh; the server creates a conversation
          // lazily once the user actually sends something.
          setState((s) => ({ ...s, conversations, loading: false }))
          setConnectionTarget({ conversationId: null, nonce: 0 })
          return
        }
        // Resume whatever the user was last looking at, falling back to the
        // most recent conversation on first-ever visit or if the remembered
        // id no longer exists.
        const target = conversations.find((c) => c.id === lastId) ?? conversations[0]
        setState((s) => ({ ...s, conversations, currentConversationId: target.id }))
        loadMessages(target.id)
        setConnectionTarget({ conversationId: target.id, nonce: 0 })
      })
      .catch((error: unknown) => {
        setState((s) => ({ ...s, loading: false, loadError: errorMessage(error, 'Failed to load conversations') }))
        setConnectionTarget({ conversationId: null, nonce: 0 })
      })
    // Intentionally run once on mount — loadMessages is stable (useCallback, no deps).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!connectionTarget) return

    streamingIdRef.current = null
    streamingStageRef.current = undefined

    // The dev proxy/backend isn't always up the instant this page loads (or
    // the connection can drop mid-session) — without a retry, the composer
    // stayed disabled (MessageInput's `disabled={!connected}`) until the
    // user happened to trigger a fresh connection some other way, e.g.
    // clicking "New chat". `cancelled` stops a stale retry from firing after
    // this effect's own cleanup (unmount, or connectionTarget changing).
    let cancelled = false
    let retryTimeout: ReturnType<typeof setTimeout> | undefined

    const url = connectionTarget.conversationId
      ? `${WS_URL}?conversationId=${encodeURIComponent(connectionTarget.conversationId)}`
      : WS_URL

    const connect = () => {
      const socket = new WebSocket(url)
      socketRef.current = socket

      socket.onopen = () => setState((s) => ({ ...s, connected: true }))
      socket.onclose = () => {
        setState((s) => ({ ...s, connected: false }))
        if (!cancelled) retryTimeout = setTimeout(connect, RECONNECT_DELAY_MS)
      }

      socket.onmessage = (event) => {
        const parsed: ChatEvent = JSON.parse(event.data)

        // Mutates streamingIdRef as a plain side effect of handling this one
        // event — never from inside a setState updater below. StrictMode
        // double-invokes updater functions in dev to catch impure ones; an
        // updater that both reads and writes a ref (as an earlier version of
        // this hook did) returns a different result the discarded second
        // time it runs, silently dropping the message it was supposed to add.
        const ensureStreamingId = (): string => {
          if (!streamingIdRef.current) streamingIdRef.current = `assistant-${Date.now()}`
          return streamingIdRef.current
        }

        const withStreamingMessage = (messages: ChatMessage[], id: string): ChatMessage[] =>
          messages.some((m) => m.id === id)
            ? messages
            : [
                ...messages,
                { id, role: 'assistant', content: '', createdAt: Date.now(), stage: streamingStageRef.current, streaming: true },
              ]

        switch (parsed.type) {
          case 'conversation_started':
            if (!parsed.conversationId) break
            localStorage.setItem(LAST_CONVERSATION_KEY, parsed.conversationId)
            setState((s) => {
              const id = parsed.conversationId as string
              const alreadyKnown = s.conversations.some((c) => c.id === id)
              return {
                ...s,
                currentConversationId: id,
                conversations: alreadyKnown
                  ? s.conversations
                  : [{ id, title: 'New chat', createdAt: Date.now(), updatedAt: Date.now() }, ...s.conversations],
              }
            })
            break
          case 'agent_activated':
            streamingStageRef.current = parsed.capability as ChatStage | undefined
            break
          case 'progress': {
            const id = ensureStreamingId()
            setState((s) => ({ ...s, messages: withStreamingMessage(s.messages, id) }))
            break
          }
          case 'tool_activity': {
            const id = ensureStreamingId()
            const activity: ToolActivity = {
              tool: parsed.tool ?? '',
              label: parsed.message ?? '',
              status: parsed.status ?? 'started',
            }
            setState((s) => ({
              ...s,
              messages: withStreamingMessage(s.messages, id).map((m) => {
                if (m.id !== id) return m
                const existing = m.toolActivity ?? []
                // Update the most recent still-running entry in place, rather
                // than appending — tool calls run one at a time, so there's at
                // most one. This covers both a progress update (a still-running
                // tool like run_gradle repeatedly reports 'started' with a
                // changing label) and the call finishing ('finished'/'failed'
                // replacing that 'started' entry) with the same logic.
                const runningIndex = existing.map((a) => a.status).lastIndexOf('started')
                const updated =
                  runningIndex !== -1
                    ? existing.map((a, i) => (i === runningIndex ? activity : a))
                    : [...existing, activity]
                return { ...m, toolActivity: updated }
              }),
            }))
            break
          }
          case 'note_used': {
            const id = ensureStreamingId()
            const noteUsed: NoteUsed = { snippet: parsed.message ?? '' }
            setState((s) => ({
              ...s,
              messages: withStreamingMessage(s.messages, id).map((m) =>
                m.id === id ? { ...m, notesUsed: [...(m.notesUsed ?? []), noteUsed] } : m,
              ),
            }))
            break
          }
          case 'text_delta': {
            const id = ensureStreamingId()
            const text = parsed.text ?? ''
            setState((s) => ({
              ...s,
              messages: withStreamingMessage(s.messages, id).map((m) =>
                m.id === id ? { ...m, content: m.content + text } : m,
              ),
            }))
            break
          }
          case 'file_changed': {
            const id = ensureStreamingId()
            setState((s) => ({
              ...s,
              messages: withStreamingMessage(s.messages, id).map((m) =>
                m.id === id
                  ? { ...m, files: [...(m.files ?? []), { path: parsed.path ?? '', summary: parsed.message ?? '' }] }
                  : m,
              ),
            }))
            break
          }
          case 'result': {
            const id = streamingIdRef.current
            setState((s) => ({
              ...s,
              sending: false,
              messages: id ? s.messages.map((m) => (m.id === id ? { ...m, streaming: false } : m)) : s.messages,
            }))
            streamingIdRef.current = null
            // The title (derived from the first message) and sort order may
            // have changed — a cheap background refresh keeps the drawer in sync.
            fetchConversations()
              .then((conversations) => setState((s) => ({ ...s, conversations })))
              .catch(() => {
                // Non-critical background refresh — keep the stale list on failure.
              })
            break
          }
          case 'error': {
            const id = streamingIdRef.current
            setState((s) => ({
              ...s,
              sending: false,
              sendError: parsed.message ?? 'Something went wrong',
              messages: id ? s.messages.filter((m) => m.id !== id) : s.messages,
            }))
            streamingIdRef.current = null
            break
          }
        }
      }
    }

    connect()

    return () => {
      cancelled = true
      if (retryTimeout) clearTimeout(retryTimeout)
      socketRef.current?.close()
    }
  }, [connectionTarget])

  const selectConversation = useCallback(
    (conversationId: string) => {
      localStorage.setItem(LAST_CONVERSATION_KEY, conversationId)
      setState((s) => ({ ...s, currentConversationId: conversationId, sendError: undefined }))
      loadMessages(conversationId)
      setConnectionTarget((prev) => ({ conversationId, nonce: (prev?.nonce ?? 0) + 1 }))
    },
    [loadMessages],
  )

  const newChat = useCallback(() => {
    localStorage.setItem(LAST_CONVERSATION_KEY, NEW_CHAT_SENTINEL)
    setState((s) => ({ ...s, currentConversationId: null, messages: [], loading: false, sendError: undefined }))
    setConnectionTarget((prev) => ({ conversationId: null, nonce: (prev?.nonce ?? 0) + 1 }))
  }, [])

  const send = useCallback((content: string, attachments: Attachment[] = []) => {
    const trimmed = content.trim()
    const socket = socketRef.current
    if ((!trimmed && attachments.length === 0) || !socket || socket.readyState !== WebSocket.OPEN) return

    streamingStageRef.current = undefined
    setState((s) => ({
      ...s,
      sending: true,
      sendError: undefined,
      messages: [
        ...s.messages,
        { id: `user-${Date.now()}`, role: 'user', content: trimmed, createdAt: Date.now(), attachments },
      ],
    }))
    socket.send(JSON.stringify({ content: trimmed, attachments }))
  }, [])

  return { ...state, send, newChat, selectConversation }
}
