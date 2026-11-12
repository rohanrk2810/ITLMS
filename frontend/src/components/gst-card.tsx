import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { gstSummary, recordGstPayment } from '@/api/finance'
import { ExportMenu } from '@/components/export-menu'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import type { Report } from '@/lib/report'

const iso = (d: Date) => d.toISOString().slice(0, 10)
const METHODS = ['BANK_TRANSFER', 'UPI', 'CASH', 'CHEQUE', 'CARD']

/**
 * The GST return: month by month the tax charged on fees, the credit for GST paid on expenses, what was paid to the
 * government and what is still payable, with the invoice register to download. Appears once GST is set up.
 */
export function GstCard() {
  const queryClient = useQueryClient()
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth() - 2, 1)))
  const [to, setTo] = useState(iso(now))
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('BANK_TRANSFER')
  const [reference, setReference] = useState('')
  const [forMonth, setForMonth] = useState(iso(now).slice(0, 7))
  const gst = useQuery({ queryKey: ['finance', 'gst', from, to], queryFn: () => gstSummary(from, to), enabled: from <= to })

  const pay = useMutation({
    mutationFn: () => recordGstPayment({ amount: Number(amount), method, reference: reference.trim() || undefined, forMonth }),
    onSuccess: () => {
      toast.success('GST payment recorded')
      setAmount('')
      setReference('')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not record the GST payment.')),
  })

  if (!gst.data?.enabled) return null
  const g = gst.data
  const s = g.summary

  async function returnReport(): Promise<Report> {
    return {
      title: 'GST return summary',
      fileName: 'gst-return',
      info: [['Period', `${from} to ${to}`], ['Rate', `${g.ratePercent}%`], ['GSTIN', g.gstin ?? ''], ['Still payable', String(s.stillPayable)]],
      header: ['Month', 'Invoices', 'Taxable value', 'Exempt', 'CGST', 'SGST', 'IGST', 'Output tax', 'Input credit', 'Net payable', 'Paid to government'],
      rows: [
        ...s.months.map((m) => [m.month, m.invoices, m.taxable, m.exempt, m.cgst, m.sgst, m.igst, m.outputTax, m.inputCredit, m.netPayable, m.paid]),
        ['Total', s.months.reduce((a, m) => a + m.invoices, 0), s.months.reduce((a, m) => a + m.taxable, 0), s.months.reduce((a, m) => a + m.exempt, 0),
          s.months.reduce((a, m) => a + m.cgst, 0), s.months.reduce((a, m) => a + m.sgst, 0), s.months.reduce((a, m) => a + m.igst, 0),
          s.outputTax, s.inputCredit, s.outputTax - s.inputCredit, s.paid],
      ],
    }
  }

  async function registerReport(): Promise<Report> {
    return {
      title: 'GST invoice register',
      fileName: 'gst-invoice-register',
      info: [['Period', `${from} to ${to}`], ['GSTIN', g.gstin ?? ''], ['Invoices', String(g.invoices.length)]],
      header: ['Invoice (receipt) no', 'Date', 'Student', 'State', 'Rate %', 'Taxable value', 'Exempt', 'CGST', 'SGST', 'IGST', 'Invoice total'],
      rows: g.invoices.map((i) => [i.receiptNo, i.date, i.student, i.state, i.rate, i.taxable, i.exempt, i.cgst, i.sgst, i.igst, i.total]),
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">GST return ({g.ratePercent}%)</CardTitle>
        <CardDescription>
          Tax charged on fees (net of refunds), less the GST paid on expenses, less what was paid to the government. Fees are taken as tax-inclusive.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="gst-from">From</Label>
            <Input id="gst-from" type="date" className="w-40" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="gst-to">To</Label>
            <Input id="gst-to" type="date" className="w-40" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </div>
          <ExportMenu label="Download return" build={returnReport} />
          <ExportMenu label="Download invoice register" build={registerReport} />
        </div>

        <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
          <Stat label="GST charged" value={formatMoney(s.outputTax)} />
          <Stat label="Credit on expenses" value={formatMoney(s.inputCredit)} />
          <Stat label="Paid to government" value={formatMoney(s.paid)} />
          <Stat label="Still payable" value={formatMoney(s.stillPayable)} bad={s.stillPayable > 0} />
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Month</TableHead>
              <TableHead>Taxable</TableHead>
              <TableHead>CGST</TableHead>
              <TableHead>SGST</TableHead>
              <TableHead>IGST</TableHead>
              <TableHead>Credit</TableHead>
              <TableHead>Net payable</TableHead>
              <TableHead>Paid</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {s.months.map((m) => (
              <TableRow key={m.month}>
                <TableCell>{m.month}</TableCell>
                <TableCell>{formatMoney(m.taxable)}</TableCell>
                <TableCell>{formatMoney(m.cgst)}</TableCell>
                <TableCell>{formatMoney(m.sgst)}</TableCell>
                <TableCell>{formatMoney(m.igst)}</TableCell>
                <TableCell>{formatMoney(m.inputCredit)}</TableCell>
                <TableCell>{formatMoney(m.netPayable)}</TableCell>
                <TableCell>{formatMoney(m.paid)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        <div className="flex flex-wrap items-end gap-2 rounded-lg border p-3">
          <p className="basis-full text-sm font-medium">Record GST paid to the government</p>
          <Input aria-label="For month" type="month" className="w-40" value={forMonth} onChange={(e) => setForMonth(e.target.value)} />
          <Input aria-label="Amount" type="number" min={1} placeholder="Amount" className="w-32" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <select aria-label="Paid by" value={method} onChange={(e) => setMethod(e.target.value)} className="h-9 rounded-md border bg-transparent px-3 text-sm">
            {METHODS.map((m) => <option key={m} value={m}>{m.replace('_', ' ')}</option>)}
          </select>
          <Input aria-label="Challan or reference" placeholder="Challan / reference" maxLength={80} className="w-48" value={reference} onChange={(e) => setReference(e.target.value)} />
          <Button disabled={!(Number(amount) > 0) || pay.isPending} onClick={() => pay.mutate()}>Record payment</Button>
        </div>

        {g.payments.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow><TableHead>Paid on</TableHead><TableHead>For month</TableHead><TableHead>Amount</TableHead><TableHead>How</TableHead><TableHead>Reference</TableHead></TableRow>
            </TableHeader>
            <TableBody>
              {g.payments.map((p) => (
                <TableRow key={p.id}>
                  <TableCell>{formatDate(p.paidOn)}</TableCell>
                  <TableCell>{p.forMonth}</TableCell>
                  <TableCell>{formatMoney(p.amount)}</TableCell>
                  <TableCell>{p.method.replace('_', ' ')}</TableCell>
                  <TableCell className="font-mono text-xs">{p.reference}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function Stat({ label, value, bad }: { label: string; value: string; bad?: boolean }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`text-lg font-semibold ${bad ? 'text-destructive' : ''}`}>{value}</p>
    </div>
  )
}
