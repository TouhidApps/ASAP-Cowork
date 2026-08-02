import { Navigate, Outlet } from 'react-router-dom'
import { useAdminAuth } from '@/features/admin/useAdminAuth'

export function RequireAdminAuth() {
  const token = useAdminAuth((state) => state.token)
  if (!token) return <Navigate to="/admin/login" replace />
  return <Outlet />
}
