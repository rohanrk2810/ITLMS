import { type ChangeEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ChevronLeft, Download, Paperclip, Upload } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import {
  type AssignmentResponse,
  type EvaluateSubmissionInput,
  type SubmitAssignmentInput,
  evaluateSubmission,
  getAssignment,
  getSubmissions,
  submitAssignment,
} from '@/api/assignments'
import { apiErrorMessage } from '@/api/client'
import { downloadFile, uploadFile } from '@/api/files'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  SUBMITTED: 'secondary',
  LATE: 'destructive',
  EVALUATED: 'default',
  RETURNED: 'outline',
}

export function AssignmentDetailPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>()
  const role = useAuthStore((state) => state.user?.role)
  const isAcademic = hasRole(role, ['ADMIN', 'COORDINATOR', 'TRAINER'])

  const query = useQuery({
    queryKey: ['assignments', assignmentId],
    queryFn: () => getAssignment(assignmentId!),
    enabled: !!assignmentId,
  })

  if (query.isLoading) return <Skeleton className="h-96 max-w-2xl" />
  const assignment = query.data
  if (!assignment) return null

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <Link to="/app/assessments" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Assessments
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{assignment.title}</h1>
          <p className="text-muted-foreground">
            Due {formatDate(assignment.dueAt)} &middot; {assignment.maxMarks} marks
            {assignment.overdue && ' · Overdue'}
          </p>
        </div>
        <Badge>{assignment.status}</Badge>
      </div>

      {assignment.instructions && (
        <Card>
          <CardContent className="pt-6 text-sm whitespace-pre-wrap">{assignment.instructions}</CardContent>
        </Card>
      )}

      {isAcademic ? (
        <SubmissionsList assignmentId={assignment.id} />
      ) : (
        <StudentSubmission assignmentId={assignment.id} assignment={assignment} />
      )}
    </div>
  )
}

