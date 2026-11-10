import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PlusIcon } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { addBatchCharge, addCharge } from '@/api/finance'
import { searchBatches } from '@/api/batches'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const today = () => new Date().toISOString().slice(0, 10)

/** The three fields of an extra charge: what it is for, how much, and when it falls due. */
function ChargeFields({
  title,
  amount,
  dueDate,
  onChange,
}: {
  title: string
  amount: string
  dueDate: string
  onChange: (next: { title?: string; amount?: string; dueDate?: string }) => void
}) {
  return (
    <>
      <div className="flex flex-col gap-2">
        <Label htmlFor="charge-title">What is it for?</Label>
        <Input
          id="charge-title"
          list="charge-suggestions"
          placeholder="Development fee, New book, Exam fee..."
          maxLength={120}
          required
          value={title}
          onChange={(event) => onChange({ title: event.target.value })}
        />
        <datalist id="charge-suggestions">
          <option value="Development fee" />
          <option value="New book" />
          <option value="Exam fee" />
          <option value="Certificate fee" />
          <option value="Lab fee" />
        </datalist>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor="charge-amount">Amount</Label>
          <Input
            id="charge-amount"
            type="number"
            min={1}
            step="0.01"
            required
            value={amount}
            onChange={(event) => onChange({ amount: event.target.value })}
          />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="charge-due">Pay by</Label>
          <Input id="charge-due" type="date" min={today()} required value={dueDate} onChange={(event) => onChange({ dueDate: event.target.value })} />
        </div>
      </div>
    </>
  )
}

/** "Add charge" on one student's fee plan: a development fee, a book, an exam fee. The student is told. */
export function AddChargeButton({ feePlanId, onAdded }: { feePlanId: number; onAdded: () => void }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ title: '', amount: '', dueDate: today() })
  const mutation = useMutation({
    mutationFn: () => addCharge(feePlanId, { title: form.title.trim(), amount: Number(form.amount), dueDate: form.dueDate }),
    onSuccess: () => {
      toast.success('Charge added. The student has been told.')
      setOpen(false)
      setForm({ title: '', amount: '', dueDate: today() })
      onAdded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the charge.')),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <>
      <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
        <PlusIcon /> Add charge
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add a charge</DialogTitle>
            <DialogDescription>
              It is added to this student&apos;s fees as a separate item. They see it under Fees, get a notification and can pay it online.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <ChargeFields {...form} onChange={(next) => setForm((prev) => ({ ...prev, ...next }))} />
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Adding...' : 'Add charge'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}

/** "Charge a batch" on the finance desk: the same charge for every student of one batch. */
export function ChargeBatchButton() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [batchId, setBatchId] = useState('')
  const [form, setForm] = useState({ title: '', amount: '', dueDate: today() })
  const batches = useQuery({ queryKey: ['finance', 'charge-batches'], queryFn: () => searchBatches({}), enabled: open })
  const list = batches.data?.content ?? []

  const mutation = useMutation({
    mutationFn: () => addBatchCharge({ batchId: Number(batchId), title: form.title.trim(), amount: Number(form.amount), dueDate: form.dueDate }),
    onSuccess: (result) => {
      toast.success(
        result.students > 0
          ? `Charge added for ${result.students} student${result.students === 1 ? '' : 's'}. They have been told.`
          : 'No student in this batch has a fee plan yet, so nothing was charged.',
      )
      setOpen(false)
      setForm({ title: '', amount: '', dueDate: today() })
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the charge.')),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <PlusIcon /> Charge a batch
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Charge a whole batch</DialogTitle>
            <DialogDescription>
              The same charge is added to the fees of every student in the batch who has a fee plan. Each student is told.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="charge-batch">Batch</Label>
              <select
                id="charge-batch"
                required
                value={batchId}
                onChange={(event) => setBatchId(event.target.value)}
                className="border-input h-9 rounded-md border bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
              >
                <option value="">{batches.isLoading ? 'Loading...' : 'Choose a batch'}</option>
                {list.map((batch) => (
                  <option key={batch.id} value={batch.id}>
                    {batch.batchCode} - {batch.courseTitle}
                  </option>
                ))}
              </select>
            </div>
            <ChargeFields {...form} onChange={(next) => setForm((prev) => ({ ...prev, ...next }))} />
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending || !batchId}>
                {mutation.isPending ? 'Adding...' : 'Charge the batch'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
