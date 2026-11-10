import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/stores/auth-store'

import { PublicCoursesPage } from './public-courses-page'

const course = (id: number, title: string, level: string, fee: number | null) => ({
  id,
  title,
  code: `C${id}`,
  summary: `${title} summary`,
  technologyStack: 'Java, Spring',
  durationHours: 120,
  level,
  fee,
  thumbnailRef: null,
  status: 'PUBLISHED',
})

vi.mock('@/api/public-site', () => ({
  getPublicCourses: () => Promise.resolve({ content: [], totalElements: 0, page: 0, size: 12, totalPages: 0, first: true, last: true }),
  getAllPublicCourses: () =>
    Promise.resolve({
      content: [course(1, 'Java Full Stack', 'BEGINNER', 15000), course(2, 'Data Science', 'ADVANCED', null)],
      totalElements: 2,
      page: 0,
      size: 100,
      totalPages: 1,
      first: true,
      last: true,
    }),
  sendEnquiry: () => Promise.resolve(),
}))
vi.mock('@/api/branding', async (orig) => ({
  ...(await orig<object>()),
  getBranding: vi.fn(() => Promise.reject(new Error('down'))),
  getSiteConfig: vi.fn(() => Promise.reject(new Error('down'))),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PublicCoursesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PublicCoursesPage', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ status: 'unauthenticated' })
  })

  it('lists every published course with its fee and the Buy and Inquiry buttons', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Java Full Stack' }, { timeout: 5000 })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Data Science' })).toBeTruthy()
    expect(screen.getByText(/15,000/)).toBeTruthy()
    expect(screen.getByText('Fee on request')).toBeTruthy()
    const cards = screen.getAllByRole('article')
    expect(cards).toHaveLength(2)
    for (const card of cards) {
      expect(within(card).getByRole('button', { name: /Buy/ })).toBeTruthy()
      expect(within(card).getByRole('button', { name: /Inquiry/ })).toBeTruthy()
    }
  })

  it('filters by the search text', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Java Full Stack' })
    await userEvent.type(screen.getByLabelText('Search courses'), 'science')
    expect(screen.queryByRole('heading', { name: 'Java Full Stack' })).toBeNull()
    expect(screen.getByRole('heading', { name: 'Data Science' })).toBeTruthy()
  })

  it('has a footer whose links stay inside the app', async () => {
    renderPage()
    const explore = await screen.findByRole('navigation', { name: 'Explore' })
    expect(within(explore).getByRole('link', { name: 'Courses' }).getAttribute('href')).toBe('/courses')
    const resources = screen.getByRole('navigation', { name: 'Resources' })
    expect(within(resources).getByRole('link', { name: 'FAQ' }).getAttribute('href')).toBe('/faq')
    const company = screen.getByRole('navigation', { name: 'Company' })
    expect(within(company).getAllByRole('link')).toHaveLength(5)
    expect(screen.getByRole('heading', { name: 'Stay Updated' })).toBeTruthy()
    for (const link of screen.getAllByRole('link')) {
      expect(link.getAttribute('href') ?? '').not.toMatch(/^https?:/)
    }
  })
})
