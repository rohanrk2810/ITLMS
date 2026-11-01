import { describe, expect, it } from 'vitest'

import type { Letterhead, Report } from './report'
import { LOGO_ROWS, sheetNameOf, xlsxSheet } from './report-xlsx'

const letterhead: Letterhead = { name: 'Test Institute', tagline: 'Learn well', lines: ['1 Main Road'], logoUrl: null }
const report: Report = {
  title: 'Batch attendance',
  fileName: 'attendance-b1',
  info: [['Batch', 'JFS-B1'], ['Trainer', 'Ravi K']],
  header: ['Student', 'Attendance %', 'Below the limit'],
  rows: [['Asha Patil', 90, 'No'], ['=1+1', null, 'Yes']],
}

const firstValue = (row: unknown[]) => (row[0] as { value?: unknown } | null)?.value

describe('xlsxSheet (the Excel report)', () => {
  it('starts with the institute name, then the tagline and address, then the title and the facts', () => {
    const { data } = xlsxSheet(report, letterhead, false)
    expect(data.map(firstValue).slice(0, 7)).toEqual(['Test Institute', 'Learn well', '1 Main Road', undefined, 'Batch attendance', 'Batch', 'Trainer'])
    expect(data[0][0]).toMatchObject({ fontWeight: 'bold', fontSize: 16, columnSpan: 3 })
  })

  it('leaves room at the top for the logo only when there is one', () => {
    const without = xlsxSheet(report, letterhead, false).data
    const withLogo = xlsxSheet(report, letterhead, true).data
    expect(withLogo.length - without.length).toBe(LOGO_ROWS)
    expect(firstValue(withLogo[LOGO_ROWS])).toBe('Test Institute')
  })

  it('the table header is bold and shaded, and stays in view while scrolling', () => {
    const sheet = xlsxSheet(report, letterhead, false)
    const header = sheet.data[sheet.stickyRows - 1]
    expect(header.map((c) => (c as { value: string }).value)).toEqual(['Student', 'Attendance %', 'Below the limit'])
    expect(header[0]).toMatchObject({ fontWeight: 'bold', backgroundColor: '#eeeeee' })
  })

  it('numbers stay numbers and text stays text, and a missing value is an empty cell', () => {
    const sheet = xlsxSheet(report, letterhead, false)
    const first = sheet.data[sheet.stickyRows]
    expect((first[1] as { value: unknown }).value).toBe(90)
    const second = sheet.data[sheet.stickyRows + 1]
    expect((second[0] as { value: unknown }).value).toBe('=1+1') // text in an .xlsx, never a formula
    expect((second[1] as { value: unknown }).value).toBe('')
  })

  it('a section row spans the table', () => {
    const sections: Report = { ...report, header: ['Item', 'Value'], rows: [['# Attendance', ''], ['Overall %', 80]] }
    const sheet = xlsxSheet(sections, letterhead, false)
    const row = sheet.data[sheet.stickyRows]
    expect(row[0]).toMatchObject({ value: 'Attendance', fontWeight: 'bold', columnSpan: 2 })
  })

  it('column widths fit the longest value, within limits', () => {
    const wide: Report = { ...report, rows: [['A very long student name indeed, longer than anything else here', 1, 'x']] }
    const { columns } = xlsxSheet(wide, letterhead, false)
    expect(columns).toHaveLength(3)
    expect(columns[0].width).toBe(50)
    expect(columns[2].width).toBeGreaterThanOrEqual(10)
  })

  it('a two-column report still lays out the facts and spans', () => {
    const narrow: Report = { ...report, header: ['Item', 'Value'], rows: [['x', 1]] }
    const { data } = xlsxSheet(narrow, letterhead, false)
    expect(data[0][0]).toMatchObject({ columnSpan: 2 })
  })
})

describe('sheetNameOf', () => {
  it('is a name Excel accepts', () => {
    expect(sheetNameOf('Batch: attendance / 2026?')).toBe('Batch attendance 2026')
    expect(sheetNameOf('A'.repeat(60))).toHaveLength(31)
    expect(sheetNameOf('///')).toBe('Report')
  })
})
