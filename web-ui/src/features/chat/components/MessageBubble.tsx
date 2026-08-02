import { Fragment } from 'react'
import { API_BASE_URL } from '@/api/client'
import { AssistantAvatarIcon, CheckIcon, CloseIcon, PlayIcon, UserIcon } from '@/features/chat/icons'
import { STAGES } from '@/features/chat/types'
import type { ChatMessage, OpenMedia, ToolActivity } from '@/features/chat/types'

const stageLabel = (stage: ChatMessage['stage']) => STAGES.find((s) => s.id === stage)?.label ?? stage

// Matches the one markdown-image line emitMediaNotes (llm-gateway's
// ToolCalling.kt) appends after a tool result with an imageUrl — a device/
// simulator screenshot (/api/v1/screenshots/...) or a branding SVG written
// by write_brand_asset (/api/v1/branding/...), e.g. ![Screenshot](/api/v1/screenshots/x.png).
const IMAGE_LINE = /^!\[([^\]]*)\]\((\/api\/v1\/(?:screenshots|branding)\/[^\s)]+)\)$/
// Matches the one markdown-style video line emitMediaNotes appends after a
// record_device_video tool call, e.g. [Video](/api/v1/videos/x.mp4).
const VIDEO_LINE = /^\[Video\]\((\/api\/v1\/videos\/[^\s)]+)\)$/
// Matches the one markdown-link line emitMediaNotes appends for a tool
// result with a fileUrl — a non-image branding file like brand-guide.md
// written by write_brand_asset, e.g. [brand-guide.md](/api/v1/branding/brand-guide.md).
const FILE_LINE = /^\[([^\]]+)\]\((\/api\/v1\/branding\/[^\s)]+)\)$/
// Matches the highlighted-callout line emitMediaNotes appends for
// ToolResult.notice (e.g. record_device_video's "use a physical device for
// better performance" tip) — a markdown blockquote, e.g. "> some text".
const NOTICE_LINE = /^> (.+)$/

function renderContent(content: string, onOpenMedia: (media: OpenMedia) => void) {
  // emitMediaNotes (llm-gateway) deterministically appends a screenshot/video
  // line right after a tool call so it's never missed — but the tool result
  // fed back to the model includes that same raw URL in its summary text
  // (e.g. "Captured screenshot: /api/v1/screenshots/x.png"), and the model's
  // own reply sometimes echoes/reformats that into an identical markdown
  // line when describing what it did. Tracking URLs already rendered in
  // this message drops that second, redundant line instead of showing the
  // same image/video twice.
  const seenMediaUrls = new Set<string>()

  return content.split('\n').map((line, index) => {
    const imageMatch = IMAGE_LINE.exec(line)
    const videoMatch = VIDEO_LINE.exec(line)
    const fileMatch = FILE_LINE.exec(line)
    const noticeMatch = NOTICE_LINE.exec(line)
    const mediaUrl = imageMatch?.[2] ?? videoMatch?.[1] ?? fileMatch?.[2]
    const isDuplicateMedia = mediaUrl !== undefined && seenMediaUrls.has(mediaUrl)
    if (mediaUrl !== undefined) seenMediaUrls.add(mediaUrl)

    const body = isDuplicateMedia
      ? null
      : imageMatch ? (
          <img
            src={`${API_BASE_URL}${imageMatch[2]}`}
            alt={imageMatch[1] || 'Screenshot'}
            className="chat-screenshot"
            onClick={() => onOpenMedia({ kind: 'image', src: `${API_BASE_URL}${imageMatch[2]}`, alt: imageMatch[1] })}
          />
        ) : videoMatch ? (
          // No native `controls` on the thumbnail — with controls, a click both
          // toggles play/pause *and* opens the lightbox at once. Full playback
          // controls only show once it's open full-size. The play icon overlay
          // is the only thing distinguishing this from a screenshot at a
          // glance, since a muted, control-less <video> just renders its first
          // frame — indistinguishable from a static image otherwise.
          <div
            className="chat-video-thumb"
            onClick={() => onOpenMedia({ kind: 'video', src: `${API_BASE_URL}${videoMatch[1]}` })}
          >
            <video src={`${API_BASE_URL}${videoMatch[1]}`} className="chat-video" muted />
            <span className="chat-video-play-icon" aria-hidden="true">
              <PlayIcon />
            </span>
          </div>
        ) : fileMatch ? (
          <a href={`${API_BASE_URL}${fileMatch[2]}`} target="_blank" rel="noreferrer" className="chat-file-link">
            {fileMatch[1]}
          </a>
        ) : noticeMatch ? (
          <div className="chat-notice">
            <span className="chat-notice-icon" aria-hidden="true">
              💡
            </span>
            {noticeMatch[1]}
          </div>
        ) : (
          line
        )
    return (
      <Fragment key={index}>
        {index > 0 && '\n'}
        {body}
      </Fragment>
    )
  })
}

function ToolActivityList({ activities }: { activities: ToolActivity[] }) {
  return (
    <div className="chat-tool-activity">
      {activities.map((activity, index) => (
        <div key={index} className={`chat-tool-activity-item chat-tool-activity-item--${activity.status}`}>
          <span className="chat-tool-activity-icon" aria-hidden="true">
            {activity.status === 'started' && <span className="chat-tool-activity-spinner" />}
            {activity.status === 'finished' && <CheckIcon />}
            {activity.status === 'failed' && <CloseIcon />}
          </span>
          <span className="chat-tool-activity-label">{activity.label}</span>
        </div>
      ))}
    </div>
  )
}

export function MessageBubble({
  message,
  onOpenMedia,
}: {
  message: ChatMessage
  onOpenMedia: (media: OpenMedia) => void
}) {
  const isUser = message.role === 'user'
  const toolActivity = message.toolActivity ?? []
  const isWaitingForFirstToken = message.streaming && message.content === '' && toolActivity.length === 0

  return (
    <div className={`chat-row chat-row--${message.role}`}>
      <div className="chat-avatar" aria-hidden="true">
        {isUser ? <UserIcon /> : <AssistantAvatarIcon />}
      </div>
      {isWaitingForFirstToken ? (
        <div className="chat-typing" aria-label="Assistant is thinking">
          <span />
          <span />
          <span />
        </div>
      ) : (
        <div className="chat-row-content">
          {!isUser && message.stage && <div className="chat-stage-label">{stageLabel(message.stage)}</div>}
          {message.attachments && message.attachments.length > 0 && (
            <div className="chat-attachments">
              {message.attachments.map((attachment) => (
                <img
                  key={attachment.id}
                  src={`${API_BASE_URL}${attachment.url}`}
                  alt="Attached"
                  className="chat-attachment-image"
                />
              ))}
            </div>
          )}
          {toolActivity.length > 0 && <ToolActivityList activities={toolActivity} />}
          {renderContent(message.content, onOpenMedia)}
          {message.streaming && <span className="chat-cursor" aria-hidden="true" />}
          {message.files && message.files.length > 0 && (
            <ul className="chat-file-list">
              {message.files.map((file, index) => (
                <li key={index}>{file.summary || file.path}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
