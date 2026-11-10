import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { searchBatches } from '@/api/batches'
import { addBatchCharge, addCharge } from '@/api/finance'

import { AddChargeButton, ChargeBatchButton } from './add-charge-dialogs'

vi.mock('@/api/finance', () => ({ addCharge: vi.fn(), addBatchCharge: vi.fn() }))
vi.mock('@/api/batches', () => ({ searchBatches: vi.fn() }))

function show(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('extra charges', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('adds a named charge to one student and refreshes the plan', async () => {
    vi.mocked(addCharge).mockResolvedValue({} as never)
    const added = vi.fn()
    show(<AddChargeButton feePlanId={11} onAdded={added} />)

    await userEvent.click(screen.getByRole('button', { name: /Add charge/ }))
    await userEvent.type(await screen.findByLabelText('What is it for?'), 'Development fee')
    await userEvent.type(screen.getByLabelText('Amount'), '500')
    await userEvent.click(screen.getByRole('button', { name: 'Add charge' }))

    await waitFor(() => expect(added).toHaveBeenCalled())
    expect(addCharge).toHaveBeenCalledWith(11, expect.objectContaining({ title: 'Development fee', amount: 500 }))
  })

  it('charges a whole batch and says how many students were reached', async () => {
    vi.mocked(searchBatches).mockResolvedValue({
      content: [{ id: 3, batchCode: 'JFS-1', courseTitle: 'Java Full Stack' }],
      totalElements: 1,
      page: 0,
      size: 20,
      totalPages: 1,
      first: true,
      last: true,
    } as never)
    vi.mocked(addBatchCharge).mockResolvedValue({ students: 12, skipped: 0 })
    show(<ChargeBatchButton />)

    await userEvent.click(screen.getByRole('button', { name: /Charge a batch/ }))
    await userEvent.selectOptions(await screen.findByLabelText('Batch'), '3')
    await userEvent.type(screen.getByLabelText('What is it for?'), 'Exam fee')
    await userEvent.type(screen.getByLabelText('Amount'), '750')
    await userEvent.click(screen.getByRole('button', { name: 'Charge the batch' }))

    await waitFor(() => expect(addBatchCharge).toHaveBeenCalledWith(expect.objectContaining({ batchId: 3, title: 'Exam fee', amount: 750 })))
  })

  it('a batch charge cannot be sent before a batch is chosen', async () => {
    vi.mocked(searchBatches).mockResolvedValue({ content: [], totalElements: 0, page: 0, size: 20, totalPages: 0, first: true, last: true } as never)
    show(<ChargeBatchButton />)
    await userEvent.click(screen.getByRole('button', { name: /Charge a batch/ }))
    const send = (await screen.findByRole('button', { name: 'Charge the batch' })) as HTMLButtonElement
    expect(send.disabled).toBe(true)
  })
})
