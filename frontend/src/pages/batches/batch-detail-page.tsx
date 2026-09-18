import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarPlus, CheckCircle2, ChevronLeft, ClipboardList } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { getBatchAssignments } from '@/api/assignments'
import { type CreateSessionInput, createSession, getBatch, getBatchSessions, getRoster } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'

const SESSION_STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  SCHEDULED: 'secondary',
  COMPLETED: 'default',
  CANCELLED: 'destructive',
}

export function BatchDetailPage() {
  const { batchId } = useParams<{ batchId: string }>()
  const [sessionDialogOpen, setSessionDialogOpen] = useState(false)

  const batchQuery = useQuery({ queryKey: ['batches', batchId], queryFn: () => getBatch(batchId!), enabled: !!batchId })
  const rosterQuery = useQuery({
    queryKey: ['batches', batchId, 'roster'],
    queryFn: () => getRoster(batchId!),
    enabled: !!batchId,
  })
  const sessionsQuery = useQuery({
    queryKey: ['batches', batchId, 'sessions'],
    queryFn: () => getBatchSessions(batchId!),
    enabled: !!batchId,
  })
  const assignmentsQuery = useQuery({
    queryKey: ['batches', batchId, 'assignments'],
    queryFn: () => getBatchAssignments(batchId!),
    enabled: !!batchId,
  })

  if (batchQuery.isLoading) return <Skeleton className="h-96 max-w-3xl" />
  const batch = batchQuery.data
  if (!batch) return null

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Link to="/app/batches" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Batches
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{batch.batchCode}</h1>
          <p className="text-muted-foreground">
            {batch.courseTitle} &middot; {batch.trainerName ?? 'No trainer assigned'}
          </p>
        </div>
        <Badge>{batch.status}</Badge>
      </div>

      <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <Field label="Dates" value={`${formatDate(batch.startDate)} – ${formatDate(batch.endDate)}`} />
        <Field label="Time" value={`${batch.startTime}–${batch.endTime}`} />
        <Field label="Days" value={batch.classDays.join(', ')} />
        <Field label="Seats" value={`${batch.enrolledCount}/${batch.capacity}`} />
        <Field label="Mode" value={batch.mode} />
        {batch.classroom && <Field label="Classroom" value={batch.classroom} />}
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">Timetable</h2>
          <AddSessionDialog batchId={batch.id} open={sessionDialogOpen} onOpenChange={setSessionDialogOpen} />
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Time</TableHead>
              <TableHead>Topic</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Attendance</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sessionsQuery.data?.map((session) => (
              <TableRow key={session.id}>
                <TableCell>{formatDate(session.sessionDate)}</TableCell>
                <TableCell>{`${session.startTime}–${session.endTime}`}</TableCell>
                <TableCell>{session.topic ?? '—'}</TableCell>
                <TableCell>
                  <Badge variant={SESSION_STATUS_VARIANT[session.status] ?? 'outline'}>{session.status}</Badge>
                </TableCell>
                <TableCell>
                  {session.status !== 'CANCELLED' && (
                    <Button asChild size="sm" variant={session.attendanceMarked ? 'ghost' : 'outline'}>
                      <Link to={`/app/batches/${batchId}/sessions/${session.id}/attendance`}>
                        {session.attendanceMarked ? (
                          <>
                            <CheckCircle2 className="size-3.5 text-emerald-600" />
                            Marked
                          </>
                        ) : (
                          <>
                            <ClipboardList className="size-3.5" />
                            Mark
                          </>
                        )}
                      </Link>
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {sessionsQuery.isSuccess && sessionsQuery.data.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No sessions scheduled yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Assignments</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Due</TableHead>
              <TableHead>Submitted</TableHead>
              <TableHead>Evaluated</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {assignmentsQuery.data?.content.map((assignment) => (
              <TableRow key={assignment.id}>
                <TableCell>
                  <Link to={`/app/assessments/assignments/${assignment.id}`} className="font-medium hover:underline">
                    {assignment.title}
                  </Link>
                </TableCell>
                <TableCell>{formatDate(assignment.dueAt)}</TableCell>
                <TableCell>{assignment.submissionCount ?? 0}</TableCell>
                <TableCell>{assignment.evaluatedCount ?? 0}</TableCell>
                <TableCell>
                  <Badge variant="outline">{assignment.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {assignmentsQuery.isSuccess && assignmentsQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No assignments set for this batch yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Roster</h2>
        <Card>
          <CardContent className="pt-6">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Enrolled</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rosterQuery.data?.map((enrollment) => (
                  <TableRow key={enrollment.id}>
                    <TableCell>{enrollment.studentCode}</TableCell>
                    <TableCell>
                      <Link to={`/app/students/${enrollment.studentId}`} className="hover:underline">
                        {enrollment.studentName}
                      </Link>
                    </TableCell>
                    <TableCell>{formatDate(enrollment.enrolledAt)}</TableCell>
                    <TableCell>{enrollment.status}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium">{value}</p>
    </div>
  )
}

function AddSessionDialog({
  batchId,
  open,
  onOpenChange,
}: {
  batchId: number
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [form, setForm] = useState<Partial<CreateSessionInput>>({})
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => createSession({ ...form, batchId } as CreateSessionInput),
    onSuccess: () => {
      toast.success('Session scheduled')
      onOpenChange(false)
      setForm({})
      void queryClient.invalidateQueries({ queryKey: ['batches', String(batchId), 'sessions'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not schedule the session.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <CalendarPlus className="size-3.5" />
          Schedule session
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Schedule a session</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="sessionDate">Date</Label>
            <Input
              id="sessionDate"
              type="date"
              required
              onChange={(event) => setForm({ ...form, sessionDate: event.target.value })}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="startTime">Start time</Label>
              <Input id="startTime" type="time" onChange={(event) => setForm({ ...form, startTime: event.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="endTime">End time</Label>
              <Input id="endTime" type="time" onChange={(event) => setForm({ ...form, endTime: event.target.value })} />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="topic">Topic</Label>
            <Input id="topic" onChange={(event) => setForm({ ...form, topic: event.target.value })} />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Scheduling...' : 'Schedule'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
