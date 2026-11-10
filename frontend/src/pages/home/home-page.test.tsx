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
vi.mock('@/api/branding', async (orig) => ({
  ...(await orig<object>()),
  getBranding: vi.fn(() => Promise.reject(new Error('down'))),
  getSiteConfig: vi.fn(() => Promise.reject(new Error('down'))),
}))

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
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ status: 'unauthenticated' })
  })

  it('shows the site with a Login button and the fallback name when the server is down', () => {
    renderHome()
    expect(screen.getAllByRole('button', { name: 'Login' }).length).toBeGreaterThan(0)
    expect(screen.getAllByText('SutraVidya').length).toBeGreaterThan(0)
    expect(screen.getByRole('heading', { name: /Talk to a counsellor/ })).toBeTruthy()
  })

  it('uses the wording the administrator saved, shows real feedback and leaves hidden sections out', async () => {
    const config = JSON.stringify({
      heroTitle: 'Learn. Build. Get hired.',
      testimonials: [{ name: 'Asha', role: 'Java Full Stack', text: 'The tests kept me on track.', image: '' }],
      faqs: [{ q: 'Are there evening batches?', a: 'Yes, from 6 pm.' }],
      announcement: { enabled: true, text: 'New batch on the 15th', link: '' },
      hidden: ['how'],
    })
    const branding = await import('@/api/branding')
    vi.mocked(branding.getSiteConfig).mockResolvedValue({ config })
    renderHome()
    expect(await screen.findByText('Learn. Build. Get hired.')).toBeTruthy()
    expect(screen.getByText(/The tests kept me on track/)).toBeTruthy()
    expect(screen.getByText('Are there evening batches?')).toBeTruthy()
    expect(screen.getByText('New batch on the 15th')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'How it works' })).toBeNull()
  })

  it('has no language button: the site is English only', async () => {
    renderHome()
    await screen.findByRole('heading', { name: /Our courses/ })
    expect(screen.queryByRole('button', { name: 'Change language' })).toBeNull()
  })
})
