import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { gstSummary } from '@/api/finance'
import { ExportMenu } from '@/components/export-menu'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatMoney } from '@/lib/format'
import type { Report } from '@/lib/report'

const iso = (d: Date) => d.toISOString().slice(0, 10)

/** GST collected on fees, month by month, for the return. Appears only when the institute has a GST rate set. */
export function GstCard() {
  const now = new Date()
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth() - 2, 1)))
  const [to, setTo] = useState(iso(now))
  const gst = useQuery({ queryKey: ['finance', 'gst', from, to], queryFn: () => gstSummary(from, to), enabled: from <= to })
  if (!gst.data?.enabled) return null
  const g = gst.data

  async function build(): Promise<Report> {
    return {
      title: 'GST on fees',
      fileName: 'gst-on-fees',
      info: [['Period', `${from} to ${to}`], ['Rate', `${g.ratePercent}%`], ['GSTIN', g.gstin ?? ''], ['Fees are taken as tax-inclusive', 'yes']],
      header: ['Month', 'Payments', 'Received', 'Taxable value', 'CGST', 'SGST'],
      rows: [
        ...g.months.map((m) => [m.month, m.payments, m.received, m.taxable, m.cgst, m.sgst]),
        ['Total', g.months.reduce((a, m) => a + m.payments, 0), g.received, g.taxable, g.cgst, g.sgst],
      ],
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">GST on fees ({g.ratePercent}%)</CardTitle>
        <CardDescription>Tax inside the fees received, net of refunds, for the GST return.</CardDescription>
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
          <ExportMenu label="Download" build={build} />
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Month</TableHead>
              <TableHead>Received</TableHead>
              <TableHead>Taxable value</TableHead>
              <TableHead>CGST</TableHead>
              <TableHead>SGST</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {g.months.map((m) => (
              <TableRow key={m.month}>
                <TableCell>{m.month}</TableCell>
                <TableCell>{formatMoney(m.received)}</TableCell>
                <TableCell>{formatMoney(m.taxable)}</TableCell>
                <TableCell>{formatMoney(m.cgst)}</TableCell>
                <TableCell>{formatMoney(m.sgst)}</TableCell>
              </TableRow>
            ))}
            <TableRow className="font-semibold">
              <TableCell>Total</TableCell>
              <TableCell>{formatMoney(g.received)}</TableCell>
              <TableCell>{formatMoney(g.taxable)}</TableCell>
              <TableCell>{formatMoney(g.cgst)}</TableCell>
              <TableCell>{formatMoney(g.sgst)}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
