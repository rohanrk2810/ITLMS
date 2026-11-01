import { describe, expect, it } from 'vitest'

import { examTiming } from './exam-timing'

const NOW = new Date('2026-11-01T10:00:00Z').getTime()

describe('examTiming', () => {
  it('a test with no times can be taken whenever it is published', () => {
    expect(examTiming(null, null, NOW)).toEqual({ kind: 'anytime' })
  })

  it('before the opening time it is upcoming', () => {
    expect(examTiming('2026-11-02T09:00:00Z', null, NOW)).toEqual({ kind: 'upcoming', opens: '2026-11-02T09:00:00Z' })
  })

  it('between the times it is open and says when it closes', () => {
    expect(examTiming('2026-11-01T09:00:00Z', '2026-11-01T12:00:00Z', NOW)).toEqual({
      kind: 'open',
      closes: '2026-11-01T12:00:00Z',
    })
  })

  it('with only an opening time it stays open with no closing time', () => {
    expect(examTiming('2026-10-31T09:00:00Z', null, NOW)).toEqual({ kind: 'open', closes: null })
  })

  it('after the closing time it has expired', () => {
    expect(examTiming(null, '2026-11-01T09:59:59Z', NOW)).toEqual({ kind: 'expired', closed: '2026-11-01T09:59:59Z' })
  })
})
