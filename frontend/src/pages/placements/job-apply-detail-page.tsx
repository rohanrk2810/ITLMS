import { type ChangeEvent, type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Paperclip, Upload, XCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { downloadFile, uploadFile } from '@/api/files'
import {
  applicationHistory,
  applyToJob,
  getJob,
  myApplications,
  withdrawApplication,
} from '@/api/placements'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

const STAGE_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  APPLIED: 'outline',
  SHORTLISTED: 'secondary',
  INTERVIEW: 'secondary',
  ON_HOLD: 'outline',
  SELECTED: 'default',
  REJECTED: 'destructive',
  WITHDRAWN: 'destructive',
}

export function JobApplyDetailPage() {
  const { jobId } = useParams<{ jobId: string }>()
  const queryClient = useQueryClient()

  const jobQuery = useQuery({
    queryKey: ['placements', 'jobs', jobId],
    queryFn: () => getJob(jobId!),
    enabled: !!jobId,
  })

  const applicationsQuery = useQuery({ queryKey: ['placements', 'applications', 'mine'], queryFn: myApplications })
  const myApplication = applicationsQuery.data?.find((a) => a.jobId === Number(jobId))

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: ['placements', 'jobs', jobId] })
    void queryClient.invalidateQueries({ queryKey: ['placements', 'applications', 'mine'] })
  }

  const withdrawMutation = useMutation({
    mutationFn: (id: number) => withdrawApplication(id),
    onSuccess: () => {
      toast.success('Application withdrawn')
      invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not withdraw the application.')),
  })

  if (jobQuery.isLoading) return <Skeleton className="h-96 max-w-2xl" />
  const job = jobQuery.data
  if (!job) return null

  return (
    <div className="flex max-w-2xl flex-col gap-6">
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
          </p>
        </div>
        {myApplication && <Badge variant={STAGE_VARIANT[myApplication.stage] ?? 'outline'}>{myApplication.stage}</Badge>}
      </div>

      <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
        <span>{job.jobType.replace('_', ' ')}</span>
        {job.packageOffered && <span>{job.packageOffered}</span>}
        {job.openings && <span>{job.openings} opening(s)</span>}
        {job.applicationDeadline && <span>Apply by {formatDate(job.applicationDeadline)}</span>}
      </div>

      {job.description && <p className="whitespace-pre-wrap text-sm">{job.description}</p>}

      {job.eligibilityNotes && (
        <p className="rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground">{job.eligibilityNotes}</p>
      )}

      {job.eligible === false && job.ineligibleReasons && job.ineligibleReasons.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Why you can&apos;t apply</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-1 text-sm">
            {job.ineligibleReasons.map((reason) => (
              <div key={reason} className="flex items-center gap-2 text-destructive">
                <XCircle className="size-4 shrink-0" />
                {reason}
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {myApplication ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Your application</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            <p className="text-muted-foreground">Applied {formatDate(myApplication.appliedAt)}</p>
            {myApplication.coverNote && <p className="whitespace-pre-wrap">{myApplication.coverNote}</p>}
            {myApplication.resumeRef && (
              <Button
                size="sm"
                variant="outline"
                className="w-fit"
                onClick={() => void downloadFile(myApplication.resumeRef!, 'resume')}
              >
                <Paperclip className="size-3.5" />
                Resume
              </Button>
            )}
            {myApplication.nextInterviewAt && (
              <p>
                Next interview: <span className="font-medium">{formatDate(myApplication.nextInterviewAt)}</span>
              </p>
            )}
            {myApplication.offerDetails && (
              <p className="font-medium text-emerald-600">Offer: {myApplication.offerDetails}</p>
            )}
            {!['SELECTED', 'REJECTED', 'WITHDRAWN'].includes(myApplication.stage) && (
              <Button
                size="sm"
                variant="destructive"
                className="w-fit"
                disabled={withdrawMutation.isPending}
                onClick={() => withdrawMutation.mutate(myApplication.id)}
              >
                Withdraw application
              </Button>
            )}
            <HistoryList applicationId={myApplication.id} />
          </CardContent>
        </Card>
      ) : job.eligible ? (
        <ApplyForm jobId={job.id} onApplied={invalidate} />
      ) : null}
    </div>
  )
}

function ApplyForm({ jobId, onApplied }: { jobId: number; onApplied: () => void }) {
  const [coverNote, setCoverNote] = useState('')
  const [resumeRef, setResumeRef] = useState<string | null>(null)
  const [resumeName, setResumeName] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const mutation = useMutation({
    mutationFn: () => applyToJob(jobId, { coverNote: coverNote || undefined, resumeRef: resumeRef ?? undefined }),
    onSuccess: () => {
      toast.success('Applied')
      onApplied()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not apply.')),
  })

  async function handleResumePick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const uploaded = await uploadFile(file, 'DOCUMENT')
      setResumeRef(String(uploaded.id))
      setResumeName(uploaded.filename)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not upload the resume.'))
    } finally {
      setUploading(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Apply</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-3" onSubmit={handleSubmit}>
          <textarea
            rows={4}
            placeholder="A short cover note (optional)"
            className="rounded-md border bg-transparent px-3 py-2 text-sm"
            value={coverNote}
            onChange={(event) => setCoverNote(event.target.value)}
          />
          <div>
            <input
              type="file"
              onChange={(event) => void handleResumePick(event)}
              className="text-sm file:mr-3 file:rounded-md file:border file:bg-transparent file:px-3 file:py-1.5 file:text-sm"
            />
            {uploading && (
              <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                <Upload className="size-3 animate-pulse" />
                Uploading...
              </p>
            )}
            {resumeName && <p className="mt-1 text-xs text-emerald-600">Attached: {resumeName}</p>}
          </div>
          <Button type="submit" className="self-start" disabled={mutation.isPending || uploading}>
            {mutation.isPending ? 'Applying...' : 'Apply'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function HistoryList({ applicationId }: { applicationId: number }) {
  const query = useQuery({
    queryKey: ['placements', 'applications', applicationId, 'history'],
    queryFn: () => applicationHistory(applicationId),
  })
  if (!query.data || query.data.length === 0) return null
  return (
    <div className="flex flex-col gap-1 border-t pt-3">
      <p className="text-xs font-medium text-muted-foreground">History</p>
      {query.data.map((entry, index) => (
        <p key={index} className="text-xs text-muted-foreground">
          {entry.fromStage ? `${entry.fromStage} → ${entry.toStage}` : entry.toStage} · {formatDate(entry.changedAt)}
          {entry.note && ` — ${entry.note}`}
        </p>
      ))}
    </div>
  )
}
