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

export function AndroidIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" fill="#3ddc84" aria-hidden="true">
      <path d="M8.5 11.5v7a1.3 1.3 0 0 0 2.6 0v-7h-2.6zM17 11.5v7a1.3 1.3 0 0 0 2.6 0v-7H17zM11.5 11.5v9.2a1.4 1.4 0 0 0 2.8 0v-3h.4v3a1.4 1.4 0 0 0 2.8 0v-9.2h-6z" />
      <path d="M11.6 9.8h4.9c-.1-1.6-1.2-2.9-2.4-2.9s-2.4 1.3-2.5 2.9z" />
      <rect x="11.2" y="9.8" width="5.6" height="1.1" rx="0.3" />
      <circle cx="12.3" cy="6.1" r="0.55" />
      <circle cx="15.7" cy="6.1" r="0.55" />
      <path d="M9.5 6l1.3 1.6M18.5 6l-1.3 1.6" stroke="#3ddc84" strokeWidth="0.7" strokeLinecap="round" fill="none" />
    </svg>
  )
}

export function AppleIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" fill="currentColor" aria-hidden="true">
      <path d="M18.4 8.1c-1 0-2.3.6-3 .6-.8 0-1.9-.6-3.1-.6-2.3 0-4.6 1.9-4.6 5.5 0 2.3.9 4.7 2 6.3.9 1.4 1.7 2.5 2.9 2.5 1.1 0 1.6-.7 2.9-.7s1.7.7 2.9.7c1.2 0 2-1.1 2.8-2.3.6-.9 1-1.7 1.3-2.6-3.2-1.2-3.4-5.4-.3-7.1-.9-1.3-2.3-2.1-3.8-2.3z" />
      <path d="M15.9 6.9c.6-.7 1-1.7.9-2.7-.9.1-1.9.6-2.5 1.3-.5.6-1 1.6-.9 2.6 1 .1 2-.5 2.5-1.2z" />
    </svg>
  )
}

export function SwiftIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" aria-hidden="true">
      <defs>
        <linearGradient id="platform-swift" x1="4" y1="6" x2="24" y2="22" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#fa8c3c" />
          <stop offset="1" stopColor="#f0512a" />
        </linearGradient>
      </defs>
      <path
        d="M6 6c4.5 2.5 9 5.6 12.5 9.3-3.8-1.8-7.6-3.3-11-5.5.9 1.3 2 2.6 3.3 3.9C8 12.4 5.7 9.4 6 6z"
        fill="url(#platform-swift)"
      />
      <path
        d="M19.5 16.4c.6-2.2-.1-4.9-1.5-7.1 1.7 1.5 3.2 3.6 3.8 6 .5 2 .3 4.3-1.1 5.7-2 2-5.4 2-8.4 1a13.6 13.6 0 0 1-6.3-4.4c1.7 1.1 3.7 1.9 5.8 2.1 3 .3 6.7-.6 7.7-3.3z"
        fill="url(#platform-swift)"
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

export function KmpIcon() {
  return (
    <svg viewBox="0 0 28 28" width="28" height="28" aria-hidden="true">
      <defs>
        <linearGradient id="platform-kmp" x1="3" y1="3" x2="25" y2="25" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#c711e1" />
          <stop offset="1" stopColor="#7f52ff" />
        </linearGradient>
      </defs>
      <rect x="3" y="3" width="14" height="14" rx="4" fill="url(#platform-kmp)" opacity="0.55" />
      <rect x="11" y="11" width="14" height="14" rx="4" fill="url(#platform-kmp)" />
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
