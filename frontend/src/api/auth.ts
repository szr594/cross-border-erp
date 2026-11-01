import { reactive } from 'vue'

import { ApiError, registerTokenRefresher, requestJson, setAccessToken } from './http'

export interface CurrentUser {
  id: number
  name: string
  loginName: string
  roles: string[]
  permissions: string[]
  mustChangePassword: boolean
}

interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: CurrentUser
}

export const authState = reactive<{
  user: CurrentUser | null
  loaded: boolean
}>({
  user: null,
  loaded: false,
})

async function authRequest(path: string, init: RequestInit = {}): Promise<AuthResponse> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
    credentials: 'same-origin',
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { code?: string; message?: string }
    throw new ApiError(
      body.message ?? `请求失败（${response.status}）`,
      response.status,
      body.code ?? 'AUTH_FAILED',
    )
  }
  return await response.json() as AuthResponse
}

function acceptAuth(response: AuthResponse) {
  setAccessToken(response.accessToken)
  authState.user = response.user
  authState.loaded = true
  return response
}

export async function login(loginName: string, password: string): Promise<CurrentUser> {
  const response = await authRequest('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ loginName, password }),
  })
  return acceptAuth(response).user
}

export async function refreshSession(): Promise<string | null> {
  try {
    const response = acceptAuth(await authRequest('/api/auth/refresh', { method: 'POST' }))
    return response.accessToken
  } catch {
    clearAuth()
    return null
  }
}

export async function ensureAuthenticated(force = false): Promise<CurrentUser | null> {
  if (authState.loaded && !force) {
    return authState.user
  }
  await refreshSession()
  return authState.user
}

export async function logout() {
  try {
    await requestJson<void>('/api/auth/logout', { method: 'POST' }, false)
  } finally {
    clearAuth()
  }
}

export function clearAuth() {
  setAccessToken(null)
  authState.user = null
  authState.loaded = true
}

registerTokenRefresher(refreshSession)

