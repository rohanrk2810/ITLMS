import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { cancelCourseRequest, myCourseRequests } from '@/api/course-requests'
import { RequestStatusBadge } from '@/components/request-status-badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

export function MyCourseRequestsPage() {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['course-requests', 'mine'], queryFn: myCourseRequests })

  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelCourseRequest(id),
    onSuccess: () => {
      toast.success('Request withdrawn')
      void queryClient.invalidateQueries({ queryKey: ['course-requests'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not withdraw the request.')),
  })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">My course requests</h1>
        <p className="text-muted-foreground">
          Courses you asked to join. Find a course under <Link to="/app/courses" className="text-primary hover:underline">My courses</Link>.
        </p>
      </div>

      {query.isLoading && <Skeleton className="h-32" />}
      {query.isSuccess && query.data.length === 0 && (
        <p className="text-sm text-muted-foreground">You have not asked to join any course yet.</p>
      )}

      {query.data?.map((request) => (
        <Card key={request.id}>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-base">{request.courseTitle ?? `Course #${request.courseId}`}</CardTitle>
              <RequestStatusBadge status={request.status} />
            </div>
            <CardDescription>Sent {formatDate(request.createdAt)}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            {request.message && <p className="whitespace-pre-wrap">{request.message}</p>}
            {request.decisionNote && (
              <p className="rounded-md bg-muted px-3 py-2">
                <span className="font-medium">{request.decidedByName ?? 'The institute'}:</span> {request.decisionNote}
              </p>
            )}
            <div className="flex flex-wrap items-center gap-2">
              {request.status === 'APPROVED' && (
                <Button asChild size="sm">
                  <Link to={`/app/courses/${request.courseId}`}>Open the course</Link>
                </Button>
              )}
              {request.status === 'REJECTED' && (
                <Button asChild size="sm" variant="outline">
                  <Link to={`/app/courses/${request.courseId}`}>Ask again</Link>
                </Button>
              )}
              {request.status === 'PENDING' && (
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate(request.id)}
                >
                  Withdraw
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
