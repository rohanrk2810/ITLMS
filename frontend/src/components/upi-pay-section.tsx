import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { claimUpiPayment, type FeePlanResponse, type UpiConfig } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatMoney } from '@/lib/format'

/**
 * Pay the institute's UPI id from any UPI app: a QR code for the amount, then the transaction reference (UTR) so the
 * finance desk can match it with the bank and record the payment.
 */
export function UpiPaySection({ plan, config, onDone }: { plan: FeePlanResponse; config: UpiConfig; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(String(plan.outstanding))
  const [utr, setUtr] = useState('')
  const [qr, setQr] = useState<string | null>(null)
  const value = Number(amount)
  const amountOk = Number.isFinite(value) && value >= 1 && value <= plan.outstanding

  const link = amountOk
    ? `upi://pay?pa=${encodeURIComponent(config.upiId ?? '')}&pn=${encodeURIComponent(config.payeeName)}` +
      `&am=${value.toFixed(2)}&cu=${plan.currency}&tn=${encodeURIComponent(`Fees ${plan.studentCode ?? ''}`.trim())}`
    : null

  useEffect(() => {
    let live = true
    if (!link) return
    QRCode.toDataURL(link, { margin: 1, width: 192 })
      .then((url) => live && setQr(url))
      .catch(() => live && setQr(null))
    return () => {
      live = false
    }
  }, [link])

  const send = useMutation({
    mutationFn: () => claimUpiPayment({ feePlanId: plan.id, amount: value, utr: utr.trim() }),
    onSuccess: () => {
      toast.success('Sent. The office will confirm it and you will get your receipt.')
      setUtr('')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
      onDone()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not send the payment details.')),
  })

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-3">
      <p className="text-sm font-medium">Pay by UPI</p>
      <div className="flex flex-col gap-2">
        <Label htmlFor={`upi-amount-${plan.id}`}>Amount</Label>
        <Input id={`upi-amount-${plan.id}`} type="number" min={1} max={plan.outstanding} step="0.01" value={amount}
          onChange={(e) => setAmount(e.target.value)} />
      </div>
      {link && qr ? (
        <div className="flex flex-col items-center gap-2">
          <img src={qr} alt={`UPI QR code for ${formatMoney(value, plan.currency)}`} className="size-48" />
          <p className="text-xs text-muted-foreground">
            Scan with any UPI app, or pay to <span className="font-medium text-foreground">{config.upiId}</span>
          </p>
          <Button asChild variant="outline" size="sm" className="sm:hidden">
            <a href={link}>Open my UPI app</a>
          </Button>
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">Enter an amount up to {formatMoney(plan.outstanding, plan.currency)}.</p>
      )}
      <div className="flex flex-col gap-2">
        <Label htmlFor={`upi-utr-${plan.id}`}>After paying, the UPI transaction reference (UTR)</Label>
        <div className="flex gap-2">
          <Input id={`upi-utr-${plan.id}`} placeholder="12-digit reference" maxLength={40} value={utr}
            onChange={(e) => setUtr(e.target.value)} />
          <Button type="button" disabled={!amountOk || utr.trim().length < 6 || send.isPending} onClick={() => send.mutate()}>
            I have paid
          </Button>
        </div>
      </div>
    </div>
  )
}
