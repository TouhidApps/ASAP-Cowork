import { useState } from 'react'
import { ChatHistoryDrawer } from '@/features/chat/components/ChatHistoryDrawer'
import { ExamplesDrawer } from '@/features/chat/components/ExamplesDrawer'
import { MessageInput } from '@/features/chat/components/MessageInput'
import { MessageList } from '@/features/chat/components/MessageList'
import { HistoryIcon, PlusIcon, SparkleIcon, TerminalIcon } from '@/features/chat/icons'
import { useChat } from '@/features/chat/useChat'
import { LogcatDrawer } from '@/features/logcat/components/LogcatDrawer'
import '@/features/chat/chat.css'

export function ChatPage() {
  const {
    conversations,
    currentConversationId,
    messages,
    loading,
    connected,
    sending,
    loadError,
    sendError,
    send,
    newChat,
    selectConversation,
  } = useChat()
  const [historyOpen, setHistoryOpen] = useState(false)
  const [logsOpen, setLogsOpen] = useState(false)
  const [examplesOpen, setExamplesOpen] = useState(false)
  // `nonce` (not just the text) is what MessageInput watches to refill the
  // composer — picking the exact same starter prompt twice in a row would
  // otherwise be a no-op the second time, since the text itself wouldn't
  // have changed.
  const [prefill, setPrefill] = useState<{ text: string; nonce: number } | null>(null)

  const handleSelect = (conversationId: string) => {
    selectConversation(conversationId)
    setHistoryOpen(false)
  }

  const handleNewChat = () => {
    newChat()
    setHistoryOpen(false)
  }

  return (
    <div className="chat-window">
      <div className="chat-toolbar">
        <div className="chat-toolbar-left">
          <button className="chat-history-button" onClick={() => setHistoryOpen(true)} aria-label="Show chat history">
            <HistoryIcon />
            <span className="chat-button-label">History</span>
          </button>
          <button className="chat-history-button" onClick={() => setLogsOpen(true)} aria-label="Show device logs">
            <TerminalIcon />
            <span className="chat-button-label">Device Logs</span>
          </button>
        </div>
        <div className="chat-toolbar-right">
          <button className="chat-new-button" onClick={handleNewChat} disabled={sending} aria-label="Start a new chat">
            <PlusIcon />
            <span className="chat-button-label">New chat</span>
          </button>
          <button className="chat-history-button" onClick={() => setExamplesOpen(true)} aria-label="Show example prompts">
            <SparkleIcon />
          </button>
        </div>
      </div>
      <ChatHistoryDrawer
        open={historyOpen}
        conversations={conversations}
        currentConversationId={currentConversationId}
        disabled={sending}
        onClose={() => setHistoryOpen(false)}
        onSelect={handleSelect}
        onNewChat={handleNewChat}
      />
      <LogcatDrawer open={logsOpen} onClose={() => setLogsOpen(false)} />
      <ExamplesDrawer
        open={examplesOpen}
        onClose={() => setExamplesOpen(false)}
        onPick={(prompt) => setPrefill({ text: prompt, nonce: Date.now() })}
      />
      <div className="chat-scroll">
        {/* Reconnecting for a new/switched conversation briefly drops
            `connected` too — gating on it here would replace the whole
            message area (or the "New chat" welcome screen) with a
            "Connecting…" flash every time. The composer below already
            disables sending while disconnected, which is all that actually
            needs to wait on the socket being open. */}
        {loading && <p className="chat-empty-state">Loading conversation…</p>}
        {loadError && <p className="chat-error">{loadError}</p>}
        {!loading && !loadError && (
          <MessageList
            messages={messages}
            // Covers the gap between hitting send (sending flips true, and
            // the user's own message appears, instantly) and the server's
            // first WS event actually arriving (intent classification, then
            // routing to an agent, take a moment) — without this, "sent"
            // looked like nothing happened for a few seconds.
            waiting={sending && !messages.some((m) => m.streaming)}
            onPickStarterPrompt={(prompt) => setPrefill({ text: prompt, nonce: Date.now() })}
          />
        )}
      </div>
      <div className="chat-composer">
        {sendError && <p className="chat-error">{sendError}</p>}
        <MessageInput sendDisabled={!connected || sending} onSend={send} prefill={prefill} />
      </div>
    </div>
  )
}
