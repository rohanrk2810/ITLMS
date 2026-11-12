import { useQuery } from '@tanstack/react-query'

import { mySlips } from '@/api/payroll'
import { DownloadSlipButton } from '@/components/download-slip-button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'

/** A member of staff's own paid salary slips. */
export function MySalaryPage() {
  const slips = useQuery({ queryKey: ['payroll', 'mine'], queryFn: mySlips })
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">My salary</h1>
        <p className="text-muted-foreground">Your salary slips, once the institute has paid them.</p>
      </div>
      {slips.isLoading && <Skeleton className="h-32" />}
      {slips.isSuccess && slips.data.length === 0 && (
        <p className="text-sm text-muted-foreground">No salary slip yet. It appears here after your salary is paid.</p>
      )}
      {slips.data && slips.data.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Month</TableHead>
              <TableHead>Net pay</TableHead>
              <TableHead>Paid on</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {slips.data.map((p) => (
              <TableRow key={p.id}>
                <TableCell>{p.month}</TableCell>
                <TableCell className="font-medium">{formatMoney(p.net)}</TableCell>
                <TableCell>{p.paidOn ? formatDate(p.paidOn) : ''}</TableCell>
                <TableCell className="text-right">
                  <DownloadSlipButton id={p.id} slipNo={p.slipNo} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
