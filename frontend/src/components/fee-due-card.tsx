import { useQuery } from '@tanstack/react-query'
import { CalendarClock } from 'lucide-react'
import { Link } from 'react-router-dom'

import { myFeePlans } from '@/api/finance'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate, formatMoney } from '@/lib/format'

const DAY = 86_400_000

/** "Overdue by 3 days", "Due today", "In 5 days": how far a due date is from today. */
export function dueWording(dueDate: string, today: Date = new Date()): { text: string; overdue: boolean; soon: boolean } {
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const [y, m, d] = dueDate.split('-').map(Number)
  const days = Math.round((new Date(y, m - 1, d).getTime() - start) / DAY)
  if (days < 0) return { text: `Overdue by ${-days} day${days === -1 ? '' : 's'}`, overdue: true, soon: true }
  if (days === 0) return { text: 'Due today', overdue: false, soon: true }
  return { text: `In ${days} day${days === 1 ? '' : 's'}`, overdue: false, soon: days <= 7 }
}

/** A student's coming fee dates, nearest first, with what is left to pay on each. */
export function FeeDueCard() {
  const plans = useQuery({ queryKey: ['finance', 'my-fee-plans'], queryFn: myFeePlans })
  const items = (plans.data ?? [])
    .filter((p) => p.status === 'ACTIVE')
    .flatMap((p) => p.installments.filter((i) => i.remaining > 0).map((i) => ({ plan: p, installment: i })))
    .sort((a, b) => a.installment.dueDate.localeCompare(b.installment.dueDate))
    .slice(0, 4)

  if (items.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <CalendarClock className="size-4" /> Fees due
        </CardTitle>
        <CardDescription>
          <Link to="/app/finance" className="underline-offset-4 hover:underline">
            See all and pay
          </Link>
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {items.map(({ plan, installment }) => {
          const when = dueWording(installment.dueDate)
          return (
            <div key={installment.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <div>
                <p className="font-medium">{installment.label ?? `Course fee, installment ${installment.installmentNo}`}</p>
                <p className="text-muted-foreground">{formatDate(installment.dueDate)}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={when.overdue ? 'destructive' : when.soon ? 'default' : 'outline'}>{when.text}</Badge>
                <span className="font-semibold">{formatMoney(installment.remaining, plan.currency)}</span>
              </div>
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}
