import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CreditCardIcon } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import type { FeePlanResponse } from '@/api/finance'
import { UpiPaySection } from '@/components/upi-pay-section'
import { getUpiConfig } from '@/api/finance'
import { createFeeOrder, getCheckoutConfig, payWithRazorpay, verifyOnlinePayment } from '@/api/online-payments'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth-store'

/** Whether online payment is set up. The Pay buttons stay hidden until it is. */
function useOnlinePaymentReady(): boolean {
  const signedIn = useAuthStore((state) => state.status) === 'authenticated'
  const config = useQuery({ queryKey: ['online-payments', 'config'], queryFn: getCheckoutConfig, enabled: signedIn, staleTime: 5 * 60_000 })
  return config.data?.enabled === true
}

/** Opens Razorpay for an amount of one fee plan, then has the server record it. */
function useFeePayment(plan: FeePlanResponse) {
  const queryClient = useQueryClient()
  const user = useAuthStore((state) => state.user)
  const [paying, setPaying] = useState(false)

  async function pay(amount?: number): Promise<boolean> {
    setPaying(true)
    try {
      const order = await createFeeOrder(plan.id, amount)
      const result = await payWithRazorpay(order, { name: user?.fullName, email: user?.email, contact: user?.phone })
      if (!result) return false // the student closed the payment window
      const status = await verifyOnlinePayment(result)
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
      toast.success(status.message ?? 'Payment received.')
      return true
    } catch (error) {
      toast.error(apiErrorMessage(error, error instanceof Error ? error.message : 'The payment could not be completed.'))
      return false
    } finally {
      setPaying(false)
    }
  }
  return { pay, paying }
}

/** "Pay online" for a fee plan: the next installment, the whole balance, or an amount the student types. */
export function PayFeesButton({ plan }: { plan: FeePlanResponse }) {
  const ready = useOnlinePaymentReady()
  const signedIn = useAuthStore((state) => state.status) === 'authenticated'
  const upi = useQuery({ queryKey: ['finance', 'upi-config'], queryFn: getUpiConfig, enabled: signedIn, staleTime: 5 * 60_000 })
  const upiReady = upi.data?.enabled === true
  const { pay, paying } = useFeePayment(plan)
  const [open, setOpen] = useState(false)
  const [custom, setCustom] = useState('')

  if ((!ready && !upiReady) || plan.status !== 'ACTIVE' || plan.outstanding <= 0) return null

  const next = plan.nextDueAmount != null && plan.nextDueAmount > 0 ? Math.min(plan.nextDueAmount, plan.outstanding) : null
  const nextIsPart = next != null && next < plan.outstanding
  const typed = Number(custom)
  const typedOk = custom.trim() !== '' && Number.isFinite(typed) && typed >= 1 && typed <= plan.outstanding

  async function go(amount?: number) {
    if (await pay(amount)) setOpen(false)
  }

  return (
    <>
      <Button size="sm" className="self-start" onClick={() => setOpen(true)}>
        <CreditCardIcon /> Pay online
      </Button>
      <Dialog open={open} onOpenChange={(value) => !paying && setOpen(value)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Pay your fees online</DialogTitle>
            <DialogDescription>
              {formatMoney(plan.outstanding, plan.currency)} is still to pay. You get a receipt by email as soon as the payment is confirmed.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            {ready && nextIsPart && (
              <Button type="button" disabled={paying} onClick={() => void go(next)}>
                <CreditCardIcon /> Pay next installment {formatMoney(next, plan.currency)}
              </Button>
            )}
            {ready && (
              <Button type="button" variant={nextIsPart ? 'outline' : 'default'} disabled={paying} onClick={() => void go()}>
                <CreditCardIcon /> Pay full balance {formatMoney(plan.outstanding, plan.currency)}
              </Button>
            )}
            {ready && (
            <div className="flex flex-col gap-2 rounded-lg border p-3">
              <Label htmlFor={`pay-amount-${plan.id}`}>Or another amount</Label>
              <div className="flex gap-2">
                <Input
                  id={`pay-amount-${plan.id}`}
                  type="number"
                  inputMode="decimal"
                  min={1}
                  max={plan.outstanding}
                  step="0.01"
                  placeholder={`1 to ${plan.outstanding}`}
                  value={custom}
                  onChange={(event) => setCustom(event.target.value)}
                />
                <Button type="button" variant="outline" disabled={paying || !typedOk} onClick={() => void go(typed)}>
                  Pay
                </Button>
              </div>
            </div>
            )}
            {upiReady && upi.data && <UpiPaySection plan={plan} config={upi.data} onDone={() => setOpen(false)} />}
            {paying && <p className="text-center text-xs text-muted-foreground">Opening the payment window...</p>}
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

/** A small "Pay" beside one installment that is not yet paid: it pays what is left of that installment. */
export function PayInstallmentButton({ plan, remaining }: { plan: FeePlanResponse; remaining: number }) {
  const ready = useOnlinePaymentReady()
  const { pay, paying } = useFeePayment(plan)
  if (!ready || plan.status !== 'ACTIVE' || remaining <= 0) return null
  return (
    <Button type="button" size="sm" variant="ghost" disabled={paying} onClick={() => void pay(Math.min(remaining, plan.outstanding))}>
      <CreditCardIcon className="size-3.5" />
      {paying ? '...' : 'Pay'}
    </Button>
  )
}
