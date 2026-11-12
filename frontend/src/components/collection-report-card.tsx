import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { allFeePlans, paymentsBetween, type PaymentResponse } from '@/api/finance'
import { ExportMenu } from '@/components/export-menu'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatMoney } from '@/lib/format'
import type { Report } from '@/lib/report'

const METHODS = ['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'ONLINE']
const iso = (d: Date) => d.toISOString().slice(0, 10)

/** What was collected between two dates, by payment method, with a download of every payment. */
export function CollectionReportCard() {
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth(), 1)))
  const [to, setTo] = useState(iso(now))
  const [method, setMethod] = useState('')

  const payments = useQuery({
    queryKey: ['finance', 'collection', from, to],
    queryFn: () => paymentsBetween(from, to),
    enabled: !!from && !!to && from <= to,
  })
  const counted = (payments.data ?? []).filter((p) => p.status === 'SUCCESS' && (!method || p.method === method))
  const total = counted.reduce((a, p) => a + p.amount, 0)
  const byMethod = METHODS.map((m) => ({
    method: m,
    amount: counted.filter((p) => p.method === m).reduce((a, p) => a + p.amount, 0),
    count: counted.filter((p) => p.method === m).length,
  })).filter((x) => x.count > 0)

  async function build(): Promise<Report> {
    const rows: PaymentResponse[] = payments.data ?? (await paymentsBetween(from, to))
    const plans = await allFeePlans('')
    const byPlan = new Map(plans.map((p) => [p.id, p]))
    const picked = rows.filter((p) => !method || p.method === method)
    const good = picked.filter((p) => p.status === 'SUCCESS')
    return {
      title: 'Fee collection',
      fileName: 'fee-collection',
      info: [
        ['Period', `${from} to ${to}`],
        ['Method', method || 'All'],
        ['Payments', String(good.length)],
        ['Collected', String(good.reduce((a, p) => a + p.amount, 0))],
      ],
      header: ['Date', 'Receipt no', 'Student code', 'Student', 'Method', 'Reference', 'Amount', 'Status'],
      rows: picked.map((p) => {
        const plan = byPlan.get(p.feePlanId)
        return [p.paymentDate, p.receiptNo, plan?.studentCode, plan?.studentName, p.method, p.referenceNo, p.amount, p.status]
      }),
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Fee collection report</CardTitle>
        <CardDescription>What was collected between two dates, and how it was paid.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="col-from">From</Label>
            <Input id="col-from" type="date" className="w-40" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="col-to">To</Label>
            <Input id="col-to" type="date" className="w-40" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </div>
          <select aria-label="Method" value={method} onChange={(e) => setMethod(e.target.value)}
            className="h-9 rounded-md border bg-transparent px-3 text-sm">
            <option value="">All methods</option>
            {METHODS.map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
          <ExportMenu label="Download" build={build} />
        </div>
        {payments.isSuccess && (
          <div className="flex flex-wrap gap-4 text-sm">
            <span>
              Collected <span className="font-semibold">{formatMoney(total)}</span> in {counted.length} payments
            </span>
            {byMethod.map((m) => (
              <span key={m.method} className="text-muted-foreground">
                {m.method}: <span className="font-medium text-foreground">{formatMoney(m.amount)}</span> ({m.count})
              </span>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
