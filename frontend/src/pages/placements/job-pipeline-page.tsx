import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Paperclip } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { downloadFile } from '@/api/files'
import {
  type ApplicationResponse,
  applicationHistory,
  cancelJob,
  changeApplicationStage,
  closeJob,
  getJob,
  jobApplications,
  publishJob,
  type StageChangeInput,
} from '@/api/placements'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const STAGE_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  APPLIED: 'outline',
  SHORTLISTED: 'secondary',
  INTERVIEW: 'secondary',
  ON_HOLD: 'outline',
  SELECTED: 'default',
  REJECTED: 'destructive',
  WITHDRAWN: 'destructive',
}

/** Mirrors ApplicationStage.allowedNext() (placement-service) so the dropdown only offers valid moves. */
const ALLOWED_NEXT: Record<string, string[]> = {
  APPLIED: ['SHORTLISTED', 'ON_HOLD', 'REJECTED'],
  SHORTLISTED: ['INTERVIEW', 'ON_HOLD', 'REJECTED'],
  INTERVIEW: ['INTERVIEW', 'SELECTED', 'ON_HOLD', 'REJECTED'],
  ON_HOLD: ['SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED'],
  SELECTED: [],
  REJECTED: [],
  WITHDRAWN: [],
}

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  DRAFT: 'outline',
  OPEN: 'default',
  CLOSED: 'secondary',
  CANCELLED: 'destructive',
}

