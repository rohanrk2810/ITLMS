import { beforeEach, describe, expect, it } from 'vitest'

import type { UserResponse } from '@/api/types'

import { hasRole, rolesOf, useAuthStore } from './auth-store'

const user = { id: 1, fullName: 'Asha Patil', role: 'STUDENT', mustChangePassword: false, extraRoles: [] } as unknown as UserResponse

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
    useAuthStore.setState({ user: null, roles: [], accessToken: null, status: 'checking' })
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

describe('roles held', () => {
  const trainer = { id: 7, fullName: 'Ravi', role: 'TRAINER', extraRoles: ['COORDINATOR', 'FINANCE'] } as UserResponse

  it('lists the main role first, then the extra duties', () => {
    expect(rolesOf(trainer)).toEqual(['TRAINER', 'COORDINATOR', 'FINANCE'])
    expect(rolesOf({ role: 'STUDENT', extraRoles: [] })).toEqual(['STUDENT'])
    expect(rolesOf(null)).toEqual([])
  })

  it('an answer from an older server without the field means no extra duties', () => {
    expect(rolesOf({ role: 'TRAINER' } as UserResponse)).toEqual(['TRAINER'])
  })

  it('never lists the main role twice', () => {
    expect(rolesOf({ role: 'COORDINATOR', extraRoles: ['COORDINATOR', 'FINANCE'] })).toEqual(['COORDINATOR', 'FINANCE'])
  })

  it('is kept in step with the signed-in person', () => {
    useAuthStore.getState().setSession('tok', trainer)
    expect(useAuthStore.getState().roles).toEqual(['TRAINER', 'COORDINATOR', 'FINANCE'])
    useAuthStore.getState().setUser({ ...trainer, extraRoles: [] })
    expect(useAuthStore.getState().roles).toEqual(['TRAINER'])
    useAuthStore.getState().clearSession()
    expect(useAuthStore.getState().roles).toEqual([])
  })

  it('hasRole accepts the whole list: any one of them may be the allowed role', () => {
    expect(hasRole(['TRAINER', 'COORDINATOR'], ['ADMIN', 'COORDINATOR'])).toBe(true)
    expect(hasRole(['TRAINER', 'FINANCE'], ['ADMIN', 'COORDINATOR'])).toBe(false)
    expect(hasRole([], ['ADMIN'])).toBe(false)
    expect(hasRole('TRAINER', ['TRAINER'])).toBe(true)
  })
})
