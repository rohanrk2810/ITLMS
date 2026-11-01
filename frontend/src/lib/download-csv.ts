/**
 * Makes one cell safe to open in a spreadsheet.
 *
 * A cell that starts with = + - @ (or a tab or return) is read by Excel and Sheets as a formula, so a student who
 * registers as "=HYPERLINK(...)" would run it on the admin's machine. Such text gets a leading apostrophe. A phone
 * number such as "+91 98765 43210" and a negative number are not formulas and are left alone.
 */
export function safeCell(value: string | number | null | undefined): string {
  if (value == null) return ''
  if (typeof value === 'number') return String(value)
  const looksLikeNumber = /^[+-][\d\s().-]*$/.test(value)
  return /^[=@\t\r]/.test(value) || (/^[+-]/.test(value) && !looksLikeNumber) ? `'${value}` : value
}

/** The text of a CSV file for these rows. Fields with a comma, quote or line break are quoted. */
export function toCsv(header: string[], rows: (string | number | null | undefined)[][]): string {
  const cell = (value: string | number | null | undefined) => {
    const text = safeCell(value)
    return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
  }
  return [header, ...rows].map((row) => row.map(cell).join(',')).join('\r\n')
}

/** Saves rows as a CSV file the browser downloads. A leading BOM makes Excel read non-English names correctly. */
export function downloadCsv(filename: string, header: string[], rows: (string | number | null | undefined)[][]): void {
  const url = URL.createObjectURL(new Blob(['﻿', toCsv(header, rows)], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

/** Today's date for a file name: 2026-11-01. */
export function today(): string {
  return new Date().toISOString().slice(0, 10)
}
