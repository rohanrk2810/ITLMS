import { isSectionRow, type Cell, type Letterhead, type Report } from './report'

/** Rows left empty at the top of the sheet for the logo, which Excel keeps in a layer above the cells. */
export const LOGO_ROWS = 4

export interface XlsxSheet {
  data: unknown[][]
  columns: { width: number }[]
  /** How many rows stay in view while scrolling: everything down to the table's header. */
  stickyRows: number
  /** A sheet name Excel accepts: at most 31 characters, none of : \ / ? * [ ]. */
  sheetName: string
}

const BORDER = { borderStyle: 'thin', borderColor: '#555555' } as const

/**
 * The cells of an Excel report: the institute's name and address, the title, the facts, then the table with
 * borders and a shaded header. Text stays text (a name that begins with = is not a formula in a real .xlsx).
 */
export function xlsxSheet(report: Report, letterhead: Letterhead, hasLogo: boolean): XlsxSheet {
  const width = Math.max(report.header.length, 2)
  const pad = (n: number) => Array.from({ length: n }, () => null)
  const spanned = (value: string, style: Record<string, unknown> = {}) => [{ value, columnSpan: width, ...style }, ...pad(width - 1)]

  const data: unknown[][] = []
  if (hasLogo) {
    for (let i = 0; i < LOGO_ROWS; i += 1) data.push([null])
  }
  data.push(spanned(letterhead.name, { fontWeight: 'bold', fontSize: 16 }))
  if (letterhead.tagline) data.push(spanned(letterhead.tagline, { fontSize: 11 }))
  for (const line of letterhead.lines) data.push(spanned(line, { fontSize: 9 }))
  data.push([null])
  data.push(spanned(report.title, { fontWeight: 'bold', fontSize: 14, align: 'center' }))
  for (const [label, value] of report.info) {
    data.push([{ value: label, fontWeight: 'bold' }, { value, columnSpan: width - 1, align: 'left' }, ...pad(width - 2)])
  }
  data.push([null])

  const headerRow = data.length + 1
  data.push(report.header.map((h) => ({ value: h, fontWeight: 'bold', backgroundColor: '#eeeeee', ...BORDER })))
  for (const row of report.rows) {
    if (isSectionRow(row)) {
      data.push([{ value: String(row[0]).slice(2), fontWeight: 'bold', backgroundColor: '#f3f3f3', columnSpan: report.header.length, ...BORDER }, ...pad(report.header.length - 1)])
      continue
    }
    data.push(report.header.map((_, i) => cellOf(row[i])))
  }

  const widths = report.header.map((h, i) => {
    const longest = Math.max(h.length, ...report.rows.filter((r) => !isSectionRow(r)).map((r) => String(r[i] ?? '').length))
    return { width: Math.min(Math.max(longest + 2, 10), 50) }
  })

  return { data, columns: widths, stickyRows: headerRow, sheetName: sheetNameOf(report.title) }
}

function cellOf(value: Cell) {
  if (typeof value === 'number') return { value, ...BORDER }
  return { value: value == null ? '' : String(value), ...BORDER }
}

export function sheetNameOf(title: string): string {
  const cleaned = title.replace(/[:\\/?*[\]]/g, ' ').replace(/\s+/g, ' ').trim()
  return (cleaned || 'Report').slice(0, 31)
}
