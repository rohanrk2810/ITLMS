import { apiClient, applySession, clearSession, getStoredRefreshToken, refreshAccessToken } from './client'
import type { ApiMessage, AuthResponse, UserResponse } from './types'

export interface RegisterInput {
  firstName: string
  lastName: string
  email: string
  phone: string
  password: string
}

export async function login(identifier: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/api/auth/login', { identifier, password })
  applySession(data)
  return data
}

export async function register(input: RegisterInput): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/api/auth/register', input)
  applySession(data)
  return data
}

/** Called once when the app loads, so a refresh token from a previous visit signs the user back in silently. */
export async function bootstrapSession(): Promise<void> {
  if (!getStoredRefreshToken()) {
    clearSession()
    return
  }
  const accessToken = await refreshAccessToken()
  if (!accessToken) {
    clearSession()
  }
}

export async function logout(): Promise<void> {
  const refreshToken = getStoredRefreshToken()
  clearSession()
  if (refreshToken) {
    // Best-effort: the session is already gone client-side either way.
    await apiClient.post('/api/auth/logout', { refreshToken }).catch(() => undefined)
  }
}

export async function fetchCurrentUser(): Promise<UserResponse> {
  const { data } = await apiClient.get<UserResponse>('/api/auth/me')
  return data
}

export async function forgotPassword(email: string): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>('/api/auth/forgot-password', { email })
  return data
}

export async function resetPassword(token: string, newPassword: string): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>('/api/auth/reset-password', { token, newPassword })
  return data
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>('/api/auth/change-password', {
    currentPassword,
    newPassword,
  })
  return data
}
