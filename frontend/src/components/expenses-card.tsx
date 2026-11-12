import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { addExpense, EXPENSE_CATEGORIES, expenseSummary, listExpenses, voidExpense } from '@/api/finance'
import { apiErrorMessage } from '@/api/client'
import { ExportMenu } from '@/components/export-menu'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import type { Report } from '@/lib/report'

const iso = (d: Date) => d.toISOString().slice(0, 10)

/** What the institute spends, and the profit or loss of the period (fees collected less expenses). */
export function ExpensesCard() {
  const queryClient = useQueryClient()
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth(), 1)))
  const [to, setTo] = useState(iso(now))
  const [category, setCategory] = useState('SALARY')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [paidTo, setPaidTo] = useState('')
  const [gstIn, setGstIn] = useState('')

  const rows = useQuery({ queryKey: ['finance', 'expenses', from, to], queryFn: () => listExpenses(from, to), enabled: from <= to })
  const summary = useQuery({ queryKey: ['finance', 'expense-summary', from, to], queryFn: () => expenseSummary(from, to), enabled: from <= to })

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['finance'] })
  const add = useMutation({
    mutationFn: () => addExpense({ category, amount: Number(amount), description: description.trim(), paidTo: paidTo.trim() || undefined, gstAmount: Number(gstIn) > 0 ? Number(gstIn) : undefined }),
    onSuccess: () => {
      toast.success('Expense recorded')
      setAmount('')
      setDescription('')
      setPaidTo('')
      setGstIn('')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not record the expense.')),
  })
  const remove = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => voidExpense(id, reason),
    onSuccess: refresh,
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not void the expense.')),
  })

  async function build(): Promise<Report> {
    const data = rows.data ?? (await listExpenses(from, to))
    const good = data.filter((e) => !e.voided)
    return {
      title: 'Expenses',
      fileName: 'expenses',
      info: [
        ['Period', `${from} to ${to}`],
        ['Expenses', String(good.length)],
        ['Total', String(good.reduce((a, e) => a + e.amount, 0))],
      ],
      header: ['Date', 'Category', 'Description', 'Paid to', 'Reference', 'Amount', 'Status'],
      rows: data.map((e) => [e.date, e.category, e.description, e.paidTo, e.referenceNo, e.amount, e.voided ? 'VOID' : '']),
    }
  }

  const s = summary.data
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Expenses and profit</CardTitle>
        <CardDescription>What the institute spent, against the fees collected in the same days.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="exp-from">From</Label>
            <Input id="exp-from" type="date" className="w-40" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="exp-to">To</Label>
            <Input id="exp-to" type="date" className="w-40" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </div>
          <ExportMenu label="Download expenses" build={build} />
        </div>

        {s && (
          <div className="flex flex-wrap gap-6 text-sm">
            <span>Collected <span className="font-semibold">{formatMoney(s.collected)}</span></span>
            <span>Spent <span className="font-semibold">{formatMoney(s.spent)}</span></span>
            <span className={s.profit < 0 ? 'text-destructive' : ''}>
              {s.profit < 0 ? 'Loss' : 'Profit'} <span className="font-semibold">{formatMoney(Math.abs(s.profit))}</span>
            </span>
            {Object.entries(s.byCategory).map(([k, v]) => (
              <span key={k} className="text-muted-foreground">{k}: <span className="font-medium text-foreground">{formatMoney(v)}</span></span>
            ))}
          </div>
        )}

        <div className="flex flex-wrap items-end gap-2">
          <select aria-label="Category" value={category} onChange={(e) => setCategory(e.target.value)}
            className="h-9 rounded-md border bg-transparent px-3 text-sm">
            {EXPENSE_CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <Input aria-label="Amount" type="number" min={1} placeholder="Amount" className="w-28" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <Input aria-label="Description" placeholder="What for" maxLength={255} className="w-56" value={description} onChange={(e) => setDescription(e.target.value)} />
          <Input aria-label="GST in this amount" type="number" min={0} placeholder="GST in it (optional)" className="w-40" value={gstIn} onChange={(e) => setGstIn(e.target.value)} />
          <Input aria-label="Paid to" placeholder="Paid to (optional)" maxLength={120} className="w-44" value={paidTo} onChange={(e) => setPaidTo(e.target.value)} />
          <Button disabled={!(Number(amount) > 0) || !description.trim() || add.isPending} onClick={() => add.mutate()}>
            Add expense
          </Button>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>For</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.data?.map((e) => (
              <TableRow key={e.id} className={e.voided ? 'text-muted-foreground line-through' : ''}>
                <TableCell>{formatDate(e.date)}</TableCell>
                <TableCell>{e.category}</TableCell>
                <TableCell>{e.description}{e.paidTo ? ` - ${e.paidTo}` : ''}</TableCell>
                <TableCell>{formatMoney(e.amount)}</TableCell>
                <TableCell>
                  {!e.voided && (
                    <Button size="sm" variant="ghost" onClick={() => {
                      const reason = window.prompt('Why is this expense void?')
                      if (reason?.trim()) remove.mutate({ id: e.id, reason: reason.trim() })
                    }}>Void</Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {rows.isSuccess && rows.data.length === 0 && (
              <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground">No expenses in this period.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
