import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { BadgePercent } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type FeePlanResponse, updateFeePlan } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { formatMoney } from '@/lib/format'

/** Gives or changes a concession (a discount) on a plan. It needs a reason; a large one needs the administrator. */
export function ConcessionButton({ plan, onDone }: { plan: FeePlanResponse; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState(String(plan.discount))
  const [reason, setReason] = useState(plan.discountReason ?? '')

  const save = useMutation({
    mutationFn: () =>
      updateFeePlan(plan.id, { totalFee: plan.totalFee, discount: Number(amount), discountReason: reason.trim() }),
    onSuccess: () => {
      toast.success('Concession saved')
      setOpen(false)
      onDone()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the concession.')),
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <BadgePercent className="size-4" /> Concession
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Concession</DialogTitle>
          <DialogDescription>
            The course fee is {formatMoney(plan.totalFee, plan.currency)}. A discount above 10% of it can only be given by the
            administrator. It is audited and the reason is kept.
          </DialogDescription>
        </DialogHeader>
        <Input aria-label="Discount" type="number" min={0} max={plan.totalFee} value={amount}
          onChange={(e) => setAmount(e.target.value)} />
        <Input aria-label="Reason" placeholder="Reason, for example Sibling of a student" maxLength={255} value={reason}
          onChange={(e) => setReason(e.target.value)} />
        <DialogFooter>
          <Button disabled={save.isPending || (Number(amount) > 0 && !reason.trim())} onClick={() => save.mutate()}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
