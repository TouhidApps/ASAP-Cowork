import type { ReactNode } from 'react'
import '@/components/collapsibleSection.css'

/** A native <details>/<summary> collapsible bar — no JS state needed, works with the keyboard/screen readers for free. */
export function CollapsibleSection({
  title,
  defaultOpen = false,
  children,
}: {
  title: string
  defaultOpen?: boolean
  children: ReactNode
}) {
  return (
    <details className="collapsible-section" open={defaultOpen}>
      <summary>
        {title}
        <svg
          className="collapsible-section-chevron"
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          aria-hidden="true"
        >
          <path d="M9 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </summary>
      <div className="collapsible-section-body">{children}</div>
    </details>
  )
}
