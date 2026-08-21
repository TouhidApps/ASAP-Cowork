export function UserIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <circle cx="12" cy="8" r="4" fill="currentColor" />
      <path
        d="M4 20c0-4 3.5-6.5 8-6.5s8 2.5 8 6.5"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        fill="none"
      />
    </svg>
  )
}

// The app's own logo, rotated 90° clockwise so its upward mark reads as an
// arrow — used as the assistant's reply avatar instead of a generic bot icon.
export function AssistantAvatarIcon() {
  return <img src="/icon.png" alt="" className="chat-avatar-brand" />
}

export function SendIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path
        d="M12 19V5M12 5l-6 6M12 5l6 6"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function HistoryIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
      <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  )
}

export function AttachIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path
        d="M17 7.5l-7.2 7.2a3 3 0 004.24 4.24l7.2-7.2a5 5 0 00-7.07-7.07l-7.2 7.2a7 7 0 009.9 9.9"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function TerminalIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="2" />
      <path d="M7 9l4 3-4 3M13 15h4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function DiffIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path d="M9 4v16M15 4v16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <path d="M4 9h5M15 9h5M4 15h5M15 15h5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function PlanIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path d="M7 3h7l4 4v14H7z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="M14 3v4h4" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="M9.5 12h5M9.5 15.5h5M9.5 18.5h3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

export function FolderIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path
        d="M4 6.5A1.5 1.5 0 0 1 5.5 5h4l2 2.2h7A1.5 1.5 0 0 1 20 8.7v9.8A1.5 1.5 0 0 1 18.5 20h-13A1.5 1.5 0 0 1 4 18.5v-12z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function ChevronRightIcon() {
  return (
    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
      <path d="M9 5l7 7-7 7" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function FileTextIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
      <path
        d="M6.5 3.5h7l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-9.5A1.5 1.5 0 0 1 5 19V5a1.5 1.5 0 0 1 1.5-1.5z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <path d="M13.5 3.5V8h4.5" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  )
}

export function ImageFileIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
      <rect x="5" y="4.5" width="14" height="15" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <circle cx="9.2" cy="9" r="1.4" stroke="currentColor" strokeWidth="1.4" />
      <path d="M5.8 17l4.4-4.6a1.3 1.3 0 0 1 1.9.05L19 19" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  )
}

export function RefreshIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
      <path
        d="M19 5v5h-5M5 19v-5h5M5.5 9a7 7 0 0 1 12.3-3.2M18.5 15a7 7 0 0 1-12.3 3.2"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
      <path d="M5 13l4 4 10-10" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
      <path d="M5 5l14 14M19 5L5 19" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  )
}

export function NoteIcon() {
  return (
    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
      <path
        d="M6 3.5h9l3 3V19a1.5 1.5 0 0 1-1.5 1.5h-10.5A1.5 1.5 0 0 1 4.5 19V5A1.5 1.5 0 0 1 6 3.5z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M8 9.5h8M8 13h8M8 16.5h5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

export function SparkleIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
      <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z" fill="currentColor" />
      <path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9L19 15z" fill="currentColor" />
    </svg>
  )
}

export function PlayIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true">
      <path d="M7 5.5v13a1 1 0 0 0 1.53.85l10.5-6.5a1 1 0 0 0 0-1.7l-10.5-6.5A1 1 0 0 0 7 5.5z" />
    </svg>
  )
}

// Simplified, original marks for the welcome screen's "what this builds"
// row — not traced from each brand's official logo files, just clean shapes
// evoking the familiar silhouette (bugdroid head, an apple, an atom, ...) at
// icon size, in each platform's usual color for quick recognition.

export function KotlinIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" aria-hidden="true">
      <defs>
        <linearGradient id="platform-kotlin" x1="2" y1="2" x2="26" y2="26" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#e44857" />
          <stop offset="0.5" stopColor="#c711e1" />
          <stop offset="1" stopColor="#7f52ff" />
        </linearGradient>
      </defs>
      <path d="M2 2h24L14 14l12 12H2V2z" fill="url(#platform-kotlin)" />
    </svg>
  )
}

