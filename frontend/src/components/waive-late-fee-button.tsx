import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { waiveLateFee } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

/** The administrator takes a late fee off a plan. */
export function WaiveLateFeeButton({ feePlanId, installmentId, onDone }: { feePlanId: number; installmentId: number; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const waive = useMutation({
    mutationFn: () => waiveLateFee(feePlanId, installmentId, reason.trim()),
    onSuccess: () => {
      toast.success('Late fee waived')
      setOpen(false)
      onDone()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not waive the late fee.')),
  })
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">Waive</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Waive this late fee</DialogTitle>
          <DialogDescription>The student is told it was removed. This is audited.</DialogDescription>
        </DialogHeader>
        <Input aria-label="Reason" placeholder="Reason" maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />
        <DialogFooter>
          <Button disabled={!reason.trim() || waive.isPending} onClick={() => waive.mutate()}>Waive</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
