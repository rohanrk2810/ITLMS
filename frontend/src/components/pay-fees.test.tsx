import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { FeePlanResponse } from '@/api/finance'
import { createFeeOrder, getCheckoutConfig, payWithRazorpay, verifyOnlinePayment } from '@/api/online-payments'
import { useAuthStore } from '@/stores/auth-store'

import { PayFeesButton, PayInstallmentButton } from './pay-fees'

vi.mock('@/api/online-payments', () => ({
  getCheckoutConfig: vi.fn(),
  createFeeOrder: vi.fn(),
  payWithRazorpay: vi.fn(),
  verifyOnlinePayment: vi.fn(),
}))

const plan: FeePlanResponse = {
  id: 11,
  studentId: 30,
  studentCode: 'STU-30',
  studentName: 'Asha',
  courseId: 5,
  batchId: null,
  currency: 'INR',
  totalFee: 30000,
  discount: 0,
  netFee: 30000,
  paid: 10000,
  outstanding: 20000,
  overdue: 0,
  status: 'ACTIVE',
  nextDueDate: '2026-12-01',
  nextDueAmount: 10000,
  notes: null,
  installments: [],
  payments: [],
}

function show(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('paying fees online', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      status: 'authenticated',
      user: { id: 1, fullName: 'Asha', email: 'a@x.in', phone: '9000000000', role: 'STUDENT', extraRoles: [] } as never,
      roles: ['STUDENT'],
    })
  })

  it('offers the next installment, the whole balance and a typed amount', async () => {
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'rzp_test_x' })
    show(<PayFeesButton plan={plan} />)
    await userEvent.click(await screen.findByRole('button', { name: /Pay online/ }))
    expect(await screen.findByRole('button', { name: /Pay next installment .*10,000/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Pay full balance .*20,000/ })).toBeTruthy()
    expect(screen.getByLabelText('Or another amount')).toBeTruthy()
  })

  it('pays the chosen amount, has the server record it, and does not ask for more than is owed', async () => {
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'rzp_test_x' })
    vi.mocked(createFeeOrder).mockResolvedValue({ orderId: 'order_1', keyId: 'rzp_test_x', amountPaise: 1_000_000, currency: 'INR', courseTitle: 'Fee payment' })
    const result = { razorpay_order_id: 'order_1', razorpay_payment_id: 'pay_1', razorpay_signature: 'sig' }
    vi.mocked(payWithRazorpay).mockResolvedValue(result)
    vi.mocked(verifyOnlinePayment).mockResolvedValue({ orderId: 'order_1', status: 'FEES_PAID', courseId: 5, batchCode: null, message: 'Payment received.' })
    show(<PayFeesButton plan={plan} />)

    await userEvent.click(await screen.findByRole('button', { name: /Pay online/ }))
    await userEvent.click(await screen.findByRole('button', { name: /Pay next installment/ }))

    expect(createFeeOrder).toHaveBeenCalledWith(11, 10000)
    expect(verifyOnlinePayment).toHaveBeenCalledWith(result)
  })

  it('a typed amount above the balance cannot be paid', async () => {
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'rzp_test_x' })
    show(<PayFeesButton plan={plan} />)
    await userEvent.click(await screen.findByRole('button', { name: /Pay online/ }))
    await userEvent.type(await screen.findByLabelText('Or another amount'), '20001')
    const pay = screen.getAllByRole('button', { name: 'Pay' }).at(-1) as HTMLButtonElement
    expect(pay.disabled).toBe(true)
    await userEvent.clear(screen.getByLabelText('Or another amount'))
    await userEvent.type(screen.getByLabelText('Or another amount'), '5000')
    expect(pay.disabled).toBe(false)
  })

  it('shows nothing while online payment is off, or when nothing is owed', async () => {
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: false, keyId: null })
    const off = show(<PayFeesButton plan={plan} />)
    await vi.waitFor(() => expect(getCheckoutConfig).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /Pay online/ })).toBeNull()
    off.unmount()

    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'k' })
    show(<PayFeesButton plan={{ ...plan, outstanding: 0 }} />)
    await vi.waitFor(() => expect(getCheckoutConfig).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('button', { name: /Pay online/ })).toBeNull()
  })

  it('an unpaid installment has its own Pay button, a paid one has none', async () => {
    vi.mocked(getCheckoutConfig).mockResolvedValue({ enabled: true, keyId: 'k' })
    const open = show(<PayInstallmentButton plan={plan} remaining={10000} />)
    expect(await screen.findByRole('button', { name: /Pay/ })).toBeTruthy()
    open.unmount()
    show(<PayInstallmentButton plan={plan} remaining={0} />)
    expect(screen.queryByRole('button', { name: /Pay/ })).toBeNull()
  })
})
