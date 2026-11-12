import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { bankCandidates, bankLines, bankSummary, type BankLineView, importBankLines, matchBankLine, unmatchBankLine } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { parseBankCsv } from '@/lib/bank-csv'
import { formatDate, formatMoney } from '@/lib/format'

/** Match the bank statement to the books: import its CSV, and settle what could not be matched on its own. */
export function BankReconciliationCard() {
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const summary = useQuery({ queryKey: ['finance', 'bank-summary'], queryFn: bankSummary })
  const open = useQuery({ queryKey: ['finance', 'bank-open'], queryFn: () => bankLines(true) })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['finance'] })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const parsed = parseBankCsv(await file.text())
      if (parsed.problem) throw new Error(parsed.problem)
      return importBankLines(parsed.lines)
    },
    onSuccess: (r) => {
      toast.success(`${r.imported} lines added, ${r.duplicates} already there, ${r.matched} matched`)
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, error instanceof Error ? error.message : 'Could not read the statement.')),
  })

  const s = summary.data
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Bank reconciliation</CardTitle>
        <CardDescription>
          Import the bank statement (CSV with Date, Narration, Reference, Debit, Credit). Lines are matched to payments, expenses and
          refunds by reference or by amount; what is left over is what to chase.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <input ref={input} type="file" accept=".csv,text/csv" className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (file) upload.mutate(file)
            }} />
          <Button variant="outline" disabled={upload.isPending} onClick={() => input.current?.click()}>
            <Upload className="size-4" /> {upload.isPending ? 'Reading...' : 'Import statement CSV'}
          </Button>
          {s && (
            <span className="text-sm text-muted-foreground">
              {s.lines} lines: <span className="font-medium text-foreground">{s.matched} matched</span>, {s.unmatched} not matched
              {s.unmatched > 0 && ` (${formatMoney(s.unmatchedIn)} in, ${formatMoney(s.unmatchedOut)} out)`}
            </span>
          )}
        </div>

        {open.data && open.data.length > 0 && (
          <div>
            <h3 className="mb-2 text-sm font-medium text-muted-foreground">In the bank, not in the books</h3>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Narration</TableHead>
                  <TableHead>In</TableHead>
                  <TableHead>Out</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {open.data.slice(0, 50).map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>{formatDate(l.date)}</TableCell>
                    <TableCell className="max-w-72 whitespace-normal">
                      {l.description}
                      {l.reference && <div className="font-mono text-xs text-muted-foreground">{l.reference}</div>}
                    </TableCell>
                    <TableCell>{l.credit > 0 ? formatMoney(l.credit) : ''}</TableCell>
                    <TableCell>{l.debit > 0 ? formatMoney(l.debit) : ''}</TableCell>
                    <TableCell className="text-right"><MatchDialog line={l} onDone={refresh} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        {s && s.notOnStatement.length > 0 && (
          <div>
            <h3 className="mb-2 text-sm font-medium text-muted-foreground">In the books as bank money, not on the statement</h3>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>What</TableHead>
                  <TableHead>Reference</TableHead>
                  <TableHead>Amount</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {s.notOnStatement.slice(0, 50).map((e) => (
                  <TableRow key={`${e.kind}-${e.id}`}>
                    <TableCell>{formatDate(e.date)}</TableCell>
                    <TableCell>{e.label}</TableCell>
                    <TableCell className="font-mono text-xs">{e.reference}</TableCell>
                    <TableCell>{formatMoney(e.amount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function MatchDialog({ line, onDone }: { line: BankLineView; onDone: () => void }) {
  const [openIt, setOpenIt] = useState(false)
  const candidates = useQuery({ queryKey: ['finance', 'bank-candidates', line.id], queryFn: () => bankCandidates(line.id), enabled: openIt })
  const match = useMutation({
    mutationFn: ({ kind, id }: { kind: string; id: number }) => matchBankLine(line.id, kind, id),
    onSuccess: () => {
      toast.success('Matched')
      setOpenIt(false)
      onDone()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not match.')),
  })
  const undo = useMutation({ mutationFn: () => unmatchBankLine(line.id), onSuccess: onDone })
  return (
    <Dialog open={openIt} onOpenChange={setOpenIt}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">Match</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{formatMoney(line.credit > 0 ? line.credit : line.debit)} on {formatDate(line.date)}</DialogTitle>
          <DialogDescription>
            Books entries of exactly this amount that no other bank line is matched to.
          </DialogDescription>
        </DialogHeader>
        {candidates.isSuccess && candidates.data.length === 0 && (
          <p className="text-sm text-muted-foreground">
            Nothing in the books has this amount. If the money is real, record the payment or expense first, then match it.
          </p>
        )}
        <div className="flex flex-col gap-2">
          {candidates.data?.map((e) => (
            <div key={`${e.kind}-${e.id}`} className="flex items-center justify-between gap-2 rounded-md border p-2 text-sm">
              <div>
                <p className="font-medium">{e.label}</p>
                <p className="text-xs text-muted-foreground">{formatDate(e.date)}{e.reference ? ` - ${e.reference}` : ''}</p>
              </div>
              <Button size="sm" disabled={match.isPending} onClick={() => match.mutate({ kind: e.kind, id: e.id })}>Match</Button>
            </div>
          ))}
        </div>
        {line.matchedKind && <Button variant="ghost" onClick={() => undo.mutate()}>Undo the match</Button>}
      </DialogContent>
    </Dialog>
  )
}
