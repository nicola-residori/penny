/** Shared API client. Relative URLs are proxied by Vite in local development. */
export type ApiError = {
  code: string
  message: string
  timestamp: string
}

export async function apiGet<T>(path: `/api/${string}`): Promise<T> {
  const response = await fetch(path, { credentials: 'same-origin' })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return (await response.json()) as T
}
