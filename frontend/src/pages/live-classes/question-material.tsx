import type { DataTable } from '@/api/live-classes'

/**
 * The code and tables a question shows. Code is monospace; each table is a real grid with ruled cells, a heading
 * band and its row count, like a database client, so a student can read a result off it without squinting.
 */
export function QuestionMaterial({ snippet, dataset }: { snippet?: string | null; dataset?: DataTable[] | null }) {
  if (!snippet && (!dataset || dataset.length === 0)) return null
  return (
    <div className="flex flex-col gap-3">
      {snippet && (
        <pre className="max-h-64 overflow-auto rounded-md border bg-muted/50 p-3 font-mono text-sm leading-6">{snippet}</pre>
      )}
      {dataset?.map((table) => (
        <div key={table.name} className="overflow-hidden rounded-md border">
          <div className="flex items-baseline justify-between gap-2 border-b bg-primary/10 px-3 py-1.5">
            <span className="font-mono text-sm font-semibold">{table.name}</span>
            <span className="text-xs text-muted-foreground">
              {table.columns.length} columns · {table.rows.length} row{table.rows.length === 1 ? '' : 's'}
            </span>
          </div>
          <div className="max-h-72 overflow-auto">
            <table className="w-full border-collapse text-sm">
              <thead className="sticky top-0 bg-muted">
                <tr>
                  {table.columns.map((column) => (
                    <th key={column} className="whitespace-nowrap border-b border-r px-3 py-1.5 text-left font-mono font-semibold last:border-r-0">
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {table.rows.map((row, i) => (
                  <tr key={i} className="even:bg-muted/30">
                    {row.map((cell, j) => (
                      <td key={j} className="whitespace-nowrap border-b border-r px-3 py-1 font-mono last:border-r-0">
                        {cell === '' ? <span className="text-muted-foreground">NULL</span> : cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  )
}

/** "id,name" on the first line, one row per line after it. Commas, or tabs when pasted from a spreadsheet. */
export function parseTable(name: string, text: string): DataTable | null {
  const lines = text.split(/\r?\n/).filter((l) => l.trim() !== '')
  if (!name.trim() || lines.length === 0) return null
  const split = (line: string) => line.split(line.includes('\t') ? '\t' : ',').map((c) => c.trim())
  const columns = split(lines[0])
  const rows = lines.slice(1).map((line) => {
    const cells = split(line)
    // A short row is padded and a long one trimmed, so the grid always lines up.
    return columns.map((_, i) => cells[i] ?? '')
  })
  return { name: name.trim(), columns, rows }
}

export function tableToText(table: DataTable): string {
  return [table.columns, ...table.rows].map((r) => r.join(',')).join('\n')
}
