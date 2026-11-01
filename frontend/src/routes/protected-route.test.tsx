import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'

import type { Role, UserResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth-store'

import { ProtectedRoute } from './protected-route'

function user(role: Role, mustChangePassword = false, extraRoles: Role[] = []) {
  return { id: 1, fullName: 'Test User', role, mustChangePassword, extraRoles } as UserResponse
}

function renderAt(path: string, roles?: readonly Role[]) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/app/forbidden" element={<div>forbidden page</div>} />
        <Route path="/app/change-password" element={<div>change password page</div>} />
        <Route element={<ProtectedRoute roles={roles} />}>
          <Route path="/app/secret" element={<div>secret page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, roles: [], accessToken: null, status: 'checking' })
  })

  it('waits while the session is being checked, without bouncing anyone to the login page', () => {
    renderAt('/app/secret')
    expect(screen.getByText('Signing you in...')).toBeInTheDocument()
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
  })

  it('sends someone who is not signed in to the login page', () => {
    useAuthStore.setState({ status: 'unauthenticated' })
    renderAt('/app/secret')
    expect(screen.getByText('login page')).toBeInTheDocument()
  })

  it('lets a signed-in user in when no role is asked for', () => {
    useAuthStore.getState().setSession('token', user('STUDENT'))
    renderAt('/app/secret')
    expect(screen.getByText('secret page')).toBeInTheDocument()
  })

  it('lets in a role on the list', () => {
    useAuthStore.getState().setSession('token', user('COORDINATOR'))
    renderAt('/app/secret', ['ADMIN', 'COORDINATOR'])
    expect(screen.getByText('secret page')).toBeInTheDocument()
  })

  it('sends a role that is not on the list to the forbidden page', () => {
    useAuthStore.getState().setSession('token', user('STUDENT'))
    renderAt('/app/secret', ['ADMIN'])
    expect(screen.getByText('forbidden page')).toBeInTheDocument()
    expect(screen.queryByText('secret page')).not.toBeInTheDocument()
  })

  it('makes a temporary password be changed before anything else opens', () => {
    useAuthStore.getState().setSession('token', user('ADMIN', true))
    renderAt('/app/secret', ['ADMIN'])
    expect(screen.getByText('change password page')).toBeInTheDocument()
  })

  it('lets a trainer in to a coordinator page through an extra duty', () => {
    useAuthStore.getState().setSession('token', user('TRAINER', false, ['COORDINATOR']))
    renderAt('/app/secret', ['ADMIN', 'COORDINATOR'])
    expect(screen.getByText('secret page')).toBeInTheDocument()
  })

  it('still lets that trainer in to a trainer page', () => {
    useAuthStore.getState().setSession('token', user('TRAINER', false, ['COORDINATOR']))
    renderAt('/app/secret', ['TRAINER'])
    expect(screen.getByText('secret page')).toBeInTheDocument()
  })

  it('an extra duty of another department does not open a page it does not cover', () => {
    useAuthStore.getState().setSession('token', user('TRAINER', false, ['FINANCE']))
    renderAt('/app/secret', ['ADMIN', 'COORDINATOR'])
    expect(screen.getByText('forbidden page')).toBeInTheDocument()
  })

  it('takes the extra duty away the moment the person is renewed without it', () => {
    useAuthStore.getState().setSession('token', user('TRAINER', false, ['COORDINATOR']))
    useAuthStore.getState().setUser(user('TRAINER'))
    renderAt('/app/secret', ['ADMIN', 'COORDINATOR'])
    expect(screen.getByText('forbidden page')).toBeInTheDocument()
  })
})
