import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/stores/auth-store'

import {
  AboutPage,
  AffiliatesPage,
  BlogPage,
  CareerGuidePage,
  CareersPage,
  CommunityPage,
  ContactPage,
  FaqPage,
  FreeResourcesPage,
  InstructorPage,
  LearningPathsPage,
  PricingPage,
  ProgramsPage,
  SuccessStoriesPage,
} from './site-pages'

const course = (id: number, title: string, level: string, fee: number | null) => ({
  id,
  title,
  code: `C${id}`,
  summary: `${title} summary`,
  technologyStack: 'Java',
  durationHours: 100,
  level,
  fee,
  thumbnailRef: null,
  status: 'PUBLISHED',
})
const page = (content: unknown[]) => ({ content, totalElements: content.length, page: 0, size: 100, totalPages: 1, first: true, last: true })

vi.mock('@/api/public-site', () => ({
  getPublicCourses: () => Promise.resolve(page([])),
  getAllPublicCourses: () => Promise.resolve(page([course(1, 'Java Full Stack', 'BEGINNER', 15000), course(2, 'Spring Boot', 'ADVANCED', null)])),
  sendEnquiry: () => Promise.resolve(),
}))
vi.mock('@/api/branding', async (orig) => ({
  ...(await orig<object>()),
  getBranding: vi.fn(() => Promise.reject(new Error('down'))),
  getSiteConfig: vi.fn(() => Promise.reject(new Error('down'))),
}))

function show(element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{element}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('the pages the footer links to', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ status: 'unauthenticated' })
  })

  const cases: [string, React.ReactElement, string][] = [
    ['About', <AboutPage key="AboutPage" />, 'About SutraVidya'],
    ['Programs', <ProgramsPage key="ProgramsPage" />, 'Programs'],
    ['Pricing', <PricingPage key="PricingPage" />, 'Pricing'],
    ['Blog', <BlogPage key="BlogPage" />, 'Blog'],
    ['Success stories', <SuccessStoriesPage key="SuccessStoriesPage" />, 'Success stories'],
    ['Learning paths', <LearningPathsPage key="LearningPathsPage" />, 'Learning paths'],
    ['Career guide', <CareerGuidePage key="CareerGuidePage" />, 'Career guide'],
    ['Community', <CommunityPage key="CommunityPage" />, 'Student community'],
    ['Free resources', <FreeResourcesPage key="FreeResourcesPage" />, 'Free resources'],
    ['FAQ', <FaqPage key="FaqPage" />, 'Frequently asked questions'],
    ['Contact', <ContactPage key="ContactPage" />, 'Contact us'],
    ['Instructor', <InstructorPage key="InstructorPage" />, 'Become an instructor'],
    ['Careers', <CareersPage key="CareersPage" />, 'Careers'],
    ['Affiliates', <AffiliatesPage key="AffiliatesPage" />, 'Affiliates'],
  ]

  it.each(cases)('%s opens with its heading and the footer', async (_name, element, heading) => {
    show(element)
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Stay Updated' })).toBeTruthy()
  })

  it('Programs groups the courses by level and Pricing lists each fee', async () => {
    const programs = show(<ProgramsPage />)
    expect(await screen.findByRole('heading', { level: 2, name: 'beginner' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 2, name: 'advanced' })).toBeTruthy()
    programs.unmount()

    show(<PricingPage />)
    const row = (await screen.findByText('Java Full Stack')).closest('tr') as HTMLElement
    expect(within(row).getByText(/15,000/)).toBeTruthy()
    expect(within(screen.getByText('Spring Boot').closest('tr') as HTMLElement).getByText('On request')).toBeTruthy()
  })

  it('the instructor page starts the enquiry message so the team knows what it is about', async () => {
    show(<InstructorPage />)
    const message = (await screen.findByLabelText(/message/i)) as HTMLTextAreaElement
    expect(message.value).toMatch(/teach/)
  })

  it('the FAQ falls back to a default set and every free resource opens in a new tab', async () => {
    const faq = show(<FaqPage />)
    expect(await screen.findByText('How do I join a course?')).toBeTruthy()
    faq.unmount()

    show(<FreeResourcesPage />)
    for (const link of screen.getAllByRole('link').filter((l) => /^https:/.test(l.getAttribute('href') ?? ''))) {
      expect(link.getAttribute('target')).toBe('_blank')
      expect(link.getAttribute('rel')).toContain('noopener')
    }
  })
})
