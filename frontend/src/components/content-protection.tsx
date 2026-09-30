import { useEffect, useRef, useState } from 'react'
import { EyeOff } from 'lucide-react'
import { toast } from 'sonner'

/**
 * Wraps a video area with two deterrents against casual copying: the content is blurred out whenever the browser
 * tab is not the one the person is looking at, and a faint watermark naming the viewer is tiled across it so a copy
 * that does get out traces back to who made it.
 *
 * <p><strong>Neither of these blocks a screenshot.</strong> A browser page cannot detect the OS-level shortcuts that
 * take one (Windows' Snipping Tool, macOS's Cmd+Shift+4, a phone camera pointed at the screen), and nothing served
 * to a browser can be encrypted against the person viewing it - that is what "watch" means. What this component
 * actually does: the tab-blur hides the frame from a second monitor or a screen-recorder's preview window while
 * attention is elsewhere, PrintScreen is one specific shortcut this page *can* see (as a key event) and is logged
 * and flashed on, and the watermark makes a leaked recording identifiable after the fact. Anyone building on this
 * should say what it does in those terms, not as "screenshot protection".
 */
export function ContentProtection({ active, watermarkLabel, children, className }: {
  /** Off entirely for a host watching their own content - there is nothing to deter them from. */
  active: boolean
  watermarkLabel: string
  children: React.ReactNode
  className?: string
}) {
  const [hidden, setHidden] = useState(false)
  const [printScreenFlash, setPrintScreenFlash] = useState(false)
  const flashTimer = useRef<number | undefined>(undefined)

  useEffect(() => {
    if (!active) return

    const updateVisibility = () => setHidden(document.hidden || !document.hasFocus())
    updateVisibility()

    const onKeyUp = (e: KeyboardEvent) => {
      if (e.key !== 'PrintScreen') return
      toast.warning('This class is watermarked to you. Screenshots and recordings can be traced back to your account.')
      setPrintScreenFlash(true)
      window.clearTimeout(flashTimer.current)
      flashTimer.current = window.setTimeout(() => setPrintScreenFlash(false), 2500)
    }

    document.addEventListener('visibilitychange', updateVisibility)
    window.addEventListener('blur', updateVisibility)
    window.addEventListener('focus', updateVisibility)
    window.addEventListener('keyup', onKeyUp)
    return () => {
      document.removeEventListener('visibilitychange', updateVisibility)
      window.removeEventListener('blur', updateVisibility)
      window.removeEventListener('focus', updateVisibility)
      window.removeEventListener('keyup', onKeyUp)
      window.clearTimeout(flashTimer.current)
    }
  }, [active])

  return (
    <div className={`relative overflow-hidden ${className ?? ''}`}>
      {children}

      {active && (
        <div className="pointer-events-none absolute inset-0 grid grid-cols-3 grid-rows-3 select-none">
          {Array.from({ length: 9 }).map((_, i) => (
            <span
              key={i}
              className="flex -rotate-[20deg] items-center justify-center text-xs font-medium text-white/15"
            >
              {watermarkLabel}
            </span>
          ))}
        </div>
      )}

      {active && (hidden || printScreenFlash) && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/90 text-white backdrop-blur-xl">
          <EyeOff className="size-6" />
          <p className="text-sm">
            {printScreenFlash ? 'Screenshot detected - this is logged to your account.' : 'Paused while this tab is not in focus.'}
          </p>
        </div>
      )}
    </div>
  )
}
