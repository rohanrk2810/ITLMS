import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getCheckoutConfig } from '@/api/online-payments'
import { useAuthStore } from '@/stores/auth-store'

import { CourseActions } from './course-actions'

vi.mock('@/api/online-payments', () => ({
  getCheckoutConfig: vi.fn(),
  createOnlineOrder: vi.fn(),
  payWithRazorpay: vi.fn(),
  verifyOnlinePayment: vi.fn(),
}))
vi.mock('@/api/public-site', () => ({
  getPublicCourses: () => Promise.resolve({ content: [], totalElements: 0, page: 0, size: 12, totalPages: 0, first: true, last: true }),
  sendEnquiry: () => Promise.resolve(),
}))

const course = { id: 5, title: 'Frontend', fee: 25000 }

function show(fee: number | null = 25000) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <CourseActions course={{ ...course, fee }} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function signIn(role: 'STUDENT' | 'ADMIN' | null) {
  if (!role) {
    useAuthStore.setState({ status: 'unauthenticated', user: null, roles: [] })
    return
  }
  useAuthStore.setState({
    status: 'authenticated',
    user: { id: 1, fullName: 'Asha', email: 'a@x.in', phone: '9000000000', role, extraRoles: [] } as never,
    roles: [role],
  })
}

describe('CourseActions: the Pay online choice', () => {
  beforeEach(() => {
    vi.mocked(getCheckoutConfig).mockReset()
  })

  it('a signed-in student sees Pay online when Razorpay is set up and the course has a fee', async () => {
    signIn('STUDENT')
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'rzp_test_x' })
    show()
    await userEvent.click(screen.getByRole('button', { name: /Buy/ }))
    expect(await screen.findByRole('button', { name: /Pay .*25,000.* online/ })).toBeTruthy()
  })

  it('no Pay online while Razorpay is not set up, for a course without a fee, or for someone who is not a student', async () => {
    signIn('STUDENT')
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: false, keyId: null })
    const off = show()
    await userEvent.click(screen.getByRole('button', { name: /Buy/ }))
    expect(await screen.findByRole('button', { name: /Continue to the course/ })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Pay .* online/ })).toBeNull()
    off.unmount()

    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'k' })
    const free = show(0)
    await userEvent.click(screen.getByRole('button', { name: /Buy/ }))
    expect(screen.queryByRole('button', { name: /Pay .* online/ })).toBeNull()
    free.unmount()

    signIn('ADMIN')
    show()
    await userEvent.click(screen.getByRole('button', { name: /Buy/ }))
    expect(screen.queryByRole('button', { name: /Pay .* online/ })).toBeNull()
  })

  it('a visitor who is not signed in is asked to create an account', async () => {
    signIn(null)
    show()
    await userEvent.click(screen.getByRole('button', { name: /Buy/ }))
    expect(await screen.findByRole('button', { name: /Create an account/ })).toBeTruthy()
    expect(getCheckoutConfig).not.toHaveBeenCalled()
  })
})
