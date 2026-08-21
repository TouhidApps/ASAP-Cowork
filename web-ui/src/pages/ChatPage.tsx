import { useState } from 'react'
import { ChatHistoryDrawer } from '@/features/chat/components/ChatHistoryDrawer'
import { ExamplesDrawer } from '@/features/chat/components/ExamplesDrawer'
import { MessageInput } from '@/features/chat/components/MessageInput'
import { MessageList } from '@/features/chat/components/MessageList'
import { DiffIcon, FolderIcon, HistoryIcon, PlanIcon, PlusIcon, SparkleIcon, TerminalIcon } from '@/features/chat/icons'
import { useChat } from '@/features/chat/useChat'
import { CodeChangesPanel } from '@/features/history/components/CodeChangesPanel'
import { LogcatDrawer } from '@/features/logcat/components/LogcatDrawer'
import { PlanPreviewPanel } from '@/features/plan/components/PlanPreviewPanel'
import { ProjectExplorerPanel } from '@/features/project/components/ProjectExplorerPanel'
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
  const [projectOpen, setProjectOpen] = useState(false)
  const [changesOpen, setChangesOpen] = useState(false)
  const [planOpen, setPlanOpen] = useState(false)
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
      <div className="chat-main">
        <div className="chat-toolbar">
          <div className="chat-toolbar-left">
            <button className="chat-history-button" onClick={() => setHistoryOpen((v) => !v)} aria-label="Show chat history">
              <HistoryIcon />
              <span className="chat-button-label">History</span>
            </button>
            <button className="chat-history-button" onClick={() => setLogsOpen(true)} aria-label="Show device logs">
              <TerminalIcon />
              <span className="chat-button-label">Device Logs</span>
            </button>
            <button
              className={`chat-history-button${projectOpen ? ' active' : ''}`}
              onClick={() => setProjectOpen((v) => !v)}
              aria-label="Show project files"
            >
              <FolderIcon />
              <span className="chat-button-label">Project</span>
            </button>
            <button
              className={`chat-history-button${changesOpen ? ' active' : ''}`}
              onClick={() => setChangesOpen((v) => !v)}
              aria-label="Show code changes"
            >
              <DiffIcon />
              <span className="chat-button-label">Code Changes</span>
            </button>
            <button
              className={`chat-history-button${planOpen ? ' active' : ''}`}
              onClick={() => setPlanOpen((v) => !v)}
              aria-label="Show plan preview"
            >
              <PlanIcon />
              <span className="chat-button-label">Plan Preview</span>
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
          <ChatHistoryDrawer
            open={historyOpen}
            conversations={conversations}
            currentConversationId={currentConversationId}
            disabled={sending}
            onClose={() => setHistoryOpen(false)}
            onSelect={handleSelect}
            onNewChat={handleNewChat}
          />
        </div>
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
      {changesOpen && <CodeChangesPanel onClose={() => setChangesOpen(false)} />}
      {planOpen && <PlanPreviewPanel onClose={() => setPlanOpen(false)} />}
      {projectOpen && <ProjectExplorerPanel onClose={() => setProjectOpen(false)} />}
    </div>
  )
}
