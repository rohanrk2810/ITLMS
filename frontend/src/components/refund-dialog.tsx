import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { HandCoins } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { requestRefund } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { formatMoney } from '@/lib/format'

/** Asks the administrator to refund part or all of a payment. */
export function RefundDialog({ paymentId, available }: { paymentId: number; available: number }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState(String(available))
  const [reason, setReason] = useState('')

  const ask = useMutation({
    mutationFn: () => requestRefund({ paymentId, amount: Number(amount), reason }),
    onSuccess: () => {
      toast.success('Refund requested. The administrator will decide.')
      setOpen(false)
      setReason('')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not ask for the refund.')),
  })

  if (available <= 0) return null
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost" title="Refund">
          <HandCoins className="size-3.5" />
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Refund on this payment</DialogTitle>
          <DialogDescription>
            Up to {formatMoney(available)} can be refunded. The administrator approves it; the fee is then owed again.
          </DialogDescription>
        </DialogHeader>
        <Input aria-label="Amount" type="number" min={1} max={available} value={amount}
          onChange={(e) => setAmount(e.target.value)} />
        <Input aria-label="Reason" placeholder="Reason" maxLength={255} value={reason}
          onChange={(e) => setReason(e.target.value)} />
        <DialogFooter>
          <Button disabled={!reason.trim() || !(Number(amount) > 0) || ask.isPending} onClick={() => ask.mutate()}>
            Ask for refund
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
