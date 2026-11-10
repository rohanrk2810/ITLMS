import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getBranding, getSiteConfig, updateBranding, updateSiteConfig } from '@/api/branding'

import { SetupTab } from './setup-tab'

vi.mock('@/api/branding', () => ({
  getBranding: vi.fn(),
  getSiteConfig: vi.fn(),
  updateBranding: vi.fn(),
  updateSiteConfig: vi.fn(),
}))

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SetupTab />
    </QueryClientProvider>,
  )
}

const saved = {
  name: 'Old Institute',
  tagline: 'Old tagline',
  primaryColor: '#111111',
  contactEmail: 'old@x.in',
  contactPhone: '123',
  website: null,
  address: 'Old address',
  signatoryName: 'Old Signer',
  signatoryTitle: 'Director',
}

describe('Quick setup', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getBranding).mockResolvedValue(saved as never)
    vi.mocked(getSiteConfig).mockResolvedValue({ config: JSON.stringify({ aboutIntro: 'Keep this', heroTitle: 'Old heading' }) })
    vi.mocked(updateBranding).mockResolvedValue({} as never)
    vi.mocked(updateSiteConfig).mockResolvedValue({ config: '{}' })
  })

  it('offers the data sheet, the prompt, the upload, the example and a box to paste into', () => {
    show()
    expect(screen.getByRole('link', { name: /Download data sheet \(PDF\)/ }).getAttribute('href')).toBe('/institute-data-sheet.pdf')
    for (const name of [/Download prompt file/, /Copy ChatGPT prompt/, /Upload \.json file/, /Example file/]) {
      expect(screen.getByRole('button', { name })).toBeTruthy()
    }
    expect(screen.getByLabelText(/paste the reply here/i)).toBeTruthy()
  })

  it('checks a pasted reply, shows what will change, and applies only after the administrator agrees', async () => {
    show()
    const reply = '{"institute":{"name":"Sprint IT Academy","address":"Pune"},"website":{"heroTitle":"Learn skills that get you hired."}}'
    await userEvent.click(screen.getByLabelText(/paste the reply here/i))
    await userEvent.paste(reply)
    await userEvent.click(screen.getByRole('button', { name: 'Check the file' }))

    expect(await screen.findByText(/Institute: Name, Full address/)).toBeTruthy()
    expect(screen.getByText(/Website: Main heading/)).toBeTruthy()
    expect(updateBranding).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Apply to my institute and website' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Apply' }))

    await waitFor(() => expect(updateBranding).toHaveBeenCalled())
    // what the file leaves out stays as it is
    expect(updateBranding).toHaveBeenCalledWith(expect.objectContaining({ name: 'Sprint IT Academy', address: 'Pune', tagline: 'Old tagline', signatoryName: 'Old Signer' }))
    const site = JSON.parse(vi.mocked(updateSiteConfig).mock.calls[0][0])
    expect(site.heroTitle).toBe('Learn skills that get you hired.')
    expect(site.aboutIntro).toBe('Keep this')
  })

  it('a file with an error cannot be applied and says why', async () => {
    show()
    await userEvent.click(screen.getByLabelText(/paste the reply here/i))
    await userEvent.paste('{"institute":{"name":"X","primaryColor":"purple"}}')
    await userEvent.click(screen.getByRole('button', { name: 'Check the file' }))

    expect(await screen.findByText(/must look like #1a2b3c/)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Apply to my institute and website' }) as HTMLButtonElement).disabled).toBe(true)
  })
})
