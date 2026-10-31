import axios, { type InternalAxiosRequestConfig } from 'axios'

import { env } from '@/lib/env'
import { useAuthStore } from '@/stores/auth-store'

import type { ApiErrorResponse, AuthResponse } from './types'

const REFRESH_TOKEN_KEY = 'itilms.refreshToken'

export function getStoredRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

function storeRefreshToken(token: string | null) {
  try {
    if (token) {
      localStorage.setItem(REFRESH_TOKEN_KEY, token)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  } catch {
    // Private browsing or storage blocked: the session just won't survive a reload.
  }
}

/** Every request goes through the gateway (Doc S9); no service is ever addressed directly. */
export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

let silentRefreshTimer: ReturnType<typeof setTimeout> | undefined

/**
 * Stores a fresh session (from login, register or a refresh) and arms the
 * next silent refresh at 80% of the access token's life - early enough that
 * a slow network still finishes before the old token expires, so a signed-in
 * user in an open tab is very rarely the one who discovers the token is
 * stale; the 401-triggered refresh below is the fallback for everyone else.
 */
export function applySession(auth: AuthResponse) {
  useAuthStore.getState().setSession(auth.accessToken, auth.user)
  storeRefreshToken(auth.refreshToken)

  if (silentRefreshTimer) {
    clearTimeout(silentRefreshTimer)
  }
  const delayMs = Math.max(auth.expiresIn * 0.8 * 1000, 10_000)
  silentRefreshTimer = setTimeout(() => {
    void refreshAccessToken()
  }, delayMs)
}

export function clearSession() {
  if (silentRefreshTimer) {
    clearTimeout(silentRefreshTimer)
    silentRefreshTimer = undefined
  }
  storeRefreshToken(null)
  useAuthStore.getState().clearSession()
}

// A single in-flight refresh is shared by every caller that needs one at
// once (bootstrap, the scheduled timer, a 401 from several parallel
// requests), so a page that fires five queries on mount does not race five
// refresh calls against the same rotating refresh token - reusing an
// already-used one revokes the whole session (Doc S12).
export const SIGNED_OUT_NOTICE_KEY = 'itilms.signedOutNotice'

let refreshPromise: Promise<string | null> | null = null

export function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise
  }
  const refreshToken = getStoredRefreshToken()
  if (!refreshToken) {
    return Promise.resolve(null)
  }

  refreshPromise = axios
    .post<AuthResponse>(`${env.apiBaseUrl}/api/auth/refresh`, { refreshToken })
    .then(({ data }) => {
      applySession(data)
      return data.accessToken
    })
    .catch((error: unknown) => {
      // Ended because the account signed in on another device: leave the reason for the
      // login page to show, rather than dropping the user there with no explanation.
      if (apiErrorMessage(error, '').includes('another device')) {
        try {
          sessionStorage.setItem(SIGNED_OUT_NOTICE_KEY, apiErrorMessage(error, ''))
        } catch {
          /* storage unavailable: the plain login page is still correct */
        }
      }
      clearSession()
      return null
    })
    .finally(() => {
      refreshPromise = null
    })
  return refreshPromise
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError<ApiErrorResponse>(error) || !error.config) {
      return Promise.reject(error)
    }
    const config = error.config as RetryableConfig
    const isAuthEndpoint = config.url?.includes('/api/auth/login') || config.url?.includes('/api/auth/refresh')

    // The server says which fields it refused: let the page turn them red (components/field-error-highlighter.tsx).
    const refused = error.response?.data?.fieldErrors
    if (error.response?.status === 400 && refused && Object.keys(refused).length > 0) {
      window.dispatchEvent(new CustomEvent(API_FIELD_ERRORS_EVENT, { detail: refused }))
    }

    if (error.response?.status === 401 && !config._retried && !isAuthEndpoint) {
      config._retried = true
      const newToken = await refreshAccessToken()
      if (newToken) {
        config.headers.set('Authorization', `Bearer ${newToken}`)
        return apiClient(config)
      }
    }
    return Promise.reject(error)
  },
)

/** Sent on `window` when a request is refused for particular fields: `detail` maps field name to the reason. */
export const API_FIELD_ERRORS_EVENT = 'itilms:field-errors'

/** "durationMinutes" or "answers[0].questionText" as words a person reads: "Duration minutes", "Question text". */
export function fieldLabel(field: string): string {
  const last = (field.split('.').pop() ?? field).replace(/\[\d+\]/g, '')
  const words = last.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase()
  return words === '' ? field : words.charAt(0).toUpperCase() + words.slice(1)
}

/** The message worth showing a user, from any error this client can throw. */
export function apiErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const data = error.response?.data
    const fields = Object.entries(data?.fieldErrors ?? {})
    if (fields.length > 0) {
      // Not "one or more fields are invalid" but which ones, so nobody has to hunt for them.
      const shown = fields.slice(0, 4).map(([field, why]) => `${fieldLabel(field)}: ${why}`)
      const more = fields.length > 4 ? ` (and ${fields.length - 4} more)` : ''
      return `Please complete or correct: ${shown.join('; ')}${more}`
    }
    return data?.message ?? fallback
  }
  return fallback
}
