import { useHealthCheck } from '@/hooks/useHealthCheck'

export function ConnectionStatus() {
  const { status } = useHealthCheck()

  const label = status === 'success' ? 'Connected' : status === 'error' ? 'Offline' : 'Connecting…'
  const color = status === 'success' ? '#3fb950' : status === 'error' ? '#d94f4f' : '#aa3bff'

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12, opacity: 0.7, flexShrink: 0 }} title={label}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: color, flexShrink: 0 }} />
      {label}
    </span>
  )
}
