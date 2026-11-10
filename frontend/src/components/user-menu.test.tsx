import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ProtectedRoute } from '@/routes/protected-route'
import { useAuthStore } from '@/stores/auth-store'

import { UserMenu } from './user-menu'

// What the real logout does first: the session is gone before the call goes to the server.
vi.mock('@/api/auth', () => ({
  logout: vi.fn(async () => {
    useAuthStore.getState().clearSession()
    await Promise.resolve()
  }),
}))

describe('signing out', () => {
  beforeEach(() => {
    useAuthStore.setState({
      status: 'authenticated',
      user: { id: 1, fullName: 'Asha Patil', email: 'a@x.in', role: 'STUDENT', extraRoles: [] } as never,
      roles: ['STUDENT'],
    })
  })

  it('lands on the front page, not the sign-in page', async () => {
    render(
      <MemoryRouter initialEntries={['/app']}>
        <Routes>
          <Route path="/" element={<p>Front page</p>} />
          <Route path="/login" element={<p>Sign-in page</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/app" element={<UserMenu />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    await userEvent.click(screen.getByRole('button', { name: /AP/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: /Sign out/ }))

    expect(await screen.findByText('Front page')).toBeTruthy()
    await waitFor(() => expect(screen.queryByText('Sign-in page')).toBeNull())
    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })
})
