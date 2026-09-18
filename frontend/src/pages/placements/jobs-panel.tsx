import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { searchCourses } from '@/api/courses'
import {
  cancelJob,
  closeJob,
  createJob,
  type JobInput,
  listCompanies,
  listJobs,
  publishJob,
} from '@/api/placements'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'

const STATUSES = ['DRAFT', 'OPEN', 'CLOSED', 'CANCELLED']
const JOB_TYPES = ['FULL_TIME', 'INTERNSHIP', 'CONTRACT', 'PART_TIME']

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  DRAFT: 'outline',
  OPEN: 'default',
  CLOSED: 'secondary',
  CANCELLED: 'destructive',
}

export function JobsPanel({ canEdit }: { canEdit: boolean }) {
  const [status, setStatus] = useState('')
  const queryClient = useQueryClient()

  const jobsQuery = useQuery({
    queryKey: ['placements', 'jobs', 'desk', status],
    queryFn: () => listJobs({ status: status || undefined }),
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: ['placements', 'jobs'] })
  }

  const publishMutation = useMutation({
    mutationFn: (id: number) => publishJob(id),
    onSuccess: () => {
      toast.success('Job published')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not publish the job.')),
  })
  const closeMutation = useMutation({
    mutationFn: (id: number) => closeJob(id),
    onSuccess: () => {
      toast.success('Applications closed')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not close the job.')),
  })
  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelJob(id),
    onSuccess: () => {
      toast.success('Job cancelled')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not cancel the job.')),
  })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          className="h-9 rounded-md border bg-transparent px-3 text-sm"
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        {canEdit && <NewJobDialog onCreated={invalidate} />}
      </div>

      {jobsQuery.isLoading && <Skeleton className="h-48" />}

      {jobsQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Company</TableHead>
              <TableHead>Applications</TableHead>
              <TableHead>Deadline</TableHead>
              <TableHead>Status</TableHead>
              {canEdit && <TableHead />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {jobsQuery.data.content.map((job) => (
              <TableRow key={job.id}>
                <TableCell>
                  <Link to={`/app/placements/${job.id}`} className="font-medium hover:underline">
                    {job.title}
                  </Link>
                </TableCell>
                <TableCell>{job.companyName}</TableCell>
                <TableCell>{job.applicationCount ?? 0}</TableCell>
                <TableCell>{job.applicationDeadline ? formatDate(job.applicationDeadline) : '—'}</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[job.status] ?? 'outline'}>{job.status}</Badge>
                </TableCell>
                {canEdit && (
                  <TableCell className="flex gap-1">
                    {job.status === 'DRAFT' && (
                      <Button size="sm" variant="outline" disabled={publishMutation.isPending} onClick={() => publishMutation.mutate(job.id)}>
                        Publish
                      </Button>
                    )}
                    {job.status === 'OPEN' && (
                      <Button size="sm" variant="outline" disabled={closeMutation.isPending} onClick={() => closeMutation.mutate(job.id)}>
                        Close
                      </Button>
                    )}
                    {(job.status === 'DRAFT' || job.status === 'OPEN') && (
                      <Button size="sm" variant="ghost" disabled={cancelMutation.isPending} onClick={() => cancelMutation.mutate(job.id)}>
                        Cancel
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
            {jobsQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={canEdit ? 6 : 5} className="text-center text-muted-foreground">
                  No jobs match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

const EMPTY_JOB: JobInput = { companyId: 0, title: '', jobType: 'FULL_TIME', eligibleCourseIds: [] }

function NewJobDialog({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<JobInput>(EMPTY_JOB)

  const companiesQuery = useQuery({
    queryKey: ['placements', 'companies', true],
    queryFn: () => listCompanies(true),
    enabled: open,
  })
  const coursesQuery = useQuery({
    queryKey: ['courses', 'search', 'PUBLISHED'],
    queryFn: () => searchCourses({ status: 'PUBLISHED' }),
    enabled: open,
  })

  const mutation = useMutation({
    mutationFn: () => createJob(form),
    onSuccess: () => {
      toast.success('Job created as a draft')
      setOpen(false)
      setForm(EMPTY_JOB)
      onCreated()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the job.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  function toggleCourse(id: number) {
    const current = new Set(form.eligibleCourseIds ?? [])
    if (current.has(id)) current.delete(id)
    else current.add(id)
    setForm({ ...form, eligibleCourseIds: Array.from(current) })
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="size-3.5" />
          New job
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>New job opening</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="companyId">Company</Label>
            <select
              id="companyId"
              value={form.companyId || ''}
              onChange={(e) => setForm({ ...form, companyId: Number(e.target.value) })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
              required
            >
              <option value="" disabled>
                Select a company
              </option>
              {companiesQuery.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="title">Title</Label>
            <Input id="title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="description">Description</Label>
            <textarea
              id="description"
              rows={3}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={form.description ?? ''}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="jobType">Type</Label>
              <select
                id="jobType"
                value={form.jobType}
                onChange={(e) => setForm({ ...form, jobType: e.target.value as JobInput['jobType'] })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                {JOB_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace('_', ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="location">Location</Label>
              <Input id="location" value={form.location ?? ''} onChange={(e) => setForm({ ...form, location: e.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="openings">Openings</Label>
              <Input
                id="openings"
                type="number"
                min={1}
                value={form.openings ?? ''}
                onChange={(e) => setForm({ ...form, openings: Number(e.target.value) })}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="packageOffered">Package</Label>
              <Input
                id="packageOffered"
                placeholder="4.5 LPA"
                value={form.packageOffered ?? ''}
                onChange={(e) => setForm({ ...form, packageOffered: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="applicationDeadline">Apply by</Label>
              <Input
                id="applicationDeadline"
                type="date"
                value={form.applicationDeadline ?? ''}
                onChange={(e) => setForm({ ...form, applicationDeadline: e.target.value })}
              />
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <Label>Eligible courses (none selected means any course)</Label>
            <div className="flex max-h-32 flex-col gap-1 overflow-y-auto rounded-md border p-2">
              {coursesQuery.data?.content.map((course) => (
                <label key={course.id} className="flex items-center gap-2 text-sm">
                  <Checkbox
                    checked={(form.eligibleCourseIds ?? []).includes(course.id)}
                    onCheckedChange={() => toggleCourse(course.id)}
                  />
                  {course.title}
                </label>
              ))}
              {coursesQuery.data?.content.length === 0 && (
                <p className="text-xs text-muted-foreground">No published courses yet.</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="minAttendancePercent">Min. attendance %</Label>
              <Input
                id="minAttendancePercent"
                type="number"
                min={0}
                max={100}
                value={form.minAttendancePercent ?? ''}
                onChange={(e) => setForm({ ...form, minAttendancePercent: Number(e.target.value) })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="minScorePercent">Min. score %</Label>
              <Input
                id="minScorePercent"
                type="number"
                min={0}
                max={100}
                value={form.minScorePercent ?? ''}
                onChange={(e) => setForm({ ...form, minScorePercent: Number(e.target.value) })}
              />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={form.requireCertificate ?? false}
              onCheckedChange={(v) => setForm({ ...form, requireCertificate: v === true })}
            />
            Requires a course certificate
          </label>
          <div className="flex flex-col gap-2">
            <Label htmlFor="eligibilityNotes">Eligibility notes</Label>
            <textarea
              id="eligibilityNotes"
              rows={2}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={form.eligibilityNotes ?? ''}
              onChange={(e) => setForm({ ...form, eligibilityNotes: e.target.value })}
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Creating...' : 'Create draft'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
