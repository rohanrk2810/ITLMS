import { useEffect, useRef, useState } from 'react'

/** Shorter than this is a notification or a slip of the mouse, not leaving the class. */
const MIN_AWAY_SECONDS = 5

/**
 * Notes when the student leaves the class tab or window (switches tab, minimises, or clicks into another app) and
 * reports it, with how long they were away, once they come back. It sees only that the page lost focus, never what
 * they did. Returns the length of the latest absence so the room can tell the student it was noticed.
 */
export function useTabMonitor({
  enabled,
  onAway,
}: {
  enabled: boolean
  onAway: (seconds: number) => void
}): number | null {
  const [lastAway, setLastAway] = useState<number | null>(null)
  const report = useRef(onAway)
  useEffect(() => {
    report.current = onAway
  }, [onAway])

  useEffect(() => {
    if (!enabled) return
    let awaySince: number | null = null

    const leave = () => {
      awaySince ??= Date.now()
    }
    const back = () => {
      if (awaySince === null || document.hidden) return
      const seconds = Math.round((Date.now() - awaySince) / 1000)
      awaySince = null
      if (seconds >= MIN_AWAY_SECONDS) {
        setLastAway(seconds)
        report.current(seconds)
      }
    }
    const onVisibility = () => (document.hidden ? leave() : back())

    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', leave)
    window.addEventListener('focus', back)
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('blur', leave)
      window.removeEventListener('focus', back)
    }
  }, [enabled])

  return lastAway
}
