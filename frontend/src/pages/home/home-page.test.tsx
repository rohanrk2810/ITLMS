import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/stores/auth-store'

import { HomePage } from './home-page'

vi.mock('@/api/public-site', () => ({
  getPublicCourses: () => Promise.resolve({ content: [], totalElements: 0, page: 0, size: 12, totalPages: 0, first: true, last: true }),
  sendEnquiry: () => Promise.resolve(),
}))
vi.mock('@/api/branding', async (orig) => ({ ...(await orig<object>()), getBranding: () => Promise.reject(new Error('down')) }))

function renderHome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('HomePage', () => {
  beforeEach(() => useAuthStore.setState({ status: 'unauthenticated' }))

  it('shows the site with a Login button and the fallback name when the server is down', () => {
    renderHome()
    expect(screen.getAllByRole('link', { name: 'Login' }).length).toBeGreaterThan(0)
    expect(screen.getAllByText('SutraVidya').length).toBeGreaterThan(0)
    expect(screen.getByRole('heading', { name: /Talk to a counsellor/ })).toBeTruthy()
  })
})
