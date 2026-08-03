import { useEffect, useRef, useState } from 'react'
import { MediaLightbox } from '@/features/chat/components/MediaLightbox'
import { MessageBubble } from '@/features/chat/components/MessageBubble'
import {
  AndroidIcon,
  AppleIcon,
  AssistantAvatarIcon,
  FlutterIcon,
  KmpIcon,
  KotlinIcon,
  ReactIcon,
  SwiftIcon,
} from '@/features/chat/icons'
import type { ChatMessage, OpenMedia } from '@/features/chat/types'

// A handful of common asks spanning what the platform can already do
// (inspect the workspace, scaffold, build/run, verify visually) — enough to
// get someone unfamiliar with the agent started without staring at a blank
// composer, not an exhaustive menu.
const STARTER_PROMPTS = [
  'Show projects in workspace directory',
  'Make a new mobile app',
  'Build and run the app on the emulator',
  'Take a screenshot of connected device',
  'Make a video of the app after build',
]

// What this agent actually builds for, front and center on the one screen a
// new user sees before typing anything — the platform's whole point is
// covering every major mobile stack, not just "an AI chat".
const SUPPORTED_PLATFORMS = [
  { label: 'Kotlin', Icon: KotlinIcon },
  { label: 'Android', Icon: AndroidIcon },
  { label: 'iOS', Icon: AppleIcon },
  { label: 'Swift', Icon: SwiftIcon },
  { label: 'Flutter', Icon: FlutterIcon },
  { label: 'KMP', Icon: KmpIcon },
  { label: 'React Native', Icon: ReactIcon },
]

export function MessageList({
  messages,
  waiting,
  onPickStarterPrompt,
}: {
  messages: ChatMessage[]
  /** True right after sending, before the assistant's own streaming message exists yet — the server needs a moment (intent classification, agent startup) before its first event arrives, and without this the "thinking" indicator wouldn't show until then. */
  waiting: boolean
  onPickStarterPrompt: (prompt: string) => void
}) {
  const bottomRef = useRef<HTMLDivElement>(null)
  const [openMedia, setOpenMedia] = useState<OpenMedia | null>(null)

  useEffect(() => {
    // Depend on the whole array, not just its length — useChat gives it a
    // new reference on every streamed token too, so this also keeps up as
    // a message's content grows, not just when a new message is added.
    // `waiting` is included too, so the typing indicator scrolling into
    // view isn't delayed until the assistant's real message shows up.
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, waiting])

  if (messages.length === 0) {
    return (
      <div className="chat-welcome">
        <img src="/icon.png" alt="" className="chat-welcome-icon" />
        <h2>How can I help you today?</h2>
        <p>Describe the app you want to build.</p>
        <div className="chat-platform-row" aria-label="Supported platforms">
          {SUPPORTED_PLATFORMS.map(({ label, Icon }) => (
            <span key={label} className="chat-platform-badge" title={label}>
              <Icon />
              <span className="chat-platform-label">{label}</span>
            </span>
          ))}
        </div>
        <div className="chat-starter-prompts">
          {STARTER_PROMPTS.map((prompt) => (
            <button key={prompt} type="button" className="chat-starter-prompt" onClick={() => onPickStarterPrompt(prompt)}>
              {prompt}
            </button>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="chat-messages">
      {messages.map((message) => (
        <MessageBubble key={message.id} message={message} onOpenMedia={setOpenMedia} />
      ))}
      {waiting && (
        <div className="chat-row chat-row--assistant">
          <div className="chat-avatar" aria-hidden="true">
            <AssistantAvatarIcon />
          </div>
          <div className="chat-typing" aria-label="Assistant is thinking">
            <span />
            <span />
            <span />
          </div>
        </div>
      )}
      <div ref={bottomRef} />
      <MediaLightbox media={openMedia} onClose={() => setOpenMedia(null)} />
    </div>
  )
}
