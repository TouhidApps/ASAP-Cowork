import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { RequireAdminAuth } from '@/features/admin/components/RequireAdminAuth'
import { AdminLoginPage } from '@/pages/AdminLoginPage'
import { AdminDashboardPage } from '@/pages/AdminDashboardPage'
import { AdminConversationPage } from '@/pages/AdminConversationPage'
import { AdminProvidersPage } from '@/pages/AdminProvidersPage'
import { AdminUsagePage } from '@/pages/AdminUsagePage'
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage'
import { EmailPage } from '@/pages/EmailPage'

/**
 * `/` and `/notes` are registered here only so the URL and NavLink active
 * state track the current tab — AppLayout renders ChatPage and NotesPage
 * itself (both stay mounted, toggled via CSS) rather than through these
 * children's elements, so switching tabs doesn't unmount either one.
 * Admin's children (including `tools/email`) use ordinary <Outlet/>
 * swapping instead — each admin page mounts fresh on navigation, which is
 * fine since none of them hold a live connection worth preserving.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [{ index: true }, { path: 'notes' }],
  },
  { path: '/admin/login', element: <AdminLoginPage /> },
  {
    path: '/admin',
    element: <RequireAdminAuth />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          { index: true, element: <AdminDashboardPage /> },
          { path: 'conversation', element: <AdminConversationPage /> },
          { path: 'providers', element: <AdminProvidersPage /> },
          { path: 'usage', element: <AdminUsagePage /> },
          { path: 'settings', element: <AdminWorkspacePage /> },
          { path: 'tools/email', element: <EmailPage /> },
        ],
      },
    ],
  },
])
