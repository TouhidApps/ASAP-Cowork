import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { RequireAdminAuth } from '@/features/admin/components/RequireAdminAuth'
import { ChatPage } from '@/pages/ChatPage'
import { NotesPage } from '@/pages/NotesPage'
import { AdminLoginPage } from '@/pages/AdminLoginPage'
import { AdminDashboardPage } from '@/pages/AdminDashboardPage'
import { AdminConversationPage } from '@/pages/AdminConversationPage'
import { AdminProvidersPage } from '@/pages/AdminProvidersPage'
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage'

/**
 * Add new feature pages here, nested under AppLayout. Once a feature grows
 * routes of its own, give it a nested route group: { path: 'chat', children: [...] }.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <ChatPage /> },
      { path: 'notes', element: <NotesPage /> },
    ],
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
          { path: 'settings', element: <AdminWorkspacePage /> },
        ],
      },
    ],
  },
])
