import { type FormEvent, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarPlusIcon } from 'lucide-react'
import { toast } from 'sonner'

import { extendAccess, extendBatch } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatDate } from '@/lib/format'

const iso = (date: Date) => date.toISOString().slice(0, 10)
const tomorrow = () => iso(new Date(Date.now() + 86_400_000))

/** A date and the reason for it: what both extensions ask. */
function DateAndReason({
  dateLabel,
  min,
  date,
  reason,
  onChange,
}: {
  dateLabel: string
  min: string
  date: string
  reason: string
  onChange: (next: { date?: string; reason?: string }) => void
}) {
  return (
    <>
      <div className="flex flex-col gap-2">
        <Label htmlFor="validity-date">{dateLabel}</Label>
        <Input id="validity-date" type="date" min={min} required value={date} onChange={(event) => onChange({ date: event.target.value })} />
      </div>
      <div className="flex flex-col gap-2">
        <Label htmlFor="validity-reason">Why? (kept in the record)</Label>
        <Input
          id="validity-reason"
          required
          maxLength={255}
          placeholder="Syllabus not finished, student was ill..."
          value={reason}
          onChange={(event) => onChange({ reason: event.target.value })}
        />
      </div>
    </>
  )
}

/** "Extend course" on a batch: a later end date. A finished batch runs again; students and trainer are told. */
export function ExtendBatchButton({ batchId, currentEnd }: { batchId: number; currentEnd: string | null }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ date: '', reason: '' })
  const min = currentEnd && currentEnd >= iso(new Date()) ? iso(new Date(new Date(currentEnd).getTime() + 86_400_000)) : tomorrow()

  const mutation = useMutation({
    mutationFn: () => extendBatch(batchId, { endDate: form.date, reason: form.reason.trim() }),
    onSuccess: (result) => {
      toast.success(
        `Extended to ${formatDate(result.endDate)}. The students and the trainer have been told.` +
          (result.studentsExtended > 0 ? ` ${result.studentsExtended} student access date(s) moved with it.` : ''),
      )
      setOpen(false)
      setForm({ date: '', reason: '' })
      void queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not extend the course.')),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <>
      <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
        <CalendarPlusIcon /> Extend course
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Extend this course</DialogTitle>
            <DialogDescription>
              {currentEnd ? `It is planned to end on ${formatDate(currentEnd)}. ` : ''}Choose a later date. A finished batch runs again, and the
              students and the trainer are told.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <DateAndReason
              dateLabel="New end date"
              min={min}
              date={form.date}
              reason={form.reason}
              onChange={(next) => setForm((prev) => ({ date: next.date ?? prev.date, reason: next.reason ?? prev.reason }))}
            />
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Extending...' : 'Extend'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}

/** One student's access to the course: set a date, or extend it. A student whose access ran out is back on the course. */
export function ExtendAccessButton({ enrollmentId, studentName, accessUntil, expired }: {
  enrollmentId: number
  studentName: string
  accessUntil: string | null
  expired: boolean
}) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ date: '', reason: '' })

  const mutation = useMutation({
    mutationFn: () => extendAccess(enrollmentId, { accessUntil: form.date, reason: form.reason.trim() }),
    onSuccess: () => {
      toast.success(`${studentName} can use the course until ${formatDate(form.date)}. They have been told.`)
      setOpen(false)
      setForm({ date: '', reason: '' })
      void queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change the access.')),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <>
      <Button size="sm" variant={expired ? 'default' : 'ghost'} onClick={() => setOpen(true)}>
        <CalendarPlusIcon className="size-3.5" /> {expired ? 'Reopen' : accessUntil ? 'Extend' : 'Set date'}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{expired ? 'Reopen the course for' : 'Course access for'} {studentName}</DialogTitle>
            <DialogDescription>
              {accessUntil ? `Their access ${expired ? 'ended' : 'ends'} on ${formatDate(accessUntil)}.` : 'They have no access limit now.'}{' '}
              Choose the date until which they may use the course. They are told.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <DateAndReason
              dateLabel="Access until"
              min={iso(new Date())}
              date={form.date}
              reason={form.reason}
              onChange={(next) => setForm((prev) => ({ date: next.date ?? prev.date, reason: next.reason ?? prev.reason }))}
            />
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Saving...' : 'Save'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
