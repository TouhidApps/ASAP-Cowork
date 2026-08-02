import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import '@/components/layout/appLayout.css'
import '@/components/layout/adminLayout.css'
import { useAdminAuth } from '@/features/admin/useAdminAuth'

const navItems = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/conversation', label: 'Conversation', end: false },
  { to: '/admin/providers', label: 'Providers', end: false },
  { to: '/admin/settings', label: 'Settings', end: false },
]

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <>
      {navItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={onNavigate}
          className={({ isActive }) => `admin-nav-link${isActive ? ' active' : ''}`}
        >
          {item.label}
        </NavLink>
      ))}
    </>
  )
}

export function AdminLayout() {
  const clearToken = useAdminAuth((state) => state.clearToken)
  const [navOpen, setNavOpen] = useState(false)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: '100svh' }}>
      {/* Same brand block (icon + wordmark) as AppLayout's header — this is
          a separate top-level route (see router.tsx), not nested under
          AppLayout, so it imports appLayout.css itself to reuse those
          classes rather than re-declaring near-duplicate styles here. */}
      <header className="admin-header">
        <div className="admin-header-left">
          <button className="admin-menu-toggle" onClick={() => setNavOpen(true)} aria-label="Open menu">
            ☰
          </button>
          <Link to="/admin" className="app-brand">
            <img src="/icon.png" alt="" className="app-brand-mark" />
            <span className="app-brand-text">
              <h2 className="app-brand-name">ASAP-Cowork</h2>
              <span className="admin-header-subtitle">Admin</span>
            </span>
          </Link>
        </div>
        <div className="admin-header-right">
          <Link to="/" className="admin-back-link" aria-label="Back to chat">
            <span className="admin-back-link-full">Back to chat</span>
            <span className="admin-back-link-short" aria-hidden="true">
              ← Chat
            </span>
          </Link>
          <button onClick={clearToken} className="admin-logout-button">
            Log out
          </button>
        </div>
      </header>

      {navOpen && (
        <>
          <div className="admin-nav-backdrop" onClick={() => setNavOpen(false)} />
          <aside className="admin-nav-drawer">
            <div className="admin-nav-drawer-header">
              <h3>Menu</h3>
              <button className="admin-nav-drawer-close" onClick={() => setNavOpen(false)} aria-label="Close menu">
                ×
              </button>
            </div>
            <NavLinks onNavigate={() => setNavOpen(false)} />
          </aside>
        </>
      )}

      <div className="admin-body">
        <nav className="admin-nav">
          <NavLinks />
        </nav>
        <main className="admin-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
