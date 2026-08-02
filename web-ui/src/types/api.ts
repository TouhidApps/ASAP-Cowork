/** Mirrors chat-gateway's ApiResponse<T> envelope (common/ApiResponse.kt). */
export interface ApiError {
  code: string
  message: string
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiError
}
