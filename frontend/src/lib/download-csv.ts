/** Saves rows as a CSV file the browser downloads. A leading BOM makes Excel read non-English names correctly. */
export function downloadCsv(filename: string, header: string[], rows: (string | number | null | undefined)[][]): void {
  const cell = (value: string | number | null | undefined) => {
    const text = value == null ? '' : String(value)
    return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
  }
  const csv = [header, ...rows].map((row) => row.map(cell).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}
