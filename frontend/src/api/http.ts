let accessToken: string | null = null
let refreshAccessToken: (() => Promise<string | null>) | null = null
let refreshInFlight: Promise<string | null> | null = null

export interface ApiErrorBody {
  code?: string
  message?: string
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
  ) {
    super(message)
  }
}

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function registerTokenRefresher(refresher: () => Promise<string | null>) {
  refreshAccessToken = refresher
}

export async function requestJson<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (response.status === 401 && retry && refreshAccessToken && path !== '/api/auth/refresh') {
    refreshInFlight ??= refreshAccessToken().finally(() => {
      refreshInFlight = null
    })
    const refreshedToken = await refreshInFlight
    if (refreshedToken) {
      return requestJson<T>(path, init, false)
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ApiErrorBody
    throw new ApiError(
      body.message ?? `请求失败（${response.status}）`,
      response.status,
      body.code ?? 'REQUEST_FAILED',
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return await response.json() as T
}

