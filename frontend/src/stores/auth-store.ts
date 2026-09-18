import { create } from 'zustand'

import type { Role, UserResponse } from '@/api/types'

/**
 * The access token lives here, in memory only - never in localStorage,
 * so a script that can run in this page cannot read it back out of storage
 * after the fact. Only the longer-lived refresh token is persisted (see
 * api/client.ts), which is the usual trade-off for a JWT SPA with no
 * backend-for-frontend to hold an httpOnly cookie instead.
 */
export type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated'

interface AuthState {
  user: UserResponse | null
  accessToken: string | null
  status: AuthStatus
  setSession: (accessToken: string, user: UserResponse) => void
  setUser: (user: UserResponse) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  status: 'checking',
  setSession: (accessToken, user) => set({ accessToken, user, status: 'authenticated' }),
  setUser: (user) => set({ user }),
  clearSession: () => set({ accessToken: null, user: null, status: 'unauthenticated' }),
}))

export function hasRole(role: Role | undefined, allowed: readonly Role[]): boolean {
  return !!role && allowed.includes(role)
}
