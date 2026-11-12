import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { cashPreview, closeCashDay, recentCashClosings } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const todayIso = () => new Date().toISOString().slice(0, 10)

/** The day-end cash count: the cash in the drawer against the cash the system recorded. */
export function CashClosingCard() {
  const queryClient = useQueryClient()
  const canClose = hasRole(useAuthStore((state) => state.roles), ['ADMIN', 'FINANCE'])
  const [date, setDate] = useState(todayIso())
  const [counted, setCounted] = useState('')
  const [note, setNote] = useState('')

  const preview = useQuery({ queryKey: ['finance', 'cash', date], queryFn: () => cashPreview(date) })
  const recent = useQuery({ queryKey: ['finance', 'cash-recent'], queryFn: recentCashClosings })

  const close = useMutation({
    mutationFn: () => closeCashDay({ date, countedCash: Number(counted), note: note.trim() || undefined }),
    onSuccess: () => {
      toast.success('Day closed')
      setCounted('')
      setNote('')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not close the day.')),
  })

  const expected = preview.data?.expectedCash ?? 0
  const difference = counted === '' ? null : Number(counted) - expected

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Day-end cash count</CardTitle>
        <CardDescription>Count the cash in the drawer and compare it with what was recorded.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="cash-date">Day</Label>
            <Input id="cash-date" type="date" className="w-40" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <p className="text-sm">
            System recorded <span className="font-semibold">{formatMoney(expected)}</span> in cash
            {preview.data ? ` (${preview.data.cashPayments} payments)` : ''}
          </p>
        </div>

        {preview.data?.closing ? (
          <p className="text-sm text-muted-foreground">
            Closed: counted {formatMoney(preview.data.closing.countedCash)}, difference {formatMoney(preview.data.closing.difference)}
            {preview.data.closing.note ? ` (${preview.data.closing.note})` : ''}.
          </p>
        ) : (
          canClose && (
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex flex-col gap-1">
                <Label htmlFor="cash-counted">Cash counted</Label>
                <Input id="cash-counted" type="number" min={0} className="w-36" value={counted} onChange={(e) => setCounted(e.target.value)} />
              </div>
              {difference != null && (
                <span className={`pb-2 text-sm ${difference === 0 ? 'text-muted-foreground' : 'text-destructive'}`}>
                  Difference {formatMoney(difference)}
                </span>
              )}
              <div className="flex flex-col gap-1">
                <Label htmlFor="cash-note">Note{difference ? ' (required)' : ''}</Label>
                <Input id="cash-note" maxLength={255} className="w-64" value={note} onChange={(e) => setNote(e.target.value)} />
              </div>
              <Button disabled={counted === '' || close.isPending || (!!difference && !note.trim())} onClick={() => close.mutate()}>
                Close the day
              </Button>
            </div>
          )
        )}

        {recent.data && recent.data.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Day</TableHead>
                <TableHead>Recorded</TableHead>
                <TableHead>Counted</TableHead>
                <TableHead>Difference</TableHead>
                <TableHead>Note</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recent.data.slice(0, 10).map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{formatDate(c.date)}</TableCell>
                  <TableCell>{formatMoney(c.expectedCash)}</TableCell>
                  <TableCell>{formatMoney(c.countedCash)}</TableCell>
                  <TableCell className={c.difference === 0 ? '' : 'text-destructive'}>{formatMoney(c.difference)}</TableCell>
                  <TableCell>{c.note}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
