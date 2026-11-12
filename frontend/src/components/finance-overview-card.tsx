import { useQuery } from '@tanstack/react-query'

import { expenseSummary, type FinanceDashboardResponse } from '@/api/finance'
import { getPayrollMonth } from '@/api/payroll'
import { ExportMenu } from '@/components/export-menu'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatMoney } from '@/lib/format'
import { percentOf } from '@/lib/export-rows'
import type { Report } from '@/lib/report'

const iso = (d: Date) => d.toISOString().slice(0, 10)

/** The whole finance picture in one place: fees billed, collected and left, this month's income, salary, expenses and profit. */
export function FinanceOverviewCard({ dashboard }: { dashboard: FinanceDashboardResponse }) {
  const now = new Date()
  const month = iso(now).slice(0, 7)
  const from = iso(new Date(now.getFullYear(), now.getMonth(), 1))
  const to = iso(now)
  const expenses = useQuery({ queryKey: ['finance', 'overview-expenses', from, to], queryFn: () => expenseSummary(from, to) })
  const payroll = useQuery({ queryKey: ['payroll', 'month', month], queryFn: () => getPayrollMonth(month) })

  const collectedPct = percentOf(dashboard.collected, dashboard.totalBilled)
  const pendingPct = Math.round((100 - collectedPct) * 10) / 10
  const salary = payroll.data?.summary
  const spent = expenses.data?.spent ?? 0
  const incomeMonth = expenses.data?.collected ?? dashboard.collectedThisMonth
  const profit = incomeMonth - spent

  async function build(): Promise<Report> {
    const rows: [string, string | number, string][] = [
      ['Fees billed (all time)', dashboard.totalBilled, ''],
      ['Fees collected (all time)', dashboard.collected, `${collectedPct}%`],
      ['Fees still to collect', dashboard.pending, `${pendingPct}%`],
      ['Overdue', dashboard.overdueAmount, `${dashboard.studentsWithOverdue} students`],
      [`Collected in ${month}`, incomeMonth, ''],
      [`Salary for ${month} (total)`, salary?.totalNet ?? 0, `${salary?.slips ?? 0} people`],
      [`Salary for ${month} paid`, salary?.paid ?? 0, ''],
      [`Salary for ${month} still to pay`, salary?.toPay ?? 0, ''],
      [`Expenses in ${month} (including paid salary)`, spent, ''],
      [profit < 0 ? `Loss in ${month}` : `Profit in ${month}`, Math.abs(profit), ''],
    ]
    return {
      title: 'Finance overview',
      fileName: 'finance-overview',
      info: [['Month', month], ['Collected %', `${collectedPct}%`], ['Still to collect %', `${pendingPct}%`]],
      header: ['Item', 'Amount', 'Note'],
      rows,
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <CardTitle className="text-sm">Finance overview</CardTitle>
            <CardDescription>
              {collectedPct}% of all fees billed is collected and {pendingPct}% is still to come.
            </CardDescription>
          </div>
          <ExportMenu label="Download overview" build={build} />
        </div>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <Line label="Collected this month" value={formatMoney(incomeMonth)} />
        <Line label="Salary this month" value={salary ? formatMoney(salary.totalNet) : '-'}
          hint={salary ? `${formatMoney(salary.paid)} paid, ${formatMoney(salary.toPay)} to pay` : undefined} />
        <Line label="Expenses this month" value={formatMoney(spent)} hint="includes paid salary" />
        <Line label={profit < 0 ? 'Loss this month' : 'Profit this month'} value={formatMoney(Math.abs(profit))} bad={profit < 0} />
      </CardContent>
    </Card>
  )
}

function Line({ label, value, hint, bad }: { label: string; value: string; hint?: string; bad?: boolean }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`text-lg font-semibold ${bad ? 'text-destructive' : ''}`}>{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}
