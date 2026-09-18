import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Receipt, Undo2 } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  cancelFeePlan,
  getFeePlan,
  getReceipt,
  type RecordPaymentInput,
  recordPayment,
  type ReceiptResponse,
  reversePayment,
} from '@/api/finance'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'

const METHODS = ['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'ONLINE']

const STATE_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  PAID: 'default',
  PARTLY_PAID: 'secondary',
  DUE: 'outline',
  OVERDUE: 'destructive',
  UPCOMING: 'outline',
}

export function FeePlanDetailPage() {
  const { feePlanId } = useParams<{ feePlanId: string }>()
  const queryClient = useQueryClient()
  const [receiptPaymentId, setReceiptPaymentId] = useState<number | null>(null)

  const query = useQuery({
    queryKey: ['finance', 'plans', feePlanId],
    queryFn: () => getFeePlan(feePlanId!),
    enabled: !!feePlanId,
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: ['finance', 'plans', feePlanId] })
  }

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => cancelFeePlan(feePlanId!, reason),
    onSuccess: () => {
      toast.success('Fee plan cancelled')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not cancel the plan.')),
  })

  const reverseMutation = useMutation({
    mutationFn: ({ paymentId, reason }: { paymentId: number; reason: string }) => reversePayment(paymentId, reason),
    onSuccess: () => {
      toast.success('Payment reversed')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not reverse the payment.')),
  })

  if (query.isLoading) return <Skeleton className="h-96 max-w-3xl" />
  const plan = query.data
  if (!plan) return null

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Link to="/app/finance" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Finance
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{plan.studentName}</h1>
          <p className="text-muted-foreground">
            {plan.studentCode} &middot; Course #{plan.courseId} &middot; Net fee {formatMoney(plan.netFee, plan.currency)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={plan.status === 'CANCELLED' ? 'destructive' : 'default'}>{plan.status}</Badge>
          {plan.status === 'ACTIVE' && plan.paid === 0 && (
            <CancelPlanDialog onConfirm={(reason) => cancelMutation.mutate(reason)} pending={cancelMutation.isPending} />
          )}
          {plan.status === 'ACTIVE' && (
            <RecordPaymentDialog feePlanId={plan.id} onRecorded={invalidate} />
          )}
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4 text-sm">
        <Stat label="Paid" value={formatMoney(plan.paid, plan.currency)} />
        <Stat label="Outstanding" value={formatMoney(plan.outstanding, plan.currency)} />
        <Stat label="Overdue" value={formatMoney(plan.overdue, plan.currency)} />
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Installments</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>#</TableHead>
              <TableHead>Due</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Paid</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {plan.installments.map((installment) => (
              <TableRow key={installment.id}>
                <TableCell>{installment.installmentNo}</TableCell>
                <TableCell>{formatDate(installment.dueDate)}</TableCell>
                <TableCell>{formatMoney(installment.amount, plan.currency)}</TableCell>
                <TableCell>{formatMoney(installment.paid, plan.currency)}</TableCell>
                <TableCell>
                  <Badge variant={STATE_VARIANT[installment.state] ?? 'outline'}>{installment.state}</Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Payments</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Method</TableHead>
              <TableHead>Receipt</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {plan.payments?.map((payment) => (
              <TableRow key={payment.id}>
                <TableCell>{formatDate(payment.paymentDate)}</TableCell>
                <TableCell>{formatMoney(payment.amount, plan.currency)}</TableCell>
                <TableCell>{payment.method}</TableCell>
                <TableCell>{payment.receiptNo}</TableCell>
                <TableCell>
                  <Badge variant={payment.status === 'REVERSED' ? 'destructive' : 'default'}>{payment.status}</Badge>
                </TableCell>
                <TableCell className="flex gap-1">
                  <Button size="sm" variant="ghost" onClick={() => setReceiptPaymentId(payment.id)}>
                    <Receipt className="size-3.5" />
                  </Button>
                  {payment.status !== 'REVERSED' && (
                    <ReverseDialog
                      onConfirm={(reason) => reverseMutation.mutate({ paymentId: payment.id, reason })}
                      pending={reverseMutation.isPending}
                    />
                  )}
                </TableCell>
              </TableRow>
            ))}
            {plan.payments?.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No payments yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Dialog open={receiptPaymentId != null} onOpenChange={(open) => !open && setReceiptPaymentId(null)}>
        <DialogContent>{receiptPaymentId != null && <ReceiptView paymentId={receiptPaymentId} />}</DialogContent>
      </Dialog>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">{value}</p>
    </div>
  )
}

function RecordPaymentDialog({ feePlanId, onRecorded }: { feePlanId: number; onRecorded: () => void }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<Partial<RecordPaymentInput>>({ method: 'CASH' })

  const mutation = useMutation({
    mutationFn: () => recordPayment({ ...form, feePlanId } as RecordPaymentInput),
    onSuccess: () => {
      toast.success('Payment recorded')
      setOpen(false)
      setForm({ method: 'CASH' })
      onRecorded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not record the payment.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">Record payment</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Record a payment</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="amount">Amount</Label>
            <Input
              id="amount"
              type="number"
              step="0.01"
              required
              onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="method">Method</Label>
              <select
                id="method"
                value={form.method}
                onChange={(event) => setForm({ ...form, method: event.target.value })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                {METHODS.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="referenceNo">Reference no.</Label>
              <Input
                id="referenceNo"
                onChange={(event) => setForm({ ...form, referenceNo: event.target.value })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Recording...' : 'Record payment'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ReverseDialog({ onConfirm, pending }: { onConfirm: (reason: string) => void; pending: boolean }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <Undo2 className="size-3.5" />
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reverse this payment</DialogTitle>
          <DialogDescription>For a bounced cheque or a mistaken entry. This is audited.</DialogDescription>
        </DialogHeader>
        <Input placeholder="Reason" value={reason} onChange={(event) => setReason(event.target.value)} />
        <DialogFooter>
          <Button
            variant="destructive"
            disabled={!reason.trim() || pending}
            onClick={() => {
              onConfirm(reason)
              setOpen(false)
              setReason('')
            }}
          >
            Reverse
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function CancelPlanDialog({ onConfirm, pending }: { onConfirm: (reason: string) => void; pending: boolean }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          Cancel plan
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cancel this fee plan</DialogTitle>
          <DialogDescription>Only possible while no money has been received on it.</DialogDescription>
        </DialogHeader>
        <Input placeholder="Reason" value={reason} onChange={(event) => setReason(event.target.value)} />
        <DialogFooter>
          <Button
            variant="destructive"
            disabled={!reason.trim() || pending}
            onClick={() => {
              onConfirm(reason)
              setOpen(false)
              setReason('')
            }}
          >
            Cancel plan
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
