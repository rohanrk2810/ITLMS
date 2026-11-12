import { describe, expect, it } from 'vitest'

import { dueWording } from './fee-due-card'

describe('dueWording', () => {
  const today = new Date(2026, 1, 10)

  it('says how far a due date is', () => {
    expect(dueWording('2026-02-10', today)).toMatchObject({ text: 'Due today', overdue: false, soon: true })
    expect(dueWording('2026-02-11', today).text).toBe('In 1 day')
    expect(dueWording('2026-02-20', today)).toMatchObject({ text: 'In 10 days', soon: false })
  })

  it('marks a date that has passed as overdue', () => {
    expect(dueWording('2026-02-09', today)).toMatchObject({ text: 'Overdue by 1 day', overdue: true })
    expect(dueWording('2026-02-01', today).text).toBe('Overdue by 9 days')
  })
})
