import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { AnimatedNumber } from './animated-number'

describe('AnimatedNumber', () => {
  it('counts up to the value and writes it in the given format', async () => {
    render(<p data-testid="n"><AnimatedNumber value={1250} format={(n) => `Rs. ${Math.round(n)}`} /></p>)
    await waitFor(() => expect(screen.getByTestId('n').textContent).toBe('Rs. 1250'), { timeout: 4000 })
  })

  it('shows the value at once for visitors who ask for less motion', () => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('reduce'), addEventListener: () => undefined, removeEventListener: () => undefined }))
    render(<p data-testid="n"><AnimatedNumber value={42} /></p>)
    expect(screen.getByTestId('n').textContent).toBe('42')
    vi.unstubAllGlobals()
  })

  it('glides to a new value when it changes', async () => {
    const view = render(<p data-testid="n"><AnimatedNumber value={10} /></p>)
    await waitFor(() => expect(screen.getByTestId('n').textContent).toBe('10'), { timeout: 4000 })
    view.rerender(<p data-testid="n"><AnimatedNumber value={50} /></p>)
    await waitFor(() => expect(screen.getByTestId('n').textContent).toBe('50'), { timeout: 4000 })
  })
})
