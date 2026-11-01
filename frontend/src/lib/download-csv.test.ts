import { describe, expect, it } from 'vitest'

import { safeCell, toCsv } from './download-csv'

describe('safeCell (spreadsheet formula injection)', () => {
  it.each([
    ['=HYPERLINK("http://evil.example","click")'],
    ['@SUM(1+1)'],
    ['+cmd|" /C calc"!A0'],
    ['-2+3+cmd|" /C calc"!A0'],
    ['\t=1+1'],
  ])('%s is made inert', (value) => {
    expect(safeCell(value).startsWith("'")).toBe(true)
  })

  it('leaves ordinary text, phone numbers and numbers alone', () => {
    expect(safeCell('Asha Patil')).toBe('Asha Patil')
    expect(safeCell('+91 98765 43210')).toBe('+91 98765 43210')
    expect(safeCell('-5')).toBe('-5')
    expect(safeCell('(020) 2553-1234')).toBe('(020) 2553-1234')
    expect(safeCell(-5)).toBe('-5')
    expect(safeCell(42)).toBe('42')
  })

  it('shows nothing for a missing value', () => {
    expect(safeCell(null)).toBe('')
    expect(safeCell(undefined)).toBe('')
  })
})

describe('toCsv', () => {
  it('writes a header and rows, separated by CRLF', () => {
    expect(toCsv(['A', 'B'], [[1, 'x'], [2, 'y']])).toBe('A,B\r\n1,x\r\n2,y')
  })

  it('quotes a field with a comma, a quote or a line break', () => {
    expect(toCsv(['Name'], [['Patil, Asha'], ['say "hi"'], ['two\nlines']])).toBe(
      'Name\r\n"Patil, Asha"\r\n"say ""hi"""\r\n"two\nlines"',
    )
  })

  it('a formula in a student name cannot reach the spreadsheet as a formula', () => {
    const csv = toCsv(['Name'], [['=1+1']])
    expect(csv.split('\r\n')[1]).toBe("'=1+1")
  })
})
