import { describe, expect, it } from 'vitest'

import { formatCourseFee, formatDate, formatMoney, hasCourseFee } from './format'

describe('formatMoney', () => {
  it('writes whole rupees with Indian grouping', () => {
    expect(formatMoney(1500)).toContain('1,500')
    expect(formatMoney(125000)).toContain('1,25,000')
  })

  it('never shows paise', () => {
    expect(formatMoney(1500.75)).not.toContain('.')
  })

  it('falls back to plain text for a currency code it does not know', () => {
    expect(formatMoney(250, 'NOT-A-CODE')).toBe('NOT-A-CODE 250')
  })
})

describe('course fee', () => {
  it('treats zero, empty and missing as "not priced", never as free', () => {
    expect(hasCourseFee(0)).toBe(false)
    expect(hasCourseFee(null)).toBe(false)
    expect(hasCourseFee(undefined)).toBe(false)
    expect(hasCourseFee(-5)).toBe(false)
    expect(hasCourseFee(100)).toBe(true)
  })

  it('shows the price, or says the institute will confirm it', () => {
    expect(formatCourseFee(20000)).toContain('20,000')
    expect(formatCourseFee(0)).toBe('To be confirmed by the institute')
    expect(formatCourseFee(null)).toBe('To be confirmed by the institute')
  })
})

describe('formatDate', () => {
  it('includes the year of the date it is given', () => {
    expect(formatDate('2027-04-30T10:00:00Z')).toContain('2027')
  })
})
