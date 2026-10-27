import { useEffect } from 'react'
import { Maximize2, Minimize2 } from 'lucide-react'

import { Button } from '@/components/ui/button'

/**
 * Full screen for a panel, done by laying the panel over the page (not the browser's fullscreen mode, which a secure
 * test is already using and which Escape would leave for the whole test). Escape closes it; the page behind does not scroll.
 */
export function useFullscreenPanel(active: boolean, close: () => void) {
  useEffect(() => {
    if (!active) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = previous
    }
  }, [active, close])
}

/** Below the test's own warnings (z-40, z-50) and its camera picture (z-30), above the page. */
export const FULLSCREEN_PANEL_CLASS = 'fixed inset-0 z-[25] overflow-auto rounded-none'

export function FullscreenToggle({ full, onToggle }: { full: boolean; onToggle: () => void }) {
  return (
    <Button
      type="button"
      size="sm"
      variant="ghost"
      onClick={onToggle}
      aria-pressed={full}
      title={full ? 'Exit full screen (Esc)' : 'Full screen'}
    >
      {full ? <Minimize2 /> : <Maximize2 />}
      {full ? 'Exit full screen' : 'Full screen'}
    </Button>
  )
}
