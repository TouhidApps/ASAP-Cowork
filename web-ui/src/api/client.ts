import type { ApiResponse } from '@/types/api'
import { useAdminAuth } from '@/features/admin/useAdminAuth'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiClientError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

/**
 * Thin fetch wrapper feature modules build their calls on top of, e.g.
 * `apiGet<SystemStatus>('/api/v1/admin/status')`. Keeps base URL, JSON
 * parsing, and error unwrapping in one place. Attaches the admin token when
 * one is stored — harmless for non-admin routes, required for
 * `/api/v1/admin/*`. Defaults to a relative base URL so Vite's dev proxy (or
 * a same-origin production deploy) handles routing without CORS.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const adminToken = useAdminAuth.getState().token

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      // A FormData body needs the browser to set its own multipart
      // boundary in Content-Type — setting it ourselves would break it.
      ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
      ...init?.headers,
    },
  })

  const body = (await response.json()) as ApiResponse<T>

  if (!response.ok || !body.success) {
    const error = body.error ?? { code: 'UNKNOWN', message: 'Request failed' }
    throw new ApiClientError(error.code, error.message, response.status)
  }

  return body.data as T
}

export function apiGet<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' })
}

export function apiPost<T>(path: string, payload: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: JSON.stringify(payload) })
}

export function apiPut<T>(path: string, payload: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body: JSON.stringify(payload) })
}

export function apiDelete<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}

export function apiPostForm<T>(path: string, formData: FormData): Promise<T> {
  return request<T>(path, { method: 'POST', body: formData })
}
