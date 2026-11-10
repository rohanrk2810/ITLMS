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
  /** The main role and every extra duty, in one list. Pages that decide what to show read this, not `user.role`. */
  roles: Role[]
  accessToken: string | null
  status: AuthStatus
  /** Set when the person chose Sign out, so the portal sends them to the front page and not the sign-in page. */
  signedOutByUser: boolean
  setSession: (accessToken: string, user: UserResponse) => void
  setUser: (user: UserResponse) => void
  clearSession: () => void
}

/** The main role first, then the extra duties; empty when nobody is signed in. */
export function rolesOf(user: Pick<UserResponse, 'role' | 'extraRoles'> | null | undefined): Role[] {
  return user ? [user.role, ...(user.extraRoles ?? []).filter((r) => r !== user.role)] : []
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  roles: [],
  accessToken: null,
  status: 'checking',
  signedOutByUser: false,
  setSession: (accessToken, user) =>
    set({ accessToken, user, roles: rolesOf(user), status: 'authenticated', signedOutByUser: false }),
  setUser: (user) => set({ user, roles: rolesOf(user) }),
  clearSession: () => set({ accessToken: null, user: null, roles: [], status: 'unauthenticated' }),
}))

/**
 * Whether someone holds any of the allowed roles. Pass the full list (`state.roles`), so a person with an extra duty
 * (a trainer who also coordinates) gets what that duty allows; a single role string still works.
 */
export function hasRole(held: Role | readonly Role[] | undefined, allowed: readonly Role[]): boolean {
  if (!held) return false
  const roles = typeof held === 'string' ? [held] : held
  return roles.some((r) => allowed.includes(r))
}
