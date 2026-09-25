import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type CreateBatchInput, createBatch, searchBatches } from '@/api/batches'
import { searchCourses } from '@/api/courses'
import { listTrainers } from '@/api/trainers'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  UPCOMING: 'secondary',
  RUNNING: 'default',
  COMPLETED: 'outline',
  CANCELLED: 'destructive',
}

export function BatchesPage() {
  const [query, setQuery] = useState('')
  const role = useAuthStore((state) => state.user?.role)
  const canCreate = hasRole(role, ['ADMIN', 'COORDINATOR'])
  const batchesQuery = useQuery({
    queryKey: ['batches', 'search', query],
    queryFn: () => searchBatches({ query: query || undefined }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Batches</h1>
          <p className="text-muted-foreground">Running and upcoming batches, their trainer and seats.</p>
        </div>
        {canCreate && <NewBatchDialog />}
      </div>

      <Input
        placeholder="Search batch, course, code..."
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        className="max-w-xs"
      />

      {batchesQuery.isLoading && <Skeleton className="h-64" />}

      {batchesQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Batch</TableHead>
              <TableHead>Course</TableHead>
              <TableHead>Trainer</TableHead>
              <TableHead>Dates</TableHead>
              <TableHead>Seats</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {batchesQuery.data.content.map((batch) => (
              <TableRow key={batch.id}>
                <TableCell>
                  <Link to={`/app/batches/${batch.id}`} className="font-medium hover:underline">
                    {batch.batchCode}
                  </Link>
                  <p className="text-xs text-muted-foreground">{batch.name}</p>
                </TableCell>
                <TableCell>{batch.courseTitle}</TableCell>
                <TableCell>{batch.trainerName ?? '—'}</TableCell>
                <TableCell>
                  {formatDate(batch.startDate)} &ndash; {formatDate(batch.endDate)}
                </TableCell>
                <TableCell>
                  {batch.enrolledCount}/{batch.capacity}
                </TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[batch.status] ?? 'outline'}>{batch.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {batchesQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No batches match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

function NewBatchDialog() {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<Partial<CreateBatchInput>>({ mode: 'ONLINE', capacity: 30, classDays: [] })
  const navigate = useNavigate()
  const queryClient = useQueryClient()

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

  const mutation = useMutation({
    mutationFn: () => createBatch(form as CreateBatchInput),
    onSuccess: (batch) => {
      toast.success('Batch created')
      setOpen(false)
      setForm({ mode: 'ONLINE', capacity: 30, classDays: [] })
      void queryClient.invalidateQueries({ queryKey: ['batches'] })
      navigate(`/app/batches/${batch.id}`)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the batch.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  function toggleDay(day: string) {
    const days = form.classDays ?? []
    setForm({
      ...form,
      classDays: days.includes(day) ? days.filter((d) => d !== day) : [...days, day],
    })
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New batch
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>New batch</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="courseId">Course</Label>
            <select
              id="courseId"
              value={form.courseId ?? ''}
              onChange={(event) => setForm({ ...form, courseId: Number(event.target.value) })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
              required
            >
              <option value="" disabled>
                Select a course
              </option>
              {coursesQuery.data?.content.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.title}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="trainerId">Trainer</Label>
            <select
              id="trainerId"
              value={form.trainerId ?? ''}
              onChange={(event) => setForm({ ...form, trainerId: Number(event.target.value) || undefined })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              <option value="">May be assigned later</option>
              {trainersQuery.data?.content.map((trainer) => (
                <option key={trainer.id} value={trainer.id}>
                  {trainer.fullName}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="name">Name</Label>
            <Input
              id="name"
              placeholder="Morning batch"
              value={form.name ?? ''}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="startDate">Start date</Label>
              <Input
                id="startDate"
                type="date"
                required
                onChange={(event) => setForm({ ...form, startDate: event.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="endDate">End date</Label>
              <Input id="endDate" type="date" onChange={(event) => setForm({ ...form, endDate: event.target.value })} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="startTime">Start time</Label>
              <Input
                id="startTime"
                type="time"
                required
                onChange={(event) => setForm({ ...form, startTime: event.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="endTime">End time</Label>
              <Input
                id="endTime"
                type="time"
                required
                onChange={(event) => setForm({ ...form, endTime: event.target.value })}
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label>Class days</Label>
            <div className="flex flex-wrap gap-3">
              {WEEKDAYS.map((day) => (
                <label key={day} className="flex items-center gap-1.5 text-sm">
                  <Checkbox checked={(form.classDays ?? []).includes(day)} onCheckedChange={() => toggleDay(day)} />
                  {day}
                </label>
              ))}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="mode">Mode</Label>
              <select
                id="mode"
                value={form.mode}
                onChange={(event) => setForm({ ...form, mode: event.target.value })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                <option value="ONLINE">Online</option>
                <option value="OFFLINE">Offline</option>
                <option value="HYBRID">Hybrid</option>
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="capacity">Capacity</Label>
              <Input
                id="capacity"
                type="number"
                min={1}
                max={500}
                value={form.capacity ?? ''}
                onChange={(event) => setForm({ ...form, capacity: Number(event.target.value) })}
              />
            </div>
          </div>
          {form.mode !== 'ONLINE' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="classroom">Classroom</Label>
              <Input
                id="classroom"
                value={form.classroom ?? ''}
                onChange={(event) => setForm({ ...form, classroom: event.target.value })}
              />
            </div>
          )}
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Creating...' : 'Create batch'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}