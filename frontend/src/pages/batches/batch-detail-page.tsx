import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookPlus, CalendarPlus, CheckCircle2, ChevronLeft, ClipboardList, Pencil, Trash2, UserPlus } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { getBatchAssignments } from '@/api/assignments'
import {
  addBatchCourse,
  type BatchCourse,
  type BatchResponse,
  changeBatchCourseTrainer,
  type CreateSessionInput,
  createSession,
  enrollStudents,
  getBatch,
  getBatchAttendance,
  getBatchSessions,
  getRoster,
  removeBatchCourse,
  setBatchFee,
} from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import { searchCourses } from '@/api/courses'
import { type StudentSummaryResponse, searchStudents } from '@/api/students'
import { listTrainers } from '@/api/trainers'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ExportMenu } from '@/components/export-menu'
import { attendanceReport } from '@/lib/export-rows'
import { formatDate, formatMoney } from '@/lib/format'
import { type ClassProvider, newJitsiRoomUrl } from '@/lib/jitsi'
import { ExtendAccessButton, ExtendBatchButton } from '@/components/extend-validity'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const SESSION_STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  SCHEDULED: 'secondary',
  COMPLETED: 'default',
  CANCELLED: 'destructive',
}

export function BatchDetailPage() {
  const { batchId } = useParams<{ batchId: string }>()
  const [sessionDialogOpen, setSessionDialogOpen] = useState(false)
  const [enrollDialogOpen, setEnrollDialogOpen] = useState(false)
  const roles = useAuthStore((state) => state.roles)
  const canManage = hasRole(roles, ['ADMIN', 'COORDINATOR'])

  const batchQuery = useQuery({ queryKey: ['batches', batchId], queryFn: () => getBatch(batchId!), enabled: !!batchId })
  const rosterQuery = useQuery({
    queryKey: ['batches', batchId, 'roster'],
    queryFn: () => getRoster(batchId!, true),
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
        <div className="flex flex-wrap items-center gap-2">
          <ExportMenu
            label="Download attendance"
            report="ATTENDANCE"
            detail={batch.batchCode}
            build={async () =>
              attendanceReport(await getBatchAttendance(batch.id), {
                code: batch.batchCode,
                course: batch.courseTitle,
                trainer: batch.trainerName,
              })
            }
          />
          {canManage && batch.status !== 'CANCELLED' && <ExtendBatchButton batchId={batch.id} currentEnd={batch.endDate} />}
          <Badge>{batch.status}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <Field label="Dates" value={`${formatDate(batch.startDate)} – ${formatDate(batch.endDate)}`} />
        <Field label="Time" value={`${batch.startTime}–${batch.endTime}`} />
        <Field label="Days" value={batch.classDays.join(', ')} />
        <Field label="Seats" value={`${batch.enrolledCount}/${batch.capacity}`} />
        <Field label="Mode" value={batch.mode} />
        <Field label="Batch fee" value={batch.fee != null ? formatMoney(batch.fee) : 'Course fee applies'} />
        {batch.classroom && <Field label="Classroom" value={batch.classroom} />}
      </div>

      <BatchCoursesSection batch={batch} canManage={canManage} />

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">Timetable</h2>
          <AddSessionDialog
            batchId={batch.id}
            courses={batch.courses}
            open={sessionDialogOpen}
            onOpenChange={setSessionDialogOpen}
          />
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Time</TableHead>
              <TableHead>Topic</TableHead>
              {batch.courses.length > 1 && <TableHead>Course</TableHead>}
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
                {batch.courses.length > 1 && (
                  <TableCell>
                    {session.courseTitle ?? batch.courseTitle}
                    {session.trainerName && (
                      <span className="block text-xs text-muted-foreground">{session.trainerName}</span>
                    )}
                  </TableCell>
                )}
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
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">Roster</h2>
          {canManage && (
            <EnrollStudentDialog
              batchId={batch.id}
              courses={batch.courses}
              open={enrollDialogOpen}
              onOpenChange={setEnrollDialogOpen}
            />
          )}
        </div>
        <Card>
          <CardContent className="pt-6">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Enrolled</TableHead>
                  <TableHead>Access until</TableHead>
                  <TableHead>Status</TableHead>
                  {canManage && <TableHead />}
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
                      {enrollment.courseOnly && (
                        <span className="block text-xs text-muted-foreground">
                          {batch.courses.find((c) => c.courseId === enrollment.courseId)?.courseTitle ?? 'One course'} only
                        </span>
                      )}
                    </TableCell>
                    <TableCell>{formatDate(enrollment.enrolledAt)}</TableCell>
                    <TableCell>{enrollment.accessUntil ? formatDate(enrollment.accessUntil) : 'No limit'}</TableCell>
                    <TableCell>
                      <Badge variant={enrollment.status === 'EXPIRED' ? 'destructive' : 'outline'}>{enrollment.status}</Badge>
                    </TableCell>
                    {canManage && (
                      <TableCell>
                        <ExtendAccessButton
                          enrollmentId={enrollment.id}
                          studentName={enrollment.studentName}
                          accessUntil={enrollment.accessUntil}
                          expired={enrollment.status === 'EXPIRED'}
                        />
                      </TableCell>
                    )}
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
  courses,
  open,
  onOpenChange,
}: {
  batchId: number
  courses: BatchCourse[]
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [form, setForm] = useState<Partial<CreateSessionInput>>({})
  const [provider, setProvider] = useState<ClassProvider>('BUILTIN')
  const queryClient = useQueryClient()

  function changeProvider(next: ClassProvider) {
    setProvider(next)
    setForm({
      ...form,
      meetingUrl: next === 'JITSI' ? newJitsiRoomUrl() : undefined,
      mode: next === 'BUILTIN' ? undefined : 'ONLINE',
    })
  }

  const mutation = useMutation({
    mutationFn: () => createSession({ ...form, batchId } as CreateSessionInput),
    onSuccess: () => {
      toast.success('Session scheduled')
      onOpenChange(false)
      setForm({})
      setProvider('BUILTIN')
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
              <Label htmlFor="startTime">Start time *</Label>
              <Input id="startTime" type="time" required onChange={(event) => setForm({ ...form, startTime: event.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="endTime">End time *</Label>
              <Input id="endTime" type="time" required onChange={(event) => setForm({ ...form, endTime: event.target.value })} />
            </div>
          </div>
          {courses.length > 1 && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="sessionCourse">Course</Label>
              <select
                id="sessionCourse"
                className="h-9 rounded-md border bg-background px-3 text-sm"
                value={form.courseId ?? courses[0].courseId}
                onChange={(event) => setForm({ ...form, courseId: Number(event.target.value) })}
              >
                {courses.map((course) => (
                  <option key={course.courseId} value={course.courseId}>
                    {course.courseTitle}
                    {course.trainerName ? ` - ${course.trainerName}` : ''}
                  </option>
                ))}
              </select>
              <p className="text-xs text-muted-foreground">The lecture is taught by that course's trainer.</p>
            </div>
          )}
          <div className="flex flex-col gap-2">
            <Label htmlFor="topic">Topic</Label>
            <Input id="topic" onChange={(event) => setForm({ ...form, topic: event.target.value })} />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="provider">How will this class run?</Label>
            <select
              id="provider"
              className="h-9 rounded-md border bg-background px-3 text-sm"
              value={provider}
              onChange={(event) => changeProvider(event.target.value as ClassProvider)}
            >
              <option value="BUILTIN">Built-in live room (attendance &amp; monitoring)</option>
              <option value="JITSI">Jitsi (opens inside this site)</option>
              <option value="EXTERNAL">Google Meet / Zoom link (opens in a new tab)</option>
            </select>
            {provider === 'EXTERNAL' && (
              <Input
                type="url"
                required
                placeholder="https://meet.google.com/abc-defg-hij"
                onChange={(event) => setForm({ ...form, meetingUrl: event.target.value.trim(), mode: 'ONLINE' })}
              />
            )}
            {provider === 'JITSI' && (
              <p className="text-xs text-muted-foreground">
                A private room link is created for this class. Attendance and monitoring are not tracked for Jitsi.
              </p>
            )}
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

function EnrollStudentDialog({
  batchId,
  courses,
  open,
  onOpenChange,
}: {
  batchId: number
  courses: BatchCourse[]
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  /** Empty: the whole batch. A course id: that course alone, at its own fee. */
  const [scope, setScope] = useState('')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<StudentSummaryResponse | null>(null)
  const queryClient = useQueryClient()

  const resultsQuery = useQuery({
    queryKey: ['students', 'search', query],
    queryFn: () => searchStudents({ query, status: 'ACTIVE' }),
    enabled: open && query.trim().length > 1,
  })

  const mutation = useMutation({
    mutationFn: () => enrollStudents(batchId, [selected!.id], scope === '' ? undefined : Number(scope)),
    onSuccess: (result) => {
      const outcome = result.outcomes[0]
      if (outcome?.success) {
        toast.success(`${selected?.fullName} enrolled`)
        onOpenChange(false)
        setQuery('')
        setSelected(null)
        setScope('')
        void queryClient.invalidateQueries({ queryKey: ['batches', String(batchId), 'roster'] })
        void queryClient.invalidateQueries({ queryKey: ['batches', String(batchId)] })
      } else {
        toast.error(outcome?.message ?? 'Could not enrol the student.')
      }
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not enrol the student.')),
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) {
          setQuery('')
          setSelected(null)
          setScope('')
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <UserPlus className="size-3.5" />
          Enrol student
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Enrol a student</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="studentQuery">Search by name, code or email</Label>
            <Input
              id="studentQuery"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                setSelected(null)
              }}
              placeholder="Asha Patil, STU-2026-0004..."
              autoFocus
            />
          </div>

          {courses.length > 1 && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="enrolScope">Place them in</Label>
              <select
                id="enrolScope"
                className="h-9 rounded-md border bg-background px-3 text-sm"
                value={scope}
                onChange={(event) => setScope(event.target.value)}
              >
                <option value="">The whole batch (batch fee, every course)</option>
                {courses.map((course) => (
                  <option key={course.courseId} value={course.courseId}>
                    {course.courseTitle} only (that course's fee)
                  </option>
                ))}
              </select>
            </div>
          )}

          {resultsQuery.isFetching && <p className="text-sm text-muted-foreground">Searching...</p>}

          {resultsQuery.data && (
            <div className="flex max-h-56 flex-col overflow-y-auto rounded-md border">
              {resultsQuery.data.content.length === 0 && (
                <p className="p-3 text-sm text-muted-foreground">No matching student.</p>
              )}
              {resultsQuery.data.content.map((student) => (
                <button
                  key={student.id}
                  type="button"
                  onClick={() => setSelected(student)}
                  className={`flex flex-col gap-0.5 border-b p-2 text-left text-sm last:border-b-0 hover:bg-accent ${
                    selected?.id === student.id ? 'bg-accent' : ''
                  }`}
                >
                  <span className="font-medium">{student.fullName}</span>
                  <span className="text-xs text-muted-foreground">
                    {student.studentCode} &middot; {student.email}
                  </span>
                </button>
              ))}
            </div>
          )}

          <DialogFooter>
            <Button type="button" disabled={!selected || mutation.isPending} onClick={() => mutation.mutate()}>
              {mutation.isPending ? 'Enrolling...' : selected ? `Enrol ${selected.fullName}` : 'Select a student'}
            </Button>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>
  )
}

/** The courses a batch holds and who teaches each; staff add, re-assign and take them out, and set the batch fee. */
function BatchCoursesSection({ batch, canManage }: { batch: BatchResponse; canManage: boolean }) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['batches', String(batch.id)] })
  const trainersQuery = useQuery({
    queryKey: ['trainers', 'active'],
    queryFn: () => listTrainers({ status: 'ACTIVE' }),
    enabled: canManage,
  })

  const trainerMutation = useMutation({
    mutationFn: (input: { courseId: number; trainerId?: number }) =>
      changeBatchCourseTrainer(batch.id, input.courseId, input.trainerId),
    onSuccess: () => {
      toast.success('Trainer changed')
      void refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change the trainer.')),
  })
  const removeMutation = useMutation({
    mutationFn: (courseId: number) => removeBatchCourse(batch.id, courseId),
    onSuccess: () => {
      toast.success('Course taken out of the batch')
      void refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not take the course out.')),
  })

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-medium text-muted-foreground">Courses in this batch</h2>
        {canManage && (
          <div className="flex flex-wrap items-center gap-2">
            <BatchFeeDialog batch={batch} onSaved={refresh} />
            <AddBatchCourseDialog batch={batch} onAdded={refresh} />
          </div>
        )}
      </div>
      <Card>
        <CardContent className="pt-6">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Course</TableHead>
                <TableHead>Trainer</TableHead>
                {canManage && <TableHead />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {batch.courses.map((course) => (
                <TableRow key={course.courseId}>
                  <TableCell>
                    <span className="font-medium">{course.courseTitle}</span>
                    {course.main && <span className="ml-2 text-xs text-muted-foreground">main course</span>}
                  </TableCell>
                  <TableCell>
                    {canManage && !course.main ? (
                      <select
                        aria-label={`Trainer for ${course.courseTitle}`}
                        className="h-8 rounded-md border bg-background px-2 text-sm"
                        value={course.trainerId ?? ''}
                        disabled={trainerMutation.isPending}
                        onChange={(event) =>
                          trainerMutation.mutate({
                            courseId: course.courseId,
                            trainerId: event.target.value === '' ? undefined : Number(event.target.value),
                          })
                        }
                      >
                        <option value="">No trainer yet</option>
                        {trainersQuery.data?.content.map((trainer) => (
                          <option key={trainer.id} value={trainer.id}>
                            {trainer.fullName}
                          </option>
                        ))}
                      </select>
                    ) : (
                      (course.trainerName ?? 'No trainer assigned')
                    )}
                  </TableCell>
                  {canManage && (
                    <TableCell className="text-right">
                      {!course.main && (
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={removeMutation.isPending}
                          aria-label={`Take ${course.courseTitle} out of the batch`}
                          onClick={() => {
                            if (
                              window.confirm(
                                `Take ${course.courseTitle} out of this batch? The students lose its lessons here; their progress is kept.`,
                              )
                            ) {
                              removeMutation.mutate(course.courseId)
                            }
                          }}
                        >
                          <Trash2 className="size-3.5" />
                        </Button>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <p className="mt-3 text-xs text-muted-foreground">
            Students placed in the whole batch have every course here and pay the batch fee only. A student placed in one
            course alone has just that course and pays its own fee.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

function AddBatchCourseDialog({ batch, onAdded }: { batch: BatchResponse; onAdded: () => unknown }) {
  const [open, setOpen] = useState(false)
  const [courseId, setCourseId] = useState('')
  const [trainerId, setTrainerId] = useState('')

  const coursesQuery = useQuery({
    queryKey: ['courses', 'manage', 'published'],
    queryFn: () => searchCourses({ status: 'PUBLISHED' }),
    enabled: open,
  })
  const trainersQuery = useQuery({
    queryKey: ['trainers', 'active'],
    queryFn: () => listTrainers({ status: 'ACTIVE' }),
    enabled: open,
  })
  const held = new Set(batch.courses.map((c) => c.courseId))

  const mutation = useMutation({
    mutationFn: () => addBatchCourse(batch.id, { courseId: Number(courseId), trainerId: trainerId ? Number(trainerId) : undefined }),
    onSuccess: () => {
      toast.success('Course added - everyone in the whole batch is enrolled in it')
      setOpen(false)
      setCourseId('')
      setTrainerId('')
      void onAdded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the course.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <BookPlus className="size-3.5" />
          Add course
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a course to this batch</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="addCourse">Course</Label>
            <select
              id="addCourse"
              required
              className="h-9 rounded-md border bg-background px-3 text-sm"
              value={courseId}
              onChange={(event) => setCourseId(event.target.value)}
            >
              <option value="" disabled>
                Select a course
              </option>
              {coursesQuery.data?.content
                .filter((course) => !held.has(course.id))
                .map((course) => (
                  <option key={course.id} value={course.id}>
                    {course.title}
                  </option>
                ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="addTrainer">Trainer for this course</Label>
            <select
              id="addTrainer"
              className="h-9 rounded-md border bg-background px-3 text-sm"
              value={trainerId}
              onChange={(event) => setTrainerId(event.target.value)}
            >
              <option value="">May be assigned later</option>
              {trainersQuery.data?.content.map((trainer) => (
                <option key={trainer.id} value={trainer.id}>
                  {trainer.fullName}
                </option>
              ))}
            </select>
          </div>
          <p className="text-xs text-muted-foreground">
            Every student placed in the whole batch is enrolled in this course at once, at no extra fee.
          </p>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending || !courseId}>
              {mutation.isPending ? 'Adding...' : 'Add course'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function BatchFeeDialog({ batch, onSaved }: { batch: BatchResponse; onSaved: () => unknown }) {
  const [open, setOpen] = useState(false)
  const [fee, setFee] = useState(batch.fee != null ? String(batch.fee) : '')

  const mutation = useMutation({
    mutationFn: () => setBatchFee(batch, fee === '' ? undefined : Number(fee)),
    onSuccess: () => {
      toast.success('Batch fee saved')
      setOpen(false)
      void onSaved()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the fee.')),
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) setFee(batch.fee != null ? String(batch.fee) : '')
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Pencil className="size-3.5" />
          Batch fee
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Batch fee</DialogTitle>
        </DialogHeader>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            mutation.mutate()
          }}
        >
          <div className="flex flex-col gap-2">
            <Label htmlFor="batchFee">Fee for the whole batch (INR)</Label>
            <Input
              id="batchFee"
              type="number"
              min={0}
              step={1}
              inputMode="numeric"
              placeholder="e.g. 60000"
              value={fee}
              onChange={(event) => setFee(event.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              A student placed in the whole batch pays this, and nothing more for the courses in it. Leave it empty to
              charge the course fee. It applies to students placed from now on; fee plans already raised are not changed.
            </p>
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving...' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