function StudentSubmission({
  assignmentId,
  assignment,
}: {
  assignmentId: number
  assignment: AssignmentResponse
}) {
  const queryClient = useQueryClient()
  const [textAnswer, setTextAnswer] = useState(assignment.mySubmission?.textAnswer ?? '')
  const [files, setFiles] = useState<SubmitAssignmentInput['files']>([])
  const [uploading, setUploading] = useState(false)

  const canSubmit = assignment.status === 'PUBLISHED'

  const mutation = useMutation({
    mutationFn: () => submitAssignment(assignmentId, { textAnswer: textAnswer || undefined, files }),
    onSuccess: () => {
      toast.success('Submitted')
      void queryClient.invalidateQueries({ queryKey: ['assignments', String(assignmentId)] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not submit.')),
  })

  async function handleFilePick(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files
    if (!picked || picked.length === 0) return
    setUploading(true)
    try {
      for (const file of Array.from(picked)) {
        const uploaded = await uploadFile(file, 'SUBMISSION')
        setFiles((prev) => [
          ...(prev ?? []),
          {
            fileRef: String(uploaded.id),
            fileName: uploaded.filename,
            contentType: uploaded.contentType,
            sizeBytes: uploaded.sizeBytes,
          },
        ])
      }
      toast.success('File(s) attached')
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not upload the file.'))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {assignment.mySubmission && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Your submission</CardTitle>
              <Badge variant={STATUS_VARIANT[assignment.mySubmission.status] ?? 'outline'}>
                {assignment.mySubmission.status}
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            <p className="text-muted-foreground">Submitted {formatDate(assignment.mySubmission.submittedAt)}</p>
            {assignment.mySubmission.textAnswer && <p className="whitespace-pre-wrap">{assignment.mySubmission.textAnswer}</p>}
            {assignment.mySubmission.files.map((file) => (
              <Button
                key={file.fileRef}
                size="sm"
                variant="outline"
                className="w-fit"
                onClick={() => void downloadFile(file.fileRef, file.fileName ?? file.fileRef)}
              >
                <Paperclip className="size-3.5" />
                {file.fileName ?? file.fileRef}
              </Button>
            ))}
            {assignment.mySubmission.marks != null && (
              <p className="font-medium">
                Marks: {assignment.mySubmission.marks}/{assignment.maxMarks}
              </p>
            )}
            {assignment.mySubmission.feedback && (
              <p className="rounded-md border p-2 text-muted-foreground">{assignment.mySubmission.feedback}</p>
            )}
          </CardContent>
        </Card>
      )}

      {canSubmit && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {assignment.mySubmission ? 'Submit again' : 'Submit your work'}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <textarea
              rows={5}
              placeholder="Write your answer..."
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={textAnswer}
              onChange={(event) => setTextAnswer(event.target.value)}
            />
            <div>
              <Input type="file" multiple onChange={(event) => void handleFilePick(event)} />
              {uploading && (
                <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                  <Upload className="size-3 animate-pulse" />
                  Uploading...
                </p>
              )}
              {files && files.length > 0 && (
                <p className="mt-1 text-xs text-emerald-600">{files.length} file(s) attached.</p>
              )}
            </div>
            <Button
              className="self-start"
              onClick={() => mutation.mutate()}
              disabled={mutation.isPending || uploading || (!textAnswer && (!files || files.length === 0))}
            >
              {mutation.isPending ? 'Submitting...' : 'Submit'}
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function SubmissionsList({ assignmentId }: { assignmentId: number }) {
  const query = useQuery({
    queryKey: ['assignments', assignmentId, 'submissions'],
    queryFn: () => getSubmissions(assignmentId),
  })

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-muted-foreground">Submissions</h2>
      {query.data?.map((submission) => (
        <Card key={submission.id}>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">{submission.studentName}</CardTitle>
              <Badge variant={STATUS_VARIANT[submission.status] ?? 'outline'}>{submission.status}</Badge>
            </div>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            <p className="text-muted-foreground">Submitted {formatDate(submission.submittedAt)}</p>
            {submission.textAnswer && <p className="whitespace-pre-wrap">{submission.textAnswer}</p>}
            <div className="flex flex-wrap gap-2">
              {submission.files.map((file) => (
                <Button
                  key={file.fileRef}
                  size="sm"
                  variant="outline"
                  onClick={() => void downloadFile(file.fileRef, file.fileName ?? file.fileRef)}
                >
                  <Download className="size-3.5" />
                  {file.fileName ?? file.fileRef}
                </Button>
              ))}
            </div>
            {submission.status === 'EVALUATED' ? (
              <p className="flex items-center gap-1 font-medium text-emerald-600">
                <CheckCircle2 className="size-4" />
                {submission.marks} marks{submission.feedback && ` - ${submission.feedback}`}
              </p>
            ) : (
              <EvaluateDialog submissionId={submission.id} assignmentId={assignmentId} />
            )}
          </CardContent>
        </Card>
      ))}
      {query.isSuccess && query.data.length === 0 && (
        <p className="text-sm text-muted-foreground">No submissions yet.</p>
      )}
    </div>
  )
}

function EvaluateDialog({ submissionId, assignmentId }: { submissionId: number; assignmentId: number }) {
  const [open, setOpen] = useState(false)
  const [marks, setMarks] = useState('')
  const [feedback, setFeedback] = useState('')
  const [returnForRework, setReturnForRework] = useState(false)
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => {
      const input: EvaluateSubmissionInput = returnForRework
        ? { feedback, returnForRework: true }
        : { marks: Number(marks), feedback: feedback || undefined }
      return evaluateSubmission(submissionId, input)
    },
    onSuccess: () => {
      toast.success(returnForRework ? 'Returned for rework' : 'Marked')
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['assignments', assignmentId, 'submissions'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save.')),
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" className="self-start">
          Evaluate
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Evaluate submission</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={returnForRework} onCheckedChange={(v) => setReturnForRework(v === true)} />
            Return for rework instead of marking
          </label>
          {!returnForRework && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="marks">Marks</Label>
              <Input id="marks" type="number" min={0} value={marks} onChange={(event) => setMarks(event.target.value)} />
            </div>
          )}
          <div className="flex flex-col gap-2">
            <Label htmlFor="feedback">Feedback</Label>
            <textarea
              id="feedback"
              rows={3}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={feedback}
              onChange={(event) => setFeedback(event.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || (!returnForRework && marks === '')}
          >
            {mutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
