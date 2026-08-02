import { Link, NavLink, Outlet } from 'react-router-dom'
import { ConnectionStatus } from '@/components/ConnectionStatus'
import '@/components/layout/appLayout.css'

const navItems = [
  { to: '/', label: 'Chat', end: true },
  { to: '/notes', label: 'Notes', end: false },
  { to: '/admin', label: 'Admin', end: false },
]

export function AppLayout() {
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
        <Outlet />
      </main>
    </div>
  )
}