// Bird silhouette adapted from Simple Icons (simpleicons.org), MIT/CC0-licensed
// SVG data used for tech-stack "built with" badges across the ecosystem —
// background chip dropped and recolored with this file's gradient style so it
// sits consistently among the other platform marks below.
export function SwiftIcon() {
  return (
    <svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true">
      <defs>
        <linearGradient id="platform-swift" x1="2" y1="3" x2="21" y2="20" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#ffb040" />
          <stop offset="1" stopColor="#ef4d2a" />
        </linearGradient>
      </defs>
      <path
        fill="url(#platform-swift)"
        d="M13.543 3.41c4.114 2.47 6.545 7.162 5.549 11.131-.024.093-.05.181-.076.272l.002.001c2.062 2.538 1.5 5.258 1.236 4.745-1.072-2.086-3.066-1.568-4.088-1.043a6.803 6.803 0 0 1-.281.158l-.02.012-.002.002c-2.115 1.123-4.957 1.205-7.812-.022a12.568 12.568 0 0 1-5.64-4.838c.649.48 1.35.902 2.097 1.252 3.019 1.414 6.051 1.311 8.197-.002C9.651 12.73 7.101 9.67 5.146 7.191a10.628 10.628 0 0 1-1.005-1.384c2.34 2.142 6.038 4.83 7.365 5.576C8.69 8.408 6.208 4.743 6.324 4.86c4.436 4.47 8.528 6.996 8.528 6.996.154.085.27.154.36.213.085-.215.16-.437.224-.668.708-2.588-.09-5.548-1.893-7.992z"
      />
    </svg>
  )
}

export function FlutterIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" fill="#54c5f8" aria-hidden="true">
      <path d="M15 3 5 13l3.2 3.2L21.4 3H15z" />
      <path d="M15 25h6.4l-6-6-3.2 3.2L15 25z" opacity="0.85" />
      <path d="M8.2 16.2 5 19.4l3.2 3.2 3.2-3.2-3.2-3.2z" opacity="0.6" />
    </svg>
  )
}

// Kotlin Multiplatform's official mark (developer.android.com/static/images/picto-icons/kmp.svg).
export function KmpIcon() {
  return (
    <svg viewBox="0 0 48 48" width="28" height="28" fill="none" aria-hidden="true">
      <defs>
        <radialGradient
          id="platform-kmp"
          cx="0"
          cy="0"
          r="1"
          gradientTransform="rotate(135 20.814 11.259) scale(61.5)"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#37bcfd" />
          <stop offset="0.58" stopColor="#7f52ff" />
          <stop offset="1" stopColor="#c711e1" />
        </radialGradient>
      </defs>
      <path
        fill="url(#platform-kmp)"
        d="M0 22.563V.083l22.48 22.48H0Zm0 2.874V48h.057L22.62 25.437H0Zm25.99-3.428L48 0H3.981l22.01 22.01Zm.03 4.094L4.121 48h43.794L26.02 26.103Z"
      />
    </svg>
  )
}

export function ReactIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" fill="none" stroke="#61dafb" strokeWidth="1.3" aria-hidden="true">
      <circle cx="14" cy="14" r="2.4" fill="#61dafb" stroke="none" />
      <ellipse cx="14" cy="14" rx="11" ry="4.3" />
      <ellipse cx="14" cy="14" rx="11" ry="4.3" transform="rotate(60 14 14)" />
      <ellipse cx="14" cy="14" rx="11" ry="4.3" transform="rotate(120 14 14)" />
    </svg>
  )
}

// Generic globe mark for backend APIs / dynamic websites — not tied to a
// single framework, so no brand colors, just the platform accent.
export function WebIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="platform-web" x1="2" y1="2" x2="26" y2="26" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#38bdf8" />
          <stop offset="1" stopColor="#6366f1" />
        </linearGradient>
      </defs>
      <circle cx="14" cy="14" r="10.5" stroke="url(#platform-web)" strokeWidth="1.6" />
      <path d="M3.5 14h21M14 3.5c-4 0-7 4.7-7 10.5s3 10.5 7 10.5 7-4.7 7-10.5-3-10.5-7-10.5z" stroke="url(#platform-web)" strokeWidth="1.3" />
      <path d="M4.7 9h18.6M4.7 19h18.6" stroke="url(#platform-web)" strokeWidth="1.1" />
    </svg>
  )
}
