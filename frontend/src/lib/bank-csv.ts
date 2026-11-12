export interface BankCsvLine {
  date: string
  description: string
  reference: string
  debit: number
  credit: number
}

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']

/** Splits one CSV record, honouring "quoted, fields" and "" inside them. */
export function splitCsvLine(line: string): string[] {
  const out: string[] = []
  let cell = ''
  let quoted = false
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i]
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') {
        cell += '"'
        i += 1
      } else if (ch === '"') {
        quoted = false
      } else {
        cell += ch
      }
    } else if (ch === '"') {
      quoted = true
    } else if (ch === ',') {
      out.push(cell.trim())
      cell = ''
    } else {
      cell += ch
    }
  }
  out.push(cell.trim())
  return out
}

/** 12/03/2026, 12-03-2026, 2026-03-12 or 12-Mar-2026 as 2026-03-12; null when it is none of them. */
export function bankDate(text: string): string | null {
  const t = text.trim()
  let m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(t)
  if (m) return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`
  m = /^(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})$/.exec(t)
  if (m) return `${m[3].length === 2 ? `20${m[3]}` : m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`
  m = /^(\d{1,2})[ -]([A-Za-z]{3})[a-z]*[ -](\d{2,4})$/.exec(t)
  if (m) {
    const month = MONTHS.indexOf(m[2].toLowerCase()) + 1
    if (month > 0) return `${m[3].length === 2 ? `20${m[3]}` : m[3]}-${String(month).padStart(2, '0')}-${m[1].padStart(2, '0')}`
  }
  return null
}

const amount = (text: string | undefined): number => {
  const n = Number((text ?? '').replace(/[,\s]/g, '').replace(/^-/, ''))
  return Number.isFinite(n) ? n : 0
}

const find = (header: string[], names: string[]): number =>
  header.findIndex((h) => names.some((n) => h.toLowerCase().includes(n)))

/**
 * Reads a bank statement exported as CSV: finds the columns by their headings (date, narration, reference, debit or
 * withdrawal, credit or deposit) and returns the lines that have a date and an amount. `skipped` counts rows that did
 * not, such as opening balance lines.
 */
export function parseBankCsv(text: string): { lines: BankCsvLine[]; skipped: number; problem?: string } {
  const rows = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((r) => r.trim() !== '')
  const headerAt = rows.findIndex((r) => {
    const cells = splitCsvLine(r).map((c) => c.toLowerCase())
    return cells.some((c) => c.includes('date')) && cells.some((c) => /debit|withdraw|credit|deposit|dr|cr/.test(c))
  })
  if (headerAt < 0) return { lines: [], skipped: 0, problem: 'Could not find the heading row (Date, Narration, Debit, Credit).' }
  const header = splitCsvLine(rows[headerAt])
  const dateCol = find(header, ['date'])
  const descCol = find(header, ['narration', 'description', 'particular', 'detail', 'remark'])
  const refCol = find(header, ['reference', 'ref', 'utr', 'chq', 'cheque'])
  const debitCol = find(header, ['debit', 'withdraw'])
  const creditCol = find(header, ['credit', 'deposit'])
  if (dateCol < 0 || debitCol < 0 || creditCol < 0) {
    return { lines: [], skipped: 0, problem: 'The statement needs a Date column and separate Debit and Credit columns.' }
  }
  const lines: BankCsvLine[] = []
  let skipped = 0
  for (const row of rows.slice(headerAt + 1)) {
    const cells = splitCsvLine(row)
    const date = bankDate(cells[dateCol] ?? '')
    const debit = amount(cells[debitCol])
    const credit = amount(cells[creditCol])
    if (!date || (debit > 0) === (credit > 0)) {
      skipped += 1
      continue
    }
    lines.push({ date, description: descCol >= 0 ? (cells[descCol] ?? '') : '', reference: refCol >= 0 ? (cells[refCol] ?? '') : '', debit, credit })
  }
  return { lines, skipped }
}
