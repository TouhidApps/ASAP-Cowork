import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAdminAuth } from '@/features/admin/useAdminAuth'

export function AdminLoginPage() {
  const token = useAdminAuth((state) => state.token)
  const setToken = useAdminAuth((state) => state.setToken)
  const [value, setValue] = useState('')

  if (token) return <Navigate to="/admin" replace />

  return (
    <div
      style={{
        display: 'flex',
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100svh',
        padding: 16,
      }}
    >
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (value.trim()) setToken(value.trim())
        }}
        style={{ display: 'flex', flexDirection: 'column', gap: 12, width: '100%', maxWidth: 320 }}
      >
        <h2>Admin sign in</h2>
        <p style={{ fontSize: 14 }}>Enter the ADMIN_TOKEN configured on the backend.</p>
        <input
          type="password"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="Admin token"
          autoFocus
          style={{
            padding: '10px 12px',
            borderRadius: 6,
            border: '1px solid var(--border)',
            background: 'var(--bg)',
            color: 'var(--text-h)',
          }}
        />
        <button
          type="submit"
          disabled={!value.trim()}
          style={{
            padding: '10px 12px',
            borderRadius: 6,
            border: 'none',
            background: 'var(--accent)',
            color: '#fff',
            cursor: 'pointer',
          }}
        >
          Continue
        </button>
      </form>
    </div>
  )
}
