import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AdminAuthState {
  token: string | null
  setToken: (token: string) => void
  clearToken: () => void
}

/**
 * Persists the admin token to localStorage so a page refresh doesn't sign
 * you out. There's no server-side session — this is a shared secret
 * (ADMIN_TOKEN), not a login — so "logging out" just forgets it locally.
 */
export const useAdminAuth = create<AdminAuthState>()(
  persist(
    (set) => ({
      token: null,
      setToken: (token) => set({ token }),
      clearToken: () => set({ token: null }),
    }),
    { name: 'asap-cowork-admin-auth' },
  ),
)