export function JobPipelinePage() {
  const { jobId } = useParams<{ jobId: string }>()
  const queryClient = useQueryClient()
  const canEdit = hasRole(useAuthStore((state) => state.user?.role), ['ADMIN', 'PLACEMENT'])
  const [historyFor, setHistoryFor] = useState<ApplicationResponse | null>(null)

  const jobQuery = useQuery({
    queryKey: ['placements', 'jobs', jobId],
    queryFn: () => getJob(jobId!),
    enabled: !!jobId,
  })
  const applicationsQuery = useQuery({
    queryKey: ['placements', 'jobs', jobId, 'applications'],
    queryFn: () => jobApplications(jobId!),
    enabled: !!jobId,
  })

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: ['placements', 'jobs', jobId] })
    void queryClient.invalidateQueries({ queryKey: ['placements', 'jobs', jobId, 'applications'] })
    void queryClient.invalidateQueries({ queryKey: ['placements', 'jobs', 'desk'] })
  }

  const publishMutation = useMutation({
    mutationFn: () => publishJob(jobId!),
    onSuccess: () => {
      toast.success('Job published')
      invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not publish the job.')),
  })
  const closeMutation = useMutation({
    mutationFn: () => closeJob(jobId!),
    onSuccess: () => {
      toast.success('Applications closed')
      invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not close the job.')),
  })
  const cancelMutation = useMutation({
    mutationFn: () => cancelJob(jobId!),
    onSuccess: () => {
      toast.success('Job cancelled')
      invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not cancel the job.')),
  })
  const stageMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: StageChangeInput }) => changeApplicationStage(id, input),
    onSuccess: () => {
      toast.success('Stage updated')
      invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not move the application.')),
  })

  if (jobQuery.isLoading) return <Skeleton className="h-96 max-w-4xl" />
  const job = jobQuery.data
  if (!job) return null

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <Link to="/app/placements" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Placements
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{job.title}</h1>
          <p className="text-muted-foreground">
            {job.companyName}
            {job.location && ` · ${job.location}`}
            {job.packageOffered && ` · ${job.packageOffered}`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={STATUS_VARIANT[job.status] ?? 'outline'}>{job.status}</Badge>
          {canEdit && job.status === 'DRAFT' && (
            <Button size="sm" variant="outline" disabled={publishMutation.isPending} onClick={() => publishMutation.mutate()}>
              Publish
            </Button>
          )}
          {canEdit && job.status === 'OPEN' && (
            <Button size="sm" variant="outline" disabled={closeMutation.isPending} onClick={() => closeMutation.mutate()}>
              Close
            </Button>
          )}
          {canEdit && (job.status === 'DRAFT' || job.status === 'OPEN') && (
            <Button size="sm" variant="ghost" disabled={cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>
              Cancel
            </Button>
          )}
        </div>
      </div>

      {job.description && <p className="whitespace-pre-wrap text-sm">{job.description}</p>}

      <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
        <span>{job.jobType.replace('_', ' ')}</span>
        {job.openings && <span>{job.openings} opening(s)</span>}
        {job.applicationDeadline && <span>Apply by {formatDate(job.applicationDeadline)}</span>}
        {job.requireCertificate && <span>Requires a certificate</span>}
        {job.minAttendancePercent != null && <span>Min. attendance {job.minAttendancePercent}%</span>}
        {job.minScorePercent != null && <span>Min. score {job.minScorePercent}%</span>}
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Applications</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Applied</TableHead>
              <TableHead>Stage</TableHead>
              <TableHead>Round</TableHead>
              <TableHead>Next interview</TableHead>
              {canEdit && <TableHead>Resume</TableHead>}
              {canEdit && <TableHead />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {applicationsQuery.data?.map((application) => (
              <TableRow key={application.id}>
                <TableCell className="font-medium">{application.studentName}</TableCell>
                <TableCell>{formatDate(application.appliedAt)}</TableCell>
                <TableCell>
                  <button className="underline-offset-2 hover:underline" onClick={() => setHistoryFor(application)}>
                    <Badge variant={STAGE_VARIANT[application.stage] ?? 'outline'}>{application.stage}</Badge>
                  </button>
                </TableCell>
                <TableCell>{application.currentRound ?? '—'}</TableCell>
                <TableCell>{application.nextInterviewAt ? formatDate(application.nextInterviewAt) : '—'}</TableCell>
                {canEdit && (
                  <TableCell>
                    {application.resumeRef && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => void downloadFile(application.resumeRef!, `${application.studentName}-resume`)}
                      >
                        <Paperclip className="size-3.5" />
                      </Button>
                    )}
                  </TableCell>
                )}
                {canEdit && (
                  <TableCell>
                    {ALLOWED_NEXT[application.stage]?.length > 0 && (
                      <StageChangeDialog
                        application={application}
                        pending={stageMutation.isPending}
                        onSubmit={(input) => stageMutation.mutate({ id: application.id, input })}
                      />
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
            {applicationsQuery.isSuccess && applicationsQuery.data.length === 0 && (
              <TableRow>
                <TableCell colSpan={canEdit ? 7 : 5} className="text-center text-muted-foreground">
                  No applications yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Dialog open={historyFor != null} onOpenChange={(open) => !open && setHistoryFor(null)}>
        <DialogContent>
          {historyFor && <HistoryView application={historyFor} />}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function StageChangeDialog({
  application,
  pending,
  onSubmit,
}: {
  application: ApplicationResponse
  pending: boolean
  onSubmit: (input: StageChangeInput) => void
}) {
  const [open, setOpen] = useState(false)
  const options = ALLOWED_NEXT[application.stage] ?? []
  const [stage, setStage] = useState(options[0] ?? '')
  const [roundNo, setRoundNo] = useState('')
  const [nextInterviewAt, setNextInterviewAt] = useState('')
  const [offerDetails, setOfferDetails] = useState('')
  const [note, setNote] = useState('')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onSubmit({
      stage,
      roundNo: stage === 'INTERVIEW' && roundNo ? Number(roundNo) : undefined,
      nextInterviewAt: stage === 'INTERVIEW' && nextInterviewAt ? new Date(nextInterviewAt).toISOString() : undefined,
      offerDetails: stage === 'SELECTED' ? offerDetails || undefined : undefined,
      note: note || undefined,
    })
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          Move
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Move {application.studentName}&apos;s application</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="stage">New stage</Label>
            <select
              id="stage"
              value={stage}
              onChange={(e) => setStage(e.target.value)}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              {options.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          {stage === 'INTERVIEW' && (
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="roundNo">Round</Label>
                <Input id="roundNo" type="number" min={1} value={roundNo} onChange={(e) => setRoundNo(e.target.value)} />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="nextInterviewAt">When</Label>
                <Input
                  id="nextInterviewAt"
                  type="datetime-local"
                  value={nextInterviewAt}
                  onChange={(e) => setNextInterviewAt(e.target.value)}
                />
              </div>
            </div>
          )}
          {stage === 'SELECTED' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="offerDetails">Offer</Label>
              <Input
                id="offerDetails"
                placeholder="5 LPA, joining 1 Jan"
                value={offerDetails}
                onChange={(e) => setOfferDetails(e.target.value)}
              />
            </div>
          )}
          <div className="flex flex-col gap-2">
            <Label htmlFor="note">Note</Label>
            <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={pending}>
              {pending ? 'Saving...' : 'Move'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function HistoryView({ application }: { application: ApplicationResponse }) {
  const query = useQuery({
    queryKey: ['placements', 'applications', application.id, 'history'],
    queryFn: () => applicationHistory(application.id),
  })

  return (
    <>
      <DialogHeader>
        <DialogTitle>{application.studentName}&apos;s history</DialogTitle>
      </DialogHeader>
      <div className="flex flex-col gap-2 text-sm">
        {query.data?.map((entry, index) => (
          <div key={index} className="flex items-center justify-between border-b pb-1 last:border-0">
            <span>{entry.fromStage ? `${entry.fromStage} → ${entry.toStage}` : entry.toStage}</span>
            <span className="text-muted-foreground">{formatDate(entry.changedAt)}</span>
          </div>
        ))}
        {query.isSuccess && query.data.length === 0 && <p className="text-muted-foreground">No moves yet.</p>}
      </div>
    </>
  )
}
