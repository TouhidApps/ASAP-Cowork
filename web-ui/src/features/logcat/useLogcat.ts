import { useCallback, useEffect, useState } from 'react'
import { API_BASE_URL } from '@/api/client'
import { fetchLogcatInfo } from '@/features/logcat/api'

const MAX_LINES = 2_000

export type LogcatScope = 'app' | 'all'
export type LogcatLevel = 'V' | 'D' | 'I' | 'W' | 'E'

interface LogcatState {
  connected: boolean
  error: string | null
  lines: string[]
}

interface LogcatInfoState {
  deviceName: string | null
  packageName: string | null
}

/**
 * Streams /api/v1/logcat/stream over a single `fetch` with a read-as-it-arrives
 * body, rather than the browser's native `EventSource`. `scope` ('app', the
 * default, vs 'all') and `level` (minimum priority, unset shows everything)
 * reopen the stream when changed — mirrors LogcatRoutes.kt's query params —
 * so switching a filter doesn't require closing and reopening the panel by
 * hand.
 *
 * This used to open a throwaway `fetch` "probe" first (to read a failure
 * response's JSON body, since EventSource can't inspect a non-200 response
 * at all), abort it, then open a second, real `EventSource` connection to
 * the same URL. That meant two requests to the same stateful, PID-resolving
 * endpoint in quick succession — fragile through any proxy that pools/reuses
 * keep-alive connections (e.g. the Vite dev proxy), and reported as a
 * generic "Lost connection to the backend" with "App only" scope selected,
 * which needs `adb shell pidof` to resolve a PID and is the one case where
 * the two requests aren't trivially idempotent. A single fetch with a
 * streamed body reads both the success/failure status *and* the log lines
 * over the exact same connection — no second request, so no race between
 * them.
 */
export function useLogcat(active: boolean, scope: LogcatScope, level: LogcatLevel | null) {
  const [state, setState] = useState<LogcatState>({ connected: false, error: null, lines: [] })
  const [info, setInfo] = useState<LogcatInfoState>({ deviceName: null, packageName: null })

  // Separate from the stream effect below (keyed only on `active`, not
  // scope/level) so switching a filter doesn't spam this best-effort lookup
  // — the device/app label doesn't depend on which lines are being shown.
  useEffect(() => {
    if (!active) {
      setInfo({ deviceName: null, packageName: null })
      return
    }
    let cancelled = false
    fetchLogcatInfo()
      .then((data) => {
        if (!cancelled) setInfo({ deviceName: data.deviceName, packageName: data.packageName })
      })
      .catch(() => {
        // Non-critical — the header just won't show a device/app label.
      })
    return () => {
      cancelled = true
    }
  }, [active])

  useEffect(() => {
    if (!active) {
      setState((s) => ({ ...s, connected: false }))
      return
    }

    let cancelled = false
    const controller = new AbortController()
    const params = new URLSearchParams({ scope })
    if (level) params.set('level', level)
    const url = `${API_BASE_URL}/api/v1/logcat/stream?${params}`

    setState({ connected: false, error: null, lines: [] })

    const run = async () => {
      let response: Response
      try {
        response = await fetch(url, { signal: controller.signal })
      } catch (e: unknown) {
        if (cancelled || controller.signal.aborted) return
        setState((s) => ({ ...s, error: e instanceof Error ? e.message : 'Failed to connect to logcat.' }))
        return
      }
      if (cancelled) return

      if (!response.ok || !response.body) {
        const body = (await response.json().catch(() => null)) as { error?: { message?: string } } | null
        setState((s) => ({ ...s, error: body?.error?.message ?? `Failed to connect (${response.status}).` }))
        return
      }

      setState((s) => ({ ...s, connected: true, error: null }))

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      try {
        while (!cancelled) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          // SSE frames are separated by a blank line; only "data: <json>"
          // frames carry a log line — the ": keep-alive" heartbeat comment
          // frames are skipped since they have no such line.
          let boundary = buffer.indexOf('\n\n')
          while (boundary !== -1) {
            const frame = buffer.slice(0, boundary)
            buffer = buffer.slice(boundary + 2)
            boundary = buffer.indexOf('\n\n')

            const dataLine = frame.split('\n').find((l) => l.startsWith('data: '))
            if (!dataLine) continue
            let parsed: { line?: unknown }
            try {
              parsed = JSON.parse(dataLine.slice('data: '.length)) as { line?: unknown }
            } catch {
              continue
            }
            if (typeof parsed.line !== 'string') continue
            const nextLine = parsed.line
            setState((s) => ({ ...s, connected: true, lines: [...s.lines, nextLine].slice(-MAX_LINES) }))
          }
        }
        if (!cancelled) {
          setState((s) => ({ ...s, connected: false, error: 'Log stream ended — reopen the panel or change a filter to reconnect.' }))
        }
      } catch (e: unknown) {
        if (cancelled || controller.signal.aborted) return
        setState((s) => ({ ...s, connected: false, error: e instanceof Error ? e.message : 'Lost connection to the backend.' }))
      }
    }

    run()

    return () => {
      cancelled = true
      controller.abort()
    }
  }, [active, scope, level])

  const clear = useCallback(() => {
    setState((s) => ({ ...s, lines: [] }))
  }, [])

  return { ...state, ...info, clear }
}
