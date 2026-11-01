import { beforeEach, describe, expect, it } from 'vitest'

import type { UserResponse } from '@/api/types'

import { hasRole, useAuthStore } from './auth-store'

const user = { id: 1, fullName: 'Asha Patil', role: 'STUDENT', mustChangePassword: false } as UserResponse

describe('hasRole', () => {
  it('is true only for a listed role', () => {
    expect(hasRole('ADMIN', ['ADMIN', 'COORDINATOR'])).toBe(true)
    expect(hasRole('STUDENT', ['ADMIN', 'COORDINATOR'])).toBe(false)
  })

  it('is false for nobody', () => {
    expect(hasRole(undefined, ['ADMIN'])).toBe(false)
  })
})

describe('auth store', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, accessToken: null, status: 'checking' })
  })

  it('starts by checking the session', () => {
    expect(useAuthStore.getState().status).toBe('checking')
  })

  it('a session keeps the token and the user, and the token is only in memory', () => {
    useAuthStore.getState().setSession('tok', user)
    const state = useAuthStore.getState()
    expect(state.status).toBe('authenticated')
    expect(state.accessToken).toBe('tok')
    expect(state.user?.fullName).toBe('Asha Patil')
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('clearing the session signs everyone out', () => {
    useAuthStore.getState().setSession('tok', user)
    useAuthStore.getState().clearSession()
    const state = useAuthStore.getState()
    expect(state.status).toBe('unauthenticated')
    expect(state.accessToken).toBeNull()
    expect(state.user).toBeNull()
  })
})
