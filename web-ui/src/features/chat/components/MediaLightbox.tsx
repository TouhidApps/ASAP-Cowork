import { useEffect, useState } from 'react'
import type { OpenMedia } from '@/features/chat/types'

/** Full-size view of a screenshot/video thumbnail clicked in the chat — MessageList owns which (if any) is open. Navigates prev/next across every item in `media.items` when there's more than one (e.g. a message that produced several generated images). */
export function MediaLightbox({ media, onClose }: { media: OpenMedia | null; onClose: () => void }) {
  const [index, setIndex] = useState(0)

  useEffect(() => {
    if (media) setIndex(media.index)
  }, [media])

  const items = media?.items ?? []
  const hasMultiple = items.length > 1
  const current = items[index]

  useEffect(() => {
    if (!media) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      if (e.key === 'ArrowRight' && hasMultiple) setIndex((i) => (i + 1) % items.length)
      if (e.key === 'ArrowLeft' && hasMultiple) setIndex((i) => (i - 1 + items.length) % items.length)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [media, hasMultiple, items.length, onClose])

  if (!media || !current) return null

  return (
    <div className="media-lightbox-backdrop" onClick={onClose}>
      <button className="media-lightbox-close" onClick={onClose} aria-label="Close">
        ×
      </button>
      {hasMultiple && (
        <button
          className="media-lightbox-prev"
          aria-label="Previous"
          onClick={(e) => {
            e.stopPropagation()
            setIndex((i) => (i - 1 + items.length) % items.length)
          }}
        >
          ‹
        </button>
      )}
      {current.kind === 'image' ? (
        <img
          src={current.src}
          alt={current.alt ?? ''}
          className="media-lightbox-content"
          onClick={(e) => e.stopPropagation()}
        />
      ) : (
        <video
          src={current.src}
          controls
          autoPlay
          className="media-lightbox-content"
          onClick={(e) => e.stopPropagation()}
        />
      )}
      {hasMultiple && (
        <button
          className="media-lightbox-next"
          aria-label="Next"
          onClick={(e) => {
            e.stopPropagation()
            setIndex((i) => (i + 1) % items.length)
          }}
        >
          ›
        </button>
      )}
      {current.alt && <div className="media-lightbox-caption">{current.alt}</div>}
    </div>
  )
}
