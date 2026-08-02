import { useEffect, useState } from 'react'
import { fetchHealth, type HealthStatus } from '@/api/health'

type Status = 'idle' | 'loading' | 'success' | 'error'

interface HealthCheckState {
  status: Status
  health?: HealthStatus
  errorMessage?: string
}

const POLL_INTERVAL_MS = 5_000

/** Drives the header's connection dot (ConnectionStatus.tsx) — polls so it actually notices the backend going down, not just whether it was up at mount. */
export function useHealthCheck(): HealthCheckState {
  const [state, setState] = useState<HealthCheckState>({ status: 'idle' })

  useEffect(() => {
    let cancelled = false
    let isFirstCheck = true

    const check = () => {
      if (isFirstCheck) setState({ status: 'loading' })
      fetchHealth()
        .then((health) => {
          if (!cancelled) setState({ status: 'success', health })
        })
        .catch((error: unknown) => {
          if (!cancelled) {
            setState({
              status: 'error',
              errorMessage: error instanceof Error ? error.message : 'Unknown error',
            })
          }
        })
        .finally(() => {
          isFirstCheck = false
        })
    }

    check()
    const interval = setInterval(check, POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [])

  return state
}
