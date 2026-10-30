import { useEffect, useState } from 'react'
import { Loader2 } from 'lucide-react'

import { type DataTable, questionDataset } from '@/api/live-classes'
import { Button } from '@/components/ui/button'

/** Rows drawn at first and per "Show more": a table of thousands of rows stays quick, and every row is still reachable. */
const PAGE = 200

/** Big databases are fetched once per question and kept, so reopening or re-polling does not download them again. */
const fetched = new Map<number, DataTable[]>()

/**
 * The code and tables a question shows. Code is monospace; each table is a real grid with ruled cells, a heading
 * band and its row count, like a database client, so a student can read a result off it without squinting.
 * A database too big to travel with the question is passed as {@code datasetQuestionId} and loaded here.
 */
export function QuestionMaterial({ snippet, dataset, datasetQuestionId }: {
  snippet?: string | null
  dataset?: DataTable[] | null
  datasetQuestionId?: number
}) {
  const [loaded, setLoaded] = useState<DataTable[] | null>(
    datasetQuestionId != null ? (fetched.get(datasetQuestionId) ?? null) : null,
  )
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (datasetQuestionId == null || fetched.has(datasetQuestionId)) return
    questionDataset(datasetQuestionId)
      .then((tables) => {
        fetched.set(datasetQuestionId, tables)
        setLoaded(tables)
      })
      .catch(() => setFailed(true))
  }, [datasetQuestionId])

  const tables = dataset ?? loaded
  const loading = datasetQuestionId != null && !tables && !failed
  if (!snippet && (!tables || tables.length === 0) && !loading && !failed) return null

  return (
    <div className="flex flex-col gap-3">
      {snippet && (
        <pre className="max-h-64 overflow-auto rounded-md border bg-muted/50 p-3 font-mono text-sm leading-6">{snippet}</pre>
      )}
      {loading && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> Loading the database...
        </p>
      )}
      {failed && <p className="text-sm text-destructive">The database could not be loaded. Reload the page to try again.</p>}
      {tables?.map((table) => <TableGrid key={table.name} table={table} />)}
    </div>
  )
}

function TableGrid({ table }: { table: DataTable }) {
  const [shown, setShown] = useState(PAGE)
  const rows = table.rows.slice(0, shown)
  const rest = table.rows.length - rows.length

  return (
    <div className="overflow-hidden rounded-md border">
      <div className="flex items-baseline justify-between gap-2 border-b bg-primary/10 px-3 py-1.5">
        <span className="font-mono text-sm font-semibold">{table.name}</span>
        <span className="text-xs text-muted-foreground">
          {table.columns.length} columns · {table.rows.length.toLocaleString()} row{table.rows.length === 1 ? '' : 's'}
        </span>
      </div>
      <div className="max-h-72 overflow-auto">
        <table className="w-full border-collapse text-sm">
          <thead className="sticky top-0 bg-muted">
            <tr>
              {table.columns.map((column, i) => (
                <th key={i} className="whitespace-nowrap border-b border-r px-3 py-1.5 text-left font-mono font-semibold last:border-r-0">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
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
      {rest > 0 && (
        <div className="flex items-center justify-between border-t bg-muted/30 px-3 py-1.5 text-xs text-muted-foreground">
          <span>{rest.toLocaleString()} more row{rest === 1 ? '' : 's'} not shown</span>
          <span className="flex gap-1">
            <Button size="sm" variant="ghost" className="h-6 px-2 text-xs" onClick={() => setShown((n) => n + PAGE)}>
              Show {Math.min(PAGE, rest)} more
            </Button>
            <Button size="sm" variant="ghost" className="h-6 px-2 text-xs" onClick={() => setShown(table.rows.length)}>
              Show all
            </Button>
          </span>
        </div>
      )}
    </div>
  )
}

/** One line of CSV (or tab separated, as pasted from a spreadsheet) into cells; "quoted, values" keep their commas. */
function splitLine(line: string): string[] {
  const sep = line.includes('\t') ? '\t' : ','
  const out: string[] = []
  let cell = ''
  let quoted = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (quoted) {
      if (c === '"' && line[i + 1] === '"') {
        cell += '"'
        i++
      } else if (c === '"') {
        quoted = false
      } else {
        cell += c
      }
    } else if (c === '"' && cell.trim() === '') {
      quoted = true
      cell = ''
    } else if (c === sep) {
      out.push(cell.trim())
      cell = ''
    } else {
      cell += c
    }
  }
  out.push(cell.trim())
  return out
}

/** "id,name" on the first line, one row per line after it. */
export function parseTable(name: string, text: string): DataTable | null {
  const lines = text.split(/\r?\n/).filter((l) => l.trim() !== '')
  if (!name.trim() || lines.length === 0) return null
  const columns = splitLine(lines[0])
  const rows = lines.slice(1).map((line) => {
    const cells = splitLine(line)
    // A short row is padded and a long one trimmed, so the grid always lines up.
    return columns.map((_, i) => cells[i] ?? '')
  })
  return { name: name.trim(), columns, rows }
}

function quoteIfNeeded(cell: string): string {
  return /[",\n\t]/.test(cell) ? `"${cell.replace(/"/g, '""')}"` : cell
}

export function tableToText(table: DataTable): string {
  return [table.columns, ...table.rows].map((r) => r.map(quoteIfNeeded).join(',')).join('\n')
}
