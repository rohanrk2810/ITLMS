/** Where a test stands against the time the trainer set for it. */
export type ExamTiming =
  | { kind: 'anytime' }
  | { kind: 'upcoming'; opens: string }
  | { kind: 'open'; closes: string | null }
  | { kind: 'expired'; closed: string }

/**
 * A test with no times can be taken whenever it is published. One with times can be started only between them; after
 * the closing time it has expired for anyone who did not take it.
 */
export function examTiming(availableFrom: string | null, availableUntil: string | null, now = Date.now()): ExamTiming {
  if (!availableFrom && !availableUntil) return { kind: 'anytime' }
  if (availableFrom && new Date(availableFrom).getTime() > now) return { kind: 'upcoming', opens: availableFrom }
  if (availableUntil && new Date(availableUntil).getTime() < now) return { kind: 'expired', closed: availableUntil }
  return { kind: 'open', closes: availableUntil }
}

/** Date and time in the reader's own time zone. */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })
}
