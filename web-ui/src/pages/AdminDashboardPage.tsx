import { Link } from 'react-router-dom'
import { useAdminStatus } from '@/features/admin/useAdminStatus'

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / 1024 ** exponent
  return `${exponent === 0 ? value : value.toFixed(1)} ${units[exponent]}`
}

function formatUptime(totalSeconds: number): string {
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (days > 0) return `${days}d ${hours}h ${minutes}m`
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${seconds}s`
  return `${seconds}s`
}

export function AdminDashboardPage() {
  const { status, data, errorMessage } = useAdminStatus()

  if (status === 'error') {
    return <p style={{ color: '#d94f4f' }}>Failed to load status: {errorMessage}</p>
  }

  if (!data) {
    return <p>Loading…</p>
  }

  const tiles = [
    { label: 'Status', value: data.status },
    { label: 'Uptime', value: formatUptime(data.uptimeSeconds) },
    { label: 'Memory', value: `${data.memoryUsedMb} / ${data.memoryMaxMb} MB` },
    { label: 'Active provider', value: data.activeProvider },
    { label: 'Conversation messages', value: String(data.conversationMessageCount) },
    { label: 'Database (SQLite)', value: formatBytes(data.databaseSizeBytes) },
    ...data.storage.categories.map((category) => ({
      label: category.label,
      value: `${category.fileCount} file${category.fileCount === 1 ? '' : 's'}, ${formatBytes(category.totalBytes)}`,
    })),
  ]

  return (
    <div>
      <h2>Dashboard</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 16 }}>
        {tiles.map((tile) => (
          <div key={tile.label} style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 16 }}>
            <div style={{ fontSize: 13, opacity: 0.7 }}>{tile.label}</div>
            <div style={{ fontSize: 20, marginTop: 4, color: 'var(--text-h)' }}>{tile.value}</div>
          </div>
        ))}
      </div>
      <p style={{ fontSize: 13, opacity: 0.7, marginTop: 16 }}>
        Manage the workspace directory on the <Link to="/admin/settings">Settings</Link> page.
      </p>
    </div>
  )
}
