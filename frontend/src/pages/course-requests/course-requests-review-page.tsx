import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { searchBatches } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import {
  approveCourseRequest,
  type CourseRequestResponse,
  type CourseRequestStatus,
  listCourseRequests,
  rejectCourseRequest,
} from '@/api/course-requests'
import { RequestStatusBadge } from '@/components/request-status-badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate } from '@/lib/format'

const TABS: Array<{ value: CourseRequestStatus; label: string }> = [
  { value: 'PENDING', label: 'Waiting' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Not approved' },
  { value: 'CANCELLED', label: 'Withdrawn' },
]

const OPEN_BATCH = new Set(['PLANNED', 'ONGOING'])

export function CourseRequestsReviewPage() {
  const [status, setStatus] = useState<CourseRequestStatus>('PENDING')
  const [approving, setApproving] = useState<CourseRequestResponse | null>(null)
  const [rejecting, setRejecting] = useState<CourseRequestResponse | null>(null)

  const query = useQuery({
    queryKey: ['course-requests', 'list', status],
    queryFn: () => listCourseRequests({ status }),
  })

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Course requests</h1>
        <p className="text-muted-foreground">Students asking to join a course. Approving puts them in a batch.</p>
      </div>

      <Tabs value={status} onValueChange={(value) => setStatus(value as CourseRequestStatus)}>
        <TabsList>
          {TABS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {query.isLoading && <Skeleton className="h-40" />}
      {query.isSuccess && query.data.content.length === 0 && (
        <p className="text-sm text-muted-foreground">Nothing here.</p>
      )}

      {query.data?.content.map((request) => (
        <RequestCard key={request.id} request={request} onApprove={() => setApproving(request)} onReject={() => setRejecting(request)} />
      ))}

      {approving && <ApproveDialog request={approving} onClose={() => setApproving(null)} />}
      {rejecting && <RejectDialog request={rejecting} onClose={() => setRejecting(null)} />}
    </div>
  )
}

function RequestCard({
  request,
  onApprove,
  onReject,
}: {
  request: CourseRequestResponse
  onApprove: () => void
  onReject: () => void
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="text-base">
            {request.studentName ?? `Student #${request.studentId}`} &rarr; {request.courseTitle ?? `Course #${request.courseId}`}
          </CardTitle>
          <RequestStatusBadge status={request.status} />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 text-sm">
        <dl className="grid gap-x-6 gap-y-1 sm:grid-cols-2">
          <Detail label="Student code" value={request.studentCode} />
          <Detail label="Email" value={request.studentEmail} />
          <Detail label="Phone" value={request.studentPhone} />
          <Detail label="Course" value={[request.courseCode, request.courseTitle].filter(Boolean).join(' - ')} />
          <Detail label="Sent" value={formatDate(request.createdAt)} />
          {request.preferredBatchId && <Detail label="Asked for batch" value={`#${request.preferredBatchId}`} />}
        </dl>
        {request.message && <p className="rounded-md bg-muted px-3 py-2 whitespace-pre-wrap">{request.message}</p>}
        {request.status !== 'PENDING' && request.decidedAt && (
          <p className="text-xs text-muted-foreground">
            {request.status === 'CANCELLED' ? 'Withdrawn' : 'Decided'} {formatDate(request.decidedAt)}
            {request.decidedByName && ` by ${request.decidedByName}`}
            {request.decisionNote && ` - ${request.decisionNote}`}
            {request.approvedBatchId && ` - placed in batch #${request.approvedBatchId}`}
          </p>
        )}
        {request.status === 'PENDING' && (
          <div className="flex gap-2">
            <Button size="sm" onClick={onApprove}>
              Approve
            </Button>
            <Button size="sm" variant="outline" onClick={onReject}>
              Reject
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function Detail({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex gap-2">
      <dt className="text-muted-foreground">{label}:</dt>
      <dd>{value || '-'}</dd>
    </div>
  )
}

function ApproveDialog({ request, onClose }: { request: CourseRequestResponse; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [batchId, setBatchId] = useState(request.preferredBatchId ? String(request.preferredBatchId) : '')
  const [note, setNote] = useState('')

  const batchesQuery = useQuery({
    queryKey: ['batches', 'open-for-course', request.courseId],
    queryFn: () => searchBatches({ courseId: request.courseId }),
  })
  const openBatches = (batchesQuery.data?.content ?? []).filter((b) => OPEN_BATCH.has(b.status))

  const mutation = useMutation({
    mutationFn: () => approveCourseRequest(request.id, { batchId: Number(batchId), note: note.trim() || undefined }),
    onSuccess: () => {
      toast.success('Approved. The student has been added to the batch.')
      void queryClient.invalidateQueries({ queryKey: ['course-requests'] })
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not approve the request.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Approve request</DialogTitle>
          <DialogDescription>
            Add {request.studentName} to a batch of {request.courseTitle}.
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="approve-batch">Batch</Label>
            <select
              id="approve-batch"
              value={batchId}
              onChange={(event) => setBatchId(event.target.value)}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
              required
            >
              <option value="" disabled>
                Select a batch
              </option>
              {openBatches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.batchCode}) - {b.enrolledCount}/{b.capacity} seats
                </option>
              ))}
            </select>
            {batchesQuery.isSuccess && openBatches.length === 0 && (
              <p className="text-xs text-destructive">This course has no batch open for students. Create one first.</p>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="approve-note">Note to the student (optional)</Label>
            <textarea
              id="approve-note"
              rows={2}
              maxLength={500}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending || !batchId}>
              {mutation.isPending ? 'Approving...' : 'Approve'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function RejectDialog({ request, onClose }: { request: CourseRequestResponse; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')

  const mutation = useMutation({
    mutationFn: () => rejectCourseRequest(request.id, note.trim()),
    onSuccess: () => {
      toast.success('Request rejected')
      void queryClient.invalidateQueries({ queryKey: ['course-requests'] })
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not reject the request.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reject request</DialogTitle>
          <DialogDescription>{request.studentName} will see this reason.</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="reject-note">Reason</Label>
            <textarea
              id="reject-note"
              rows={3}
              maxLength={500}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              required
            />
          </div>
          <DialogFooter>
            <Button type="submit" variant="destructive" disabled={mutation.isPending || !note.trim()}>
              {mutation.isPending ? 'Rejecting...' : 'Reject'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
