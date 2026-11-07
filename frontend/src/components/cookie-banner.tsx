import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'

const KEY = 'cookie-notice-accepted'

function alreadyAccepted(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

/**
 * The site only uses essential storage (sign-in, form protection, theme), so this is a notice with
 * one acknowledgement rather than a set of optional toggles. Remembered in this browser only.
 */
export function CookieBanner() {
  const [hidden, setHidden] = useState(alreadyAccepted)
  if (hidden) return null

  function accept() {
    try {
      localStorage.setItem(KEY, '1')
    } catch {
      // Private window: the notice simply shows again next visit.
    }
    setHidden(true)
  }

  return (
    <div
      role="region"
      aria-label="Cookie notice"
      className="fixed inset-x-0 bottom-0 z-50 border-t bg-background p-4 shadow-lg"
    >
      <div className="mx-auto flex max-w-3xl flex-col items-start gap-3 sm:flex-row sm:items-center">
        <p className="text-sm">
          We use only essential cookies and browser storage to keep you signed in, protect forms and remember your theme.
          See our{' '}
          <Link to="/cookies" className="underline">
            Cookie Policy
          </Link>{' '}
          and{' '}
          <Link to="/privacy" className="underline">
            Privacy Policy
          </Link>
          .
        </p>
        <Button size="sm" onClick={accept} className="shrink-0">
          Got it
        </Button>
      </div>
    </div>
  )
}
