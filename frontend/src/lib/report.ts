import type { Branding } from '@/api/branding'

export type Cell = string | number | null | undefined

/**
 * A report as a person reads it: a title, a few facts about what it covers, and a table. The same object becomes a
 * CSV, an Excel file or a printed page with the institute's letterhead.
 */
export interface Report {
  title: string
  /** What the report covers: ["Batch", "JFS-2026-B01"], ["Trainer", "Ravi Kulkarni"]. Shown under the title. */
  info: [string, string][]
  header: string[]
  rows: Cell[][]
  /** The file name without the date and the extension. */
  fileName: string
}

/** What goes at the top of every printed or Excel report: the institute's own name, address and logo. */
export interface Letterhead {
  name: string
  tagline: string | null
  /** Address, phone, email and website, one line each, left out when empty. */
  lines: string[]
  /** An address the browser can load the logo from, or null when the institute has not set one. */
  logoUrl: string | null
}

export function letterheadOf(branding: Branding | undefined, logoUrl: string | null, fallbackName: string): Letterhead {
  const contact = [branding?.contactPhone, branding?.contactEmail, branding?.website].filter((v): v is string => !!v?.trim())
  return {
    name: branding?.name?.trim() || fallbackName,
    tagline: branding?.tagline?.trim() || null,
    lines: [branding?.address?.trim(), contact.join('  |  ')].filter((v): v is string => !!v),
    logoUrl,
  }
}

export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** A row that opens a section of a long report ("# Attendance") instead of holding a value. */
export const isSectionRow = (row: Cell[]) => typeof row[0] === 'string' && row[0].startsWith('# ')

const CHUNK = 6

/** A table of more than this many columns is printed on a landscape page; many more also get a smaller type. */
export const WIDE_COLUMNS = 8
export const VERY_WIDE_COLUMNS = 14

/**
 * The page that is printed (or saved as PDF): letterhead, a heavy rule, the title, the facts in a small table (six to
 * a row, like a grade card), then the data. Every value is escaped, so a student named "<script>" prints as text.
 */
export function reportHtml(report: Report, letterhead: Letterhead, madeOn: string): string {
  const e = escapeHtml
  const logo = letterhead.logoUrl ? `<img class="logo" src="${e(letterhead.logoUrl)}" alt="" />` : ''
  // Faint, centred behind the table on every printed page (a fixed element repeats on each page when printed).
  const watermark = letterhead.logoUrl ? `<img class="watermark" src="${e(letterhead.logoUrl)}" alt="" />` : ''
  const infoTables: string[] = []
  for (let i = 0; i < report.info.length; i += CHUNK) {
    const part = report.info.slice(i, i + CHUNK)
    infoTables.push(
      `<table class="info"><thead><tr>${part.map(([k]) => `<th>${e(k)}</th>`).join('')}</tr></thead>` +
        `<tbody><tr>${part.map(([, v]) => `<td>${e(v)}</td>`).join('')}</tr></tbody></table>`,
    )
  }
  const width = report.header.length
  const landscape = width > WIDE_COLUMNS
  const dataFont = width > VERY_WIDE_COLUMNS ? '7.5pt' : landscape ? '8.5pt' : '9.5pt'
  const body = report.rows
    .map((row) =>
      isSectionRow(row)
        ? `<tr class="section"><td colspan="${width}">${e(String(row[0]).slice(2))}</td></tr>`
        : `<tr>${report.header.map((_, i) => `<td${typeof row[i] === 'number' ? ' class="num"' : ''}>${e(row[i])}</td>`).join('')}</tr>`,
    )
    .join('')

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<title>${e(report.title)} - ${e(letterhead.name)}</title>
<style>
  @page { size: ${landscape ? 'A4 landscape' : 'A4'}; margin: 12mm 10mm 14mm; @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 9pt Georgia, serif; color: #555; } }
  * { box-sizing: border-box; }
  body { margin: 0; font: 11pt/1.35 "Times New Roman", Georgia, serif; color: #000; }
  .letterhead { display: flex; align-items: center; gap: 18px; padding-bottom: 10px; }
  .logo { max-height: 70px; max-width: 150px; object-fit: contain; }
  .watermark { position: fixed; top: 50%; left: 50%; width: 55%; max-height: 55%; object-fit: contain; transform: translate(-50%, -50%); opacity: 0.08; z-index: -1; pointer-events: none; }
  .name { font-size: 22pt; font-weight: 700; line-height: 1.1; }
  .tagline { font-size: 12pt; color: #555; }
  .lines { font-size: 9.5pt; color: #333; margin-top: 2px; }
  .rule { border: 0; border-top: 3px solid #000; margin: 4px 0 14px; }
  h1.title { text-align: center; font-size: 17pt; margin: 6px 0 14px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 14px; }
  th, td { border: 1.5px solid #333; padding: 6px 8px; text-align: center; vertical-align: middle; }
  th { font-weight: 700; }
  .info th { font-size: 11pt; }
  .info td { font-size: 10.5pt; }
  .data { font-size: ${dataFont}; overflow-wrap: break-word; }
  .data th, .data td { padding: ${landscape ? '4px 5px' : '6px 8px'}; }
  .data thead th { background: #eee; }
  .data thead { display: table-header-group; }
  .data tr { page-break-inside: avoid; }
  .data td { text-align: left; }
  .data td.num { text-align: right; }
  .data tr.section td { background: #f3f3f3; font-weight: 700; text-align: left; }
  .foot { font-size: 9pt; color: #555; margin-top: 8px; }
</style>
</head>
<body>
  ${watermark}
  <div class="letterhead">
    ${logo}
    <div>
      <div class="name">${e(letterhead.name)}</div>
      ${letterhead.tagline ? `<div class="tagline">${e(letterhead.tagline)}</div>` : ''}
      ${letterhead.lines.map((l) => `<div class="lines">${e(l)}</div>`).join('')}
    </div>
  </div>
  <hr class="rule" />
  <h1 class="title">${e(report.title)}</h1>
  ${infoTables.join('')}
  <table class="data">
    <thead><tr>${report.header.map((h) => `<th>${e(h)}</th>`).join('')}</tr></thead>
    <tbody>${body}</tbody>
  </table>
  <div class="foot">${report.rows.filter((r) => !isSectionRow(r)).length} row(s). Made on ${e(madeOn)}.</div>
</body>
</html>`
}

/**
 * The same report with only the chosen columns, in their original order. `keep` holds column positions.
 * A section row ("# Attendance") keeps its heading even when the first column is dropped, so a report with
 * sections still reads as one.
 */
export function pickColumns(report: Report, keep: number[]): Report {
  const wanted = [...new Set(keep)].sort((a, b) => a - b)
  return {
    ...report,
    header: wanted.map((i) => report.header[i] ?? ''),
    rows: report.rows.map((row) =>
      isSectionRow(row) ? [row[0], ...wanted.slice(1).map(() => '')] : wanted.map((i) => row[i]),
    ),
  }
}
