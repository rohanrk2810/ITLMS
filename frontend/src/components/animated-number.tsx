import { useEffect, useRef, useState } from 'react'

/**
 * A figure that counts up to its value when it first appears and glides to a new value when it changes. Visitors who
 * ask for less motion just see the value.
 */
export function AnimatedNumber({ value, format = (n) => String(Math.round(n)) }: { value: number; format?: (n: number) => string }) {
  const [shown, setShown] = useState(0)
  const from = useRef(0)

  useEffect(() => {
    const reduce = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduce || typeof requestAnimationFrame !== 'function') {
      from.current = value
      setShown(value)
      return
    }
    const start = from.current
    const began = performance.now()
    const duration = 800
    let frame = 0
    const tick = () => {
      // The clock is read here rather than taken from the frame, so the two can never disagree about their start.
      const t = Math.min(1, Math.max(0, (performance.now() - began) / duration))
      const eased = 1 - Math.pow(1 - t, 3)
      const current = start + (value - start) * eased
      from.current = current
      setShown(current)
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])

  return <>{format(shown)}</>
}
