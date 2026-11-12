import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { allFeePlans, type FeePlanResponse, getFinanceDashboard, getOverdueInstallments, searchFeePlans } from '@/api/finance'
import { searchStudents, type StudentSummaryResponse } from '@/api/students'
import { BankReconciliationCard } from '@/components/bank-reconciliation-card'
import { BooksLockCard } from '@/components/books-lock-card'
import { CashClosingCard } from '@/components/cash-closing-card'
import { ChargeBatchButton } from '@/components/add-charge-dialogs'
import { CollectionReportCard } from '@/components/collection-report-card'
import { ExportMenu } from '@/components/export-menu'
import { FinanceOverviewCard } from '@/components/finance-overview-card'
import { percentOf } from '@/lib/export-rows'
import { RefundsCard } from '@/components/refunds-card'
import { UpiClaimsCard } from '@/components/upi-claims-card'
import { GstCard } from '@/components/gst-card'
import { ExpensesCard } from '@/components/expenses-card'
import { FeeTemplateCard } from '@/components/fee-template-card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { feePlansReport, feePositionReport, overdueReport } from '@/lib/export-rows'
import { formatDate, formatMoney } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'default',
  SETTLED: 'secondary',
  CANCELLED: 'destructive',
}

export function FinanceDeskPage() {
  const canSeeExpenses = hasRole(useAuthStore((state) => state.roles), ['ADMIN', 'FINANCE'])
  const [status, setStatus] = useState('')

  const dashboardQuery = useQuery({ queryKey: ['finance', 'dashboard'], queryFn: getFinanceDashboard })
  const overdueQuery = useQuery({ queryKey: ['finance', 'overdue'], queryFn: getOverdueInstallments })
  const plansQuery = useQuery({
    queryKey: ['finance', 'plans', status],
    queryFn: () => searchFeePlans({ status: status || undefined }),
  })

  /** Every student with their fee position; read in full only when the menu is used. */
  async function feePositionSheet() {
    const students: StudentSummaryResponse[] = []
    const plans: FeePlanResponse[] = []
    for (let page = 0, last = false; !last; page += 1) {
      const result = await searchStudents({ page })
      students.push(...result.content)
      last = result.last || result.content.length === 0
    }
    plans.push(...(await allFeePlans('')))
    return feePositionReport(students, plans)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Finance</h1>
          <p className="text-muted-foreground">Fee position across the institute (Doc S15).</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canSeeExpenses && (
            <Button asChild variant="outline">
              <Link to="/app/finance/payroll">Payroll</Link>
            </Button>
          )}
          {canSeeExpenses && (
            <Button asChild variant="outline">
              <Link to="/app/finance/books">Books</Link>
            </Button>
          )}
          <ChargeBatchButton />
          <ExportMenu label="Download every student's fees" report="FEE_POSITION" build={feePositionSheet} />
        </div>
      </div>

      {dashboardQuery.isLoading && <Skeleton className="h-32" />}
      {dashboardQuery.data && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Billed" value={formatMoney(dashboardQuery.data.totalBilled, dashboardQuery.data.currency)} />
          <StatCard
            label="Collected"
            value={formatMoney(dashboardQuery.data.collected, dashboardQuery.data.currency)}
            hint={`${percentOf(dashboardQuery.data.collected, dashboardQuery.data.totalBilled)}% of billed`}
          />
          <StatCard
            label="Pending"
            value={formatMoney(dashboardQuery.data.pending, dashboardQuery.data.currency)}
            hint={`${Math.round((100 - percentOf(dashboardQuery.data.collected, dashboardQuery.data.totalBilled)) * 10) / 10}% still to come`}
          />
          <StatCard
            label="Overdue"
            value={formatMoney(dashboardQuery.data.overdueAmount, dashboardQuery.data.currency)}
            tone="destructive"
            hint={`${dashboardQuery.data.overdueInstallments} installments, ${dashboardQuery.data.studentsWithOverdue} students`}
          />
        </div>
      )}

      {canSeeExpenses && dashboardQuery.data && <FinanceOverviewCard dashboard={dashboardQuery.data} />}

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

      {canSeeExpenses && <BankReconciliationCard />}

      {canSeeExpenses && <BooksLockCard />}

      <UpiClaimsCard />

      <CashClosingCard />

      <RefundsCard />

      <CollectionReportCard />

      {canSeeExpenses && <ExpensesCard />}

      {canSeeExpenses && <GstCard />}

      <FeeTemplateCard />

      <div>
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-medium text-muted-foreground">Overdue installments</h2>
          <ExportMenu
            label="Download overdue"
            report="OVERDUE_FEES"
            build={async () => overdueReport(overdueQuery.data ?? (await getOverdueInstallments()))}
          />
        </div>
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
          <ExportMenu
            label="Download fee plans"
            report="FEE_PLANS"
            detail={status || 'all statuses'}
            build={async () => feePlansReport(await allFeePlans(status), status)}
          />
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
