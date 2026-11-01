import { downloadCsv, today } from './download-csv'
import { type Letterhead, type Report, reportHtml } from './report'
import { LOGO_ROWS, xlsxSheet } from './report-xlsx'

/** Saves the table as CSV, with the institute's name and the facts above it left out so it stays plain data. */
export function saveCsv(report: Report): void {
  downloadCsv(`${report.fileName}-${today()}.csv`, report.header, report.rows)
}

/**
 * Saves an Excel file with the institute's letterhead (logo, name, address) above the title, the facts and the table.
 * The Excel writer is loaded only now, so the rest of the app does not carry it.
 */
export async function saveXlsx(report: Report, letterhead: Letterhead): Promise<void> {
  const { default: writeExcelFile } = await import('write-excel-file/browser')
  const logo = await loadLogo(letterhead.logoUrl)
  const sheet = xlsxSheet(report, letterhead, logo !== null)
  await writeExcelFile(sheet.data as never, {
    sheet: sheet.sheetName,
    columns: sheet.columns,
    stickyRowsCount: sheet.stickyRows,
    images: logo
      ? [{ content: logo.blob, contentType: logo.type, width: logo.width, height: logo.height, dpi: 96, anchor: { row: 1, column: 1 }, offsetX: 4, offsetY: 4 }]
      : undefined,
  } as never).toFile(`${report.fileName}-${today()}.xlsx`)
}

async function loadLogo(url: string | null) {
  if (!url) return null
  try {
    const response = await fetch(url)
    if (!response.ok) return null
    const blob = await response.blob()
    if (blob.type !== 'image/png' && blob.type !== 'image/jpeg') return null // the Excel writer takes these two
    const bitmap = await createImageBitmap(blob)
    const height = LOGO_ROWS * 18
    const width = Math.round((bitmap.width / bitmap.height) * height)
    bitmap.close()
    return { blob, type: blob.type, width, height }
  } catch {
    return null // a report without a logo is better than no report
  }
}

let printFrame: HTMLIFrameElement | null = null

/**
 * Opens the browser's print dialog on the report with the institute's letterhead. "Save as PDF" in that dialog gives
 * the PDF. It prints from a hidden frame, so it is not a pop-up and any script's name prints correctly.
 */
export async function printReport(report: Report, letterhead: Letterhead): Promise<void> {
  printFrame?.remove()
  const frame = document.createElement('iframe')
  frame.title = 'Report print preview'
  frame.setAttribute('aria-hidden', 'true')
  frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
  printFrame = frame
  document.body.appendChild(frame)

  await new Promise<void>((resolve) => {
    frame.addEventListener('load', () => resolve(), { once: true })
    frame.srcdoc = reportHtml(report, letterhead, new Date().toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' }))
  })
  const images = Array.from(frame.contentDocument?.images ?? [])
  await Promise.all(images.map((img) => (img.complete ? Promise.resolve() : new Promise((r) => { img.onload = img.onerror = () => r(null) }))))

  frame.contentWindow?.focus()
  frame.contentWindow?.print()
  // Kept for a minute: the dialog can still be open, and a test can look at what was printed.
  setTimeout(() => {
    if (printFrame === frame) printFrame = null
    frame.remove()
  }, 60_000)
}
