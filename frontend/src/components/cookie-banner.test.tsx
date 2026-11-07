import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { CookieBanner } from './cookie-banner'

describe('CookieBanner', () => {
  it('renders inside a router and links to the policies', () => {
    localStorage.removeItem('cookie-notice-accepted')
    render(
      <MemoryRouter>
        <CookieBanner />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: 'Cookie Policy' })).toBeTruthy()
  })
})
