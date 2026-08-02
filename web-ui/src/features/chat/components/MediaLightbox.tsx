import { useEffect } from 'react'
import type { OpenMedia } from '@/features/chat/types'

/** Full-size view of a screenshot/video thumbnail clicked in the chat — MessageList owns which (if any) is open. */
export function MediaLightbox({ media, onClose }: { media: OpenMedia | null; onClose: () => void }) {
  useEffect(() => {
    if (!media) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [media, onClose])

  if (!media) return null

  return (
    <div className="media-lightbox-backdrop" onClick={onClose}>
      <button className="media-lightbox-close" onClick={onClose} aria-label="Close">
        ×
      </button>
      {media.kind === 'image' ? (
        <img
          src={media.src}
          alt={media.alt ?? ''}
          className="media-lightbox-content"
          onClick={(e) => e.stopPropagation()}
        />
      ) : (
        <video
          src={media.src}
          controls
          autoPlay
          className="media-lightbox-content"
          onClick={(e) => e.stopPropagation()}
        />
      )}
    </div>
  )
}
