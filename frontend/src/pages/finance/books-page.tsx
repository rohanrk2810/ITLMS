import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { type BookBalance, type Books, getBooks } from '@/api/books'
import { ExportMenu } from '@/components/export-menu'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import type { Report } from '@/lib/report'

const iso = (d: Date) => d.toISOString().slice(0, 10)
type View = 'trial' | 'pl' | 'sheet' | 'journal'
const VIEWS: { id: View; label: string }[] = [
  { id: 'trial', label: 'Trial balance' },
  { id: 'pl', label: 'Profit and loss' },
  { id: 'sheet', label: 'Balance sheet' },
  { id: 'journal', label: 'Journal' },
]

/** The books in double entry, worked out from the fee, payment, refund and expense records. */
export function BooksPage() {
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth(), 1)))
  const [to, setTo] = useState(iso(now))
  const [view, setView] = useState<View>('trial')
  const books = useQuery({ queryKey: ['finance', 'books', from, to], queryFn: () => getBooks(from, to), enabled: from <= to })
  const names = new Map(books.data?.accounts.map((a) => [a.code, a.name]))

  async function build(): Promise<Report> {
    const b = books.data ?? (await getBooks(from, to))
    return reportOf(view, b)
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link to="/app/finance" className="text-sm text-muted-foreground hover:underline">&larr; Finance</Link>
        <h1 className="text-2xl font-semibold">Books</h1>
        <p className="text-muted-foreground">
          Double-entry books worked out from the fee plans, payments, refunds and expenses, so they can never disagree with them.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="bk-from">From</Label>
          <Input id="bk-from" type="date" className="w-40" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="bk-to">To</Label>
          <Input id="bk-to" type="date" className="w-40" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </div>
        <div className="flex gap-1">
          {VIEWS.map((v) => (
            <button key={v.id} type="button" onClick={() => setView(v.id)}
              className={`h-9 rounded-md border px-3 text-sm ${view === v.id ? 'bg-primary text-primary-foreground' : 'bg-transparent'}`}>
              {v.label}
            </button>
          ))}
        </div>
        <ExportMenu label="Download" build={build} />
        {books.data && <Badge variant={books.data.balanced ? 'secondary' : 'destructive'}>{books.data.balanced ? 'Debits equal credits' : 'Does not balance'}</Badge>}
      </div>

      {books.isLoading && <Skeleton className="h-64" />}
      {books.data && view === 'trial' && <Trial rows={books.data.trialBalance} to={books.data.to} />}
      {books.data && view === 'pl' && <ProfitLoss books={books.data} />}
      {books.data && view === 'sheet' && <Sheet books={books.data} />}
      {books.data && view === 'journal' && <Journal books={books.data} names={names} />}
    </div>
  )
}

function reportOf(view: View, b: Books): Report {
  const period = `${b.from} to ${b.to}`
  if (view === 'trial') {
    return {
      title: 'Trial balance', fileName: 'trial-balance', info: [['As on', b.to]],
      header: ['Code', 'Account', 'Debit', 'Credit'],
      rows: b.trialBalance.map((r) => [r.code, r.name, r.debit, r.credit]),
    }
  }
  if (view === 'pl') {
    return {
      title: 'Profit and loss', fileName: 'profit-and-loss', info: [['Period', period]],
      header: ['Item', 'Amount'],
      rows: [
        ...b.profitLoss.income.map((r) => [r.name, r.credit]),
        ['Total income', b.profitLoss.totalIncome],
        ...b.profitLoss.expenses.map((r) => [r.name, r.debit]),
        ['Total expenses', b.profitLoss.totalExpenses],
        [b.profitLoss.surplus < 0 ? 'Deficit' : 'Surplus', Math.abs(b.profitLoss.surplus)],
      ],
    }
  }
  if (view === 'sheet') {
    return {
      title: 'Balance sheet', fileName: 'balance-sheet', info: [['As on', b.to]],
      header: ['Item', 'Amount'],
      rows: [
        ...b.balanceSheet.assets.map((r) => [r.name, r.debit]),
        ['Total assets', b.balanceSheet.totalAssets],
        ...b.balanceSheet.liabilities.map((r) => [r.name, r.credit]),
        ['Surplus to date', b.balanceSheet.surplus],
        ['Total liabilities and surplus', b.balanceSheet.totalLiabilities + b.balanceSheet.surplus],
      ],
    }
  }
  const names = new Map(b.accounts.map((a) => [a.code, a.name]))
  return {
    title: 'Journal', fileName: 'journal', info: [['Period', period], ['Entries', String(b.journal.length)]],
    header: ['Date', 'Reference', 'Description', 'Account', 'Debit', 'Credit'],
    rows: b.journal.flatMap((e) => e.lines.map((l) => [e.date, e.reference, e.description, `${l.account} ${names.get(l.account) ?? ''}`, l.debit || '', l.credit || ''])),
  }
}

