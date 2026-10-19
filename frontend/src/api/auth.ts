import { apiClient, applySession, clearSession, getStoredRefreshToken, refreshAccessToken } from './client'
import type { ApiMessage, AuthResponse, UserResponse } from './types'

export interface RegisterInput {
  firstName: string
  lastName: string
  email: string
  phone: string
  password: string
}

/** Whether the "verify you are human" check is on, and the public key for its widget. */
export interface CaptchaConfig {
  enabled: boolean
  siteKey: string
}

export async function getCaptchaConfig(): Promise<CaptchaConfig> {
  const { data } = await apiClient.get<CaptchaConfig>('/api/auth/captcha-config')
  return data
}

export async function login(identifier: string, password: string, captchaToken?: string | null): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/api/auth/login', {
    identifier,
    password,
    captchaToken: captchaToken ?? undefined,
  })
  applySession(data)
  return data
}

export async function register(input: RegisterInput, captchaToken?: string | null): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/api/auth/register', {
    ...input,
    captchaToken: captchaToken ?? undefined,
  })
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

export async function forgotPassword(email: string, captchaToken?: string | null): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>('/api/auth/forgot-password', {
    email,
    captchaToken: captchaToken ?? undefined,
  })
  return data
}

export async function resetPassword(email: string, otp: string, newPassword: string): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>('/api/auth/reset-password', { email, otp, newPassword })
  return data
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>('/api/auth/change-password', {
    currentPassword,
    newPassword,
  })
  return data
}
