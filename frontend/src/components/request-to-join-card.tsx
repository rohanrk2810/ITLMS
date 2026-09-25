import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { searchBatches } from '@/api/batches'
import { createCourseRequest, myCourseRequests } from '@/api/course-requests'
import { RequestStatusBadge } from '@/components/request-status-badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { formatDate } from '@/lib/format'

const OPEN_BATCH = new Set(['PLANNED', 'ONGOING'])

/**
 * Shown to a student on a course they are not in. They read the outline above, then ask to join:
 * the request goes to the administrators and coordinators, who approve it into a batch.
 * If they already asked, this shows where that request stands instead of a second form.
 */
export function RequestToJoinCard({ courseId, courseTitle }: { courseId: number; courseTitle: string }) {
  const queryClient = useQueryClient()
  const [batchId, setBatchId] = useState('')
  const [message, setMessage] = useState('')

  const requestsQuery = useQuery({ queryKey: ['course-requests', 'mine'], queryFn: myCourseRequests })
  const batchesQuery = useQuery({
    queryKey: ['batches', 'open-for-course', courseId],
    queryFn: () => searchBatches({ courseId }),
  })

  const mutation = useMutation({
    mutationFn: () =>
      createCourseRequest({
        courseId,
        batchId: batchId ? Number(batchId) : undefined,
        message: message.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success('Request sent. You will be told when it is decided.')
      setMessage('')
      void queryClient.invalidateQueries({ queryKey: ['course-requests'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not send the request.')),
  })

  const latest = requestsQuery.data?.find((r) => r.courseId === courseId)
  const openBatches = (batchesQuery.data?.content ?? []).filter((b) => OPEN_BATCH.has(b.status))

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  if (latest && latest.status !== 'REJECTED' && latest.status !== 'CANCELLED') {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            Your request <RequestStatusBadge status={latest.status} />
          </CardTitle>
          <CardDescription>
            {latest.status === 'PENDING'
              ? `Sent on ${formatDate(latest.createdAt)}. An administrator will review it.`
              : 'Approved. Your course is under My courses.'}
          </CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Join {courseTitle}</CardTitle>
        <CardDescription>
          Send a request to the institute. Once it is approved you are added to a batch and the lessons open up.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {latest && (
          <p className="mb-3 rounded-md bg-muted px-3 py-2 text-sm">
            Your last request was <RequestStatusBadge status={latest.status} />
            {latest.decisionNote ? ` - ${latest.decisionNote}` : ''}. You can ask again.
          </p>
        )}
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="join-batch">Preferred batch (optional)</Label>
            <select
              id="join-batch"
              value={batchId}
              onChange={(event) => setBatchId(event.target.value)}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              <option value="">No preference</option>
              {openBatches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.batchCode}) - starts {formatDate(b.startDate)}, {b.enrolledCount}/{b.capacity} seats
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="join-message">Anything the institute should know (optional)</Label>
            <textarea
              id="join-message"
              rows={3}
              maxLength={1000}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
            />
          </div>
          <Button type="submit" className="self-start" disabled={mutation.isPending}>
            {mutation.isPending ? 'Sending...' : 'Request to join'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