function Trial({ rows, to }: { rows: BookBalance[]; to: string }) {
  const debit = rows.reduce((a, r) => a + r.debit, 0)
  const credit = rows.reduce((a, r) => a + r.credit, 0)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Trial balance as on {formatDate(to)}</CardTitle>
        <CardDescription>Every account from the start of the records up to this date.</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow><TableHead>Code</TableHead><TableHead>Account</TableHead><TableHead className="text-right">Debit</TableHead><TableHead className="text-right">Credit</TableHead></TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.code}>
                <TableCell>{r.code}</TableCell><TableCell>{r.name}</TableCell>
                <TableCell className="text-right">{r.debit ? formatMoney(r.debit) : ''}</TableCell>
                <TableCell className="text-right">{r.credit ? formatMoney(r.credit) : ''}</TableCell>
              </TableRow>
            ))}
            <TableRow className="font-semibold">
              <TableCell /><TableCell>Total</TableCell>
              <TableCell className="text-right">{formatMoney(debit)}</TableCell><TableCell className="text-right">{formatMoney(credit)}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

function Section({ title, rows, pick }: { title: string; rows: BookBalance[]; pick: (r: BookBalance) => number }) {
  return (
    <>
      <TableRow><TableCell colSpan={2} className="pt-4 text-xs font-medium uppercase text-muted-foreground">{title}</TableCell></TableRow>
      {rows.map((r) => (
        <TableRow key={r.code}><TableCell>{r.name}</TableCell><TableCell className="text-right">{formatMoney(pick(r))}</TableCell></TableRow>
      ))}
    </>
  )
}

function ProfitLoss({ books }: { books: Books }) {
  const p = books.profitLoss
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Profit and loss, {formatDate(books.from)} to {formatDate(books.to)}</CardTitle>
      </CardHeader>
      <CardContent>
        <Table>
          <TableBody>
            <Section title="Income" rows={p.income} pick={(r) => r.credit} />
            <TableRow className="font-semibold"><TableCell>Total income</TableCell><TableCell className="text-right">{formatMoney(p.totalIncome)}</TableCell></TableRow>
            <Section title="Expenses" rows={p.expenses} pick={(r) => r.debit} />
            <TableRow className="font-semibold"><TableCell>Total expenses</TableCell><TableCell className="text-right">{formatMoney(p.totalExpenses)}</TableCell></TableRow>
            <TableRow className={`text-base font-semibold ${p.surplus < 0 ? 'text-destructive' : ''}`}>
              <TableCell>{p.surplus < 0 ? 'Deficit' : 'Surplus'}</TableCell><TableCell className="text-right">{formatMoney(Math.abs(p.surplus))}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

function Sheet({ books }: { books: Books }) {
  const s = books.balanceSheet
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Balance sheet as on {formatDate(s.asOf)}</CardTitle>
        <CardDescription>{s.balances ? 'Assets equal liabilities plus surplus.' : 'Assets do not equal liabilities plus surplus.'}</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableBody>
            <Section title="Assets" rows={s.assets} pick={(r) => r.debit} />
            <TableRow className="font-semibold"><TableCell>Total assets</TableCell><TableCell className="text-right">{formatMoney(s.totalAssets)}</TableCell></TableRow>
            <Section title="Liabilities" rows={s.liabilities} pick={(r) => r.credit} />
            <TableRow><TableCell>Surplus to date</TableCell><TableCell className="text-right">{formatMoney(s.surplus)}</TableCell></TableRow>
            <TableRow className="font-semibold"><TableCell>Total liabilities and surplus</TableCell><TableCell className="text-right">{formatMoney(s.totalLiabilities + s.surplus)}</TableCell></TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

function Journal({ books, names }: { books: Books; names: Map<string, string> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Journal, {books.journal.length} entries</CardTitle>
        <CardDescription>Every entry has equal debits and credits.</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow><TableHead>Date</TableHead><TableHead>Entry</TableHead><TableHead>Account</TableHead><TableHead className="text-right">Debit</TableHead><TableHead className="text-right">Credit</TableHead></TableRow>
          </TableHeader>
          <TableBody>
            {books.journal.slice(0, 500).flatMap((e, i) =>
              e.lines.map((l, j) => (
                <TableRow key={`${i}-${j}`}>
                  <TableCell>{j === 0 ? formatDate(e.date) : ''}</TableCell>
                  <TableCell>{j === 0 ? <>{e.description}<div className="text-xs text-muted-foreground">{e.reference}</div></> : ''}</TableCell>
                  <TableCell className={l.credit ? 'pl-8' : ''}>{l.account} {names.get(l.account)}</TableCell>
                  <TableCell className="text-right">{l.debit ? formatMoney(l.debit) : ''}</TableCell>
                  <TableCell className="text-right">{l.credit ? formatMoney(l.credit) : ''}</TableCell>
                </TableRow>
              )),
            )}
          </TableBody>
        </Table>
        {books.journal.length > 500 && <p className="mt-2 text-xs text-muted-foreground">Showing the first 500; the download has all of them.</p>}
      </CardContent>
    </Card>
  )
}
