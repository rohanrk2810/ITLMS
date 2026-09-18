import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { getFinanceDashboard, getOverdueInstallments, searchFeePlans } from '@/api/finance'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'default',
  SETTLED: 'secondary',
  CANCELLED: 'destructive',
}

export function FinanceDeskPage() {
  const [status, setStatus] = useState('')

  const dashboardQuery = useQuery({ queryKey: ['finance', 'dashboard'], queryFn: getFinanceDashboard })
  const overdueQuery = useQuery({ queryKey: ['finance', 'overdue'], queryFn: getOverdueInstallments })
  const plansQuery = useQuery({
    queryKey: ['finance', 'plans', status],
    queryFn: () => searchFeePlans({ status: status || undefined }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Finance</h1>
        <p className="text-muted-foreground">Fee position across the institute (Doc S15).</p>
      </div>

      {dashboardQuery.isLoading && <Skeleton className="h-32" />}
      {dashboardQuery.data && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Billed" value={formatMoney(dashboardQuery.data.totalBilled, dashboardQuery.data.currency)} />
          <StatCard label="Collected" value={formatMoney(dashboardQuery.data.collected, dashboardQuery.data.currency)} />
          <StatCard label="Pending" value={formatMoney(dashboardQuery.data.pending, dashboardQuery.data.currency)} />
          <StatCard
            label="Overdue"
            value={formatMoney(dashboardQuery.data.overdueAmount, dashboardQuery.data.currency)}
            tone="destructive"
            hint={`${dashboardQuery.data.overdueInstallments} installments, ${dashboardQuery.data.studentsWithOverdue} students`}
          />
        </div>
      )}

      {dashboardQuery.data && dashboardQuery.data.methodSplitThisMonth.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">This month's collections</CardTitle>
            <CardDescription>{formatMoney(dashboardQuery.data.collectedThisMonth, dashboardQuery.data.currency)} total</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-4 text-sm">
            {dashboardQuery.data.methodSplitThisMonth.map((m) => (
              <span key={m.method} className="text-muted-foreground">
                {m.method}: <span className="font-medium text-foreground">{formatMoney(m.amount, dashboardQuery.data!.currency)}</span>{' '}
                ({m.payments})
              </span>
            ))}
          </CardContent>
        </Card>
      )}

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Overdue installments</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Installment</TableHead>
              <TableHead>Due</TableHead>
              <TableHead>Days overdue</TableHead>
              <TableHead>Amount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {overdueQuery.data?.map((item) => (
              <TableRow key={item.installmentId}>
                <TableCell>
                  <Link to={`/app/finance/${item.feePlanId}`} className="hover:underline">
                    {item.studentName}
                  </Link>
                </TableCell>
                <TableCell>#{item.installmentNo}</TableCell>
                <TableCell>{formatDate(item.dueDate)}</TableCell>
                <TableCell className="text-destructive">{item.daysOverdue}</TableCell>
                <TableCell>{formatMoney(item.amountOverdue)}</TableCell>
              </TableRow>
            ))}
            {overdueQuery.isSuccess && overdueQuery.data.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  Nothing overdue.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">Fee plans</h2>
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            className="h-9 rounded-md border bg-transparent px-3 text-sm"
          >
            <option value="">All statuses</option>
            {['ACTIVE', 'SETTLED', 'CANCELLED'].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Net fee</TableHead>
              <TableHead>Paid</TableHead>
              <TableHead>Outstanding</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {plansQuery.data?.content.map((plan) => (
              <TableRow key={plan.id}>
                <TableCell>
                  <Link to={`/app/finance/${plan.id}`} className="font-medium hover:underline">
                    {plan.studentName}
                  </Link>
                </TableCell>
                <TableCell>{formatMoney(plan.netFee, plan.currency)}</TableCell>
                <TableCell>{formatMoney(plan.paid, plan.currency)}</TableCell>
                <TableCell>{formatMoney(plan.outstanding, plan.currency)}</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[plan.status] ?? 'outline'}>{plan.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {plansQuery.isSuccess && plansQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No fee plans match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

function StatCard({
  label,
  value,
  hint,
  tone,
}: {
  label: string
  value: string
  hint?: string
  tone?: 'destructive'
}) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`text-xl font-semibold ${tone === 'destructive' ? 'text-destructive' : ''}`}>{value}</p>
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}
