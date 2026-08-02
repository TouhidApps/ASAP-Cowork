import { useEffect, useState } from 'react'
import { fetchStatus } from '@/features/admin/api'
import type { SystemStatus } from '@/features/admin/types'

type Status = 'idle' | 'loading' | 'success' | 'error'

interface StatusState {
  status: Status
  data?: SystemStatus
  errorMessage?: string
}

export function useAdminStatus(pollMs = 5000): StatusState {
  const [state, setState] = useState<StatusState>({ status: 'idle' })

  useEffect(() => {
    let cancelled = false

    const load = () => {
      fetchStatus()
        .then((data) => {
          if (!cancelled) setState({ status: 'success', data })
        })
        .catch((error: unknown) => {
          if (!cancelled) {
            setState({
              status: 'error',
              errorMessage: error instanceof Error ? error.message : 'Unknown error',
            })
          }
        })
    }

    setState((s) => ({ ...s, status: 'loading' }))
    load()
    const interval = setInterval(load, pollMs)

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [pollMs])

  return state
}
