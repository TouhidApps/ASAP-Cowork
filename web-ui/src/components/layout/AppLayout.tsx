import { Link, NavLink, useLocation } from 'react-router-dom'
import { ConnectionStatus } from '@/components/ConnectionStatus'
import { ChatPage } from '@/pages/ChatPage'
import { NotesPage } from '@/pages/NotesPage'
import '@/components/layout/appLayout.css'

const navItems = [
  { to: '/', label: 'Chat', end: true },
  { to: '/notes', label: 'Notes', end: false },
  { to: '/admin', label: 'Admin', end: false },
]

export function AppLayout() {
  const location = useLocation()
  const isNotes = location.pathname === '/notes'

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      <header className="app-header">
        <Link to="/" className="app-brand">
          <img src="/icon.png" alt="" className="app-brand-mark" />
          <span className="app-brand-text">
            <h2 className="app-brand-name">ASAP-Cowork</h2>
            <ConnectionStatus />
          </span>
        </Link>

        <nav className="app-nav">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main style={{ flex: 1, padding: '12px 24px 24px', display: 'flex', minHeight: 0 }}>
        {/* Both tabs stay mounted and are only hidden via CSS, rather than
            swapped through an <Outlet/>, so switching tabs doesn't unmount
            ChatPage — that would tear down its WebSocket connection and
            reset the chat scroll position every time. */}
        <div className="app-tab-pane" hidden={isNotes}>
          <ChatPage />
        </div>
        <div className="app-tab-pane" hidden={!isNotes}>
          <NotesPage />
        </div>
      </main>
    </div>
  )
}
