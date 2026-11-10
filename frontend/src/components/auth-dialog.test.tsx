import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { login } from '@/api/auth'
import type { UserResponse } from '@/api/types'
import { useAuthDialog } from '@/stores/auth-dialog-store'
import { useAuthStore } from '@/stores/auth-store'

import { AuthDialog } from './auth-dialog'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  loginWithGoogle: vi.fn(),
  requestWhatsAppCode: vi.fn(),
  loginWithWhatsAppCode: vi.fn(),
}))
vi.mock('@/api/branding', async (orig) => ({
  ...(await orig<object>()),
  getBranding: vi.fn(() => Promise.reject(new Error('down'))),
}))
vi.mock('@/lib/use-captcha', () => ({
  useCaptcha: () => ({ enabled: false, token: null, widget: null, reset: () => undefined }),
}))
vi.mock('@/components/social-login', () => ({ SocialLogin: () => null }))

// The registration form's checkbox measures itself with ResizeObserver, which the test DOM does not have.
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
  },
)

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function show(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Where />
        <Routes>
          <Route path="*" element={<AuthDialog />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** What the sign-in call does on success: the session is stored, then the caller decides where to go. */
function signInAs(role: UserResponse['role']) {
  vi.mocked(login).mockImplementation(() => {
    useAuthStore.setState({ status: 'authenticated', user: { id: 1, role, extraRoles: [] } as never, roles: [role] })
    return Promise.resolve({} as never)
  })
}

describe('the sign-in popup', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ status: 'unauthenticated', user: null, roles: [] })
    useAuthDialog.setState({ open: 'login' })
  })

  it('shows the sign-in form over the page, and switches to registering and back', async () => {
    show('/courses')
    expect(await screen.findByLabelText('Email or phone')).toBeTruthy()

    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }))
    expect(await screen.findByLabelText('First name')).toBeTruthy()

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByLabelText('Email or phone')).toBeTruthy()
    expect(screen.getByTestId('where').textContent).toBe('/courses')
  })

  it('a student who signs in on a public page stays on it, with the popup closed', async () => {
    signInAs('STUDENT')
    show('/courses')
    await userEvent.type(await screen.findByLabelText('Email or phone'), 'asha@x.in')
    await userEvent.type(screen.getByLabelText('Password'), 'Secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(useAuthDialog.getState().open).toBeNull())
    expect(screen.getByTestId('where').textContent).toBe('/courses')
  })

  it('staff go to the portal, and so does anyone signing in from the front page', async () => {
    signInAs('ADMIN')
    const first = show('/courses')
    await userEvent.type(await screen.findByLabelText('Email or phone'), 'admin@x.in')
    await userEvent.type(screen.getByLabelText('Password'), 'Secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/app'))
    first.unmount()

    useAuthStore.setState({ status: 'unauthenticated', user: null, roles: [] })
    useAuthDialog.setState({ open: 'login' })
    signInAs('STUDENT')
    show('/')
    await userEvent.type(await screen.findByLabelText('Email or phone'), 'asha@x.in')
    await userEvent.type(screen.getByLabelText('Password'), 'Secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/app'))
  })

  it('the Forgot password link leaves for its page and closes the popup', async () => {
    show('/courses')
    await userEvent.click(await screen.findByRole('link', { name: 'Forgot password?' }))
    await waitFor(() => expect(useAuthDialog.getState().open).toBeNull())
  })
})
