import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Receipt } from 'lucide-react'

import { type FeePlanResponse, getReceipt, myFeePlans, type ReceiptResponse } from '@/api/finance'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'

const STATE_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  PAID: 'default',
  PARTLY_PAID: 'secondary',
  DUE: 'outline',
  OVERDUE: 'destructive',
  UPCOMING: 'outline',
}

export function FinancePage() {
  const query = useQuery({ queryKey: ['finance', 'my-fee-plans'], queryFn: myFeePlans })
  const [receiptPaymentId, setReceiptPaymentId] = useState<number | null>(null)

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Fees</h1>
        <p className="text-muted-foreground">Your fee plans, what&apos;s paid, and what&apos;s still due.</p>
      </div>

      {query.isLoading && (
        <div className="flex flex-col gap-4">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-64" />
          ))}
        </div>
      )}

      {query.isSuccess && query.data.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>No fee plan yet</CardTitle>
            <CardDescription>One is raised automatically once your admission is confirmed.</CardDescription>
          </CardHeader>
        </Card>
      )}

      {query.data?.map((plan) => (
        <FeePlanCard key={plan.id} plan={plan} onViewReceipt={setReceiptPaymentId} />
      ))}

      <Dialog open={receiptPaymentId != null} onOpenChange={(open) => !open && setReceiptPaymentId(null)}>
        <DialogContent>{receiptPaymentId != null && <ReceiptView paymentId={receiptPaymentId} />}</DialogContent>
      </Dialog>
    </div>
  )
}

function FeePlanCard({ plan, onViewReceipt }: { plan: FeePlanResponse; onViewReceipt: (id: number) => void }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <CardTitle className="text-base">Course #{plan.courseId}</CardTitle>
            <CardDescription>Net fee {formatMoney(plan.netFee, plan.currency)}</CardDescription>
          </div>
          <Badge variant={plan.overdue > 0 ? 'destructive' : plan.outstanding > 0 ? 'secondary' : 'default'}>
            {plan.overdue > 0
              ? `${formatMoney(plan.overdue, plan.currency)} overdue`
              : plan.outstanding > 0
                ? `${formatMoney(plan.outstanding, plan.currency)} due`
                : 'Fully paid'}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid grid-cols-3 gap-2 text-sm">
          <Stat label="Paid" value={formatMoney(plan.paid, plan.currency)} />
          <Stat label="Outstanding" value={formatMoney(plan.outstanding, plan.currency)} />
          <Stat
            label="Next due"
            value={plan.nextDueDate ? formatDate(plan.nextDueDate) : '—'}
          />
        </div>

        <Separator />

        <div>
          <h3 className="mb-2 text-sm font-medium">Installments</h3>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Due</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {plan.installments.map((installment) => (
                <TableRow key={installment.id}>
                  <TableCell>{installment.installmentNo}</TableCell>
                  <TableCell>{formatDate(installment.dueDate)}</TableCell>
                  <TableCell>{formatMoney(installment.amount, plan.currency)}</TableCell>
                  <TableCell>
                    <Badge variant={STATE_VARIANT[installment.state] ?? 'outline'}>{installment.state}</Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>

        {plan.payments && plan.payments.length > 0 && (
          <div>
            <h3 className="mb-2 text-sm font-medium">Payments</h3>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead>Receipt</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {plan.payments.map((payment) => (
                  <TableRow key={payment.id}>
                    <TableCell>{formatDate(payment.paymentDate)}</TableCell>
                    <TableCell>{formatMoney(payment.amount, plan.currency)}</TableCell>
                    <TableCell>{payment.method}</TableCell>
                    <TableCell>{payment.receiptNo}</TableCell>
                    <TableCell>
                      <Button size="sm" variant="ghost" onClick={() => onViewReceipt(payment.id)}>
                        <Receipt className="size-3.5" />
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium">{value}</p>
    </div>
  )
}

function ReceiptView({ paymentId }: { paymentId: number }) {
  const query = useQuery({ queryKey: ['finance', 'receipt', paymentId], queryFn: () => getReceipt(paymentId) })

  if (query.isLoading) return <Skeleton className="h-64" />
  if (!query.data) return null
  const r: ReceiptResponse = query.data

  return (
    <>
      <DialogHeader>
        <DialogTitle>Receipt {r.receiptNo}</DialogTitle>
        <DialogDescription>
          {formatDate(r.paymentDate)}
          {r.status === 'REVERSED' && ' · REVERSED'}
        </DialogDescription>
      </DialogHeader>
      <div className="flex flex-col gap-2 text-sm">
        <Row label="Received from" value={r.studentName} />
        <Row label="Amount" value={`${r.currencySymbol}${r.amount}`} />
        <Row label="Method" value={r.method} />
        {r.referenceNo && <Row label="Reference" value={r.referenceNo} />}
        <Separator className="my-2" />
        <Row label="Net fee" value={`${r.currencySymbol}${r.netFee}`} />
        <Row label="Paid to date" value={`${r.currencySymbol}${r.paidToDate}`} />
        <Row label="Balance today" value={`${r.currencySymbol}${r.balanceAsOfToday}`} />
      </div>
    </>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  )
}
