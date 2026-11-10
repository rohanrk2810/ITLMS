import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { extendAccess, extendBatch } from '@/api/batches'

import { ExtendAccessButton, ExtendBatchButton } from './extend-validity'

vi.mock('@/api/batches', () => ({ extendBatch: vi.fn(), extendAccess: vi.fn() }))

function show(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

const future = '2099-12-31'

describe('extending how long a course runs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('extends a batch with a new end date and a reason', async () => {
    vi.mocked(extendBatch).mockResolvedValue({
      batchId: 3,
      batchCode: 'JFS-1',
      previousEndDate: '2026-11-01',
      endDate: future,
      status: 'ONGOING',
      studentsExtended: 4,
    })
    show(<ExtendBatchButton batchId={3} currentEnd="2026-11-01" />)

    await userEvent.click(screen.getByRole('button', { name: /Extend course/ }))
    await userEvent.type(await screen.findByLabelText('New end date'), future)
    await userEvent.type(screen.getByLabelText(/Why\?/), 'Syllabus not finished')
    await userEvent.click(screen.getByRole('button', { name: 'Extend' }))

    await waitFor(() => expect(extendBatch).toHaveBeenCalledWith(3, { endDate: future, reason: 'Syllabus not finished' }))
  })

  it('reopens a student whose access ran out', async () => {
    vi.mocked(extendAccess).mockResolvedValue({} as never)
    show(<ExtendAccessButton enrollmentId={7} studentName="Asha Patil" accessUntil="2026-10-01" expired />)

    await userEvent.click(screen.getByRole('button', { name: /Reopen/ }))
    expect(await screen.findByText(/Their access ended on/)).toBeTruthy()
    await userEvent.type(screen.getByLabelText('Access until'), future)
    await userEvent.type(screen.getByLabelText(/Why\?/), 'Missed classes for illness')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(extendAccess).toHaveBeenCalledWith(7, { accessUntil: future, reason: 'Missed classes for illness' }))
  })

  it('offers Set date to a student with no limit and Extend to one who has a date', () => {
    const first = show(<ExtendAccessButton enrollmentId={1} studentName="A" accessUntil={null} expired={false} />)
    expect(screen.getByRole('button', { name: /Set date/ })).toBeTruthy()
    first.unmount()
    show(<ExtendAccessButton enrollmentId={1} studentName="A" accessUntil="2099-01-01" expired={false} />)
    expect(screen.getByRole('button', { name: /Extend/ })).toBeTruthy()
  })
})
