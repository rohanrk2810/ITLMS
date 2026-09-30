import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Award, CheckCircle2, Download, ExternalLink, XCircle } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  certificateEligibility,
  certificatePdfObjectUrl,
  type CertificateRequestResponse,
  type EligibilityCriterion,
  myCertificateRequests,
  myCertificates,
  requestCertificate,
} from '@/api/certificates'
import { lookupCourses, myCourseProgress } from '@/api/courses'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { useAuthStore } from '@/stores/auth-store'

export function CertificatesPage() {
  const studentId = useAuthStore((state) => state.user?.profileId)
  const queryClient = useQueryClient()

  const certificatesQuery = useQuery({ queryKey: ['certificates', 'mine'], queryFn: myCertificates })
  const requestsQuery = useQuery({ queryKey: ['certificates', 'requests', 'mine'], queryFn: myCertificateRequests })
  const progressQuery = useQuery({ queryKey: ['courses', 'my-progress'], queryFn: myCourseProgress })

  const certifiedCourseIds = new Set(certificatesQuery.data?.map((c) => c.courseId))
  const uncertifiedProgress = progressQuery.data?.filter((p) => !certifiedCourseIds.has(p.courseId)) ?? []
  const requestByCourse = new Map<number, CertificateRequestResponse>()
  // Newest first from the API, so the first one seen per course is the current one.
  requestsQuery.data?.forEach((r) => {
    if (!requestByCourse.has(r.courseId)) requestByCourse.set(r.courseId, r)
  })
  const uncertifiedCourseIds = uncertifiedProgress.map((p) => p.courseId)

  const coursesQuery = useQuery({
    queryKey: ['courses', 'lookup', uncertifiedCourseIds],
    queryFn: () => lookupCourses(uncertifiedCourseIds),
    enabled: progressQuery.isSuccess && certificatesQuery.isSuccess && uncertifiedCourseIds.length > 0,
  })

  async function handleDownload(id: number, certificateNo: string) {
    try {
      const url = await certificatePdfObjectUrl(id)
      const link = document.createElement('a')
      link.href = url
      link.download = `${certificateNo}.pdf`
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 30_000)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not download the certificate.'))
    }
  }

  async function handleView(id: number) {
    try {
      const url = await certificatePdfObjectUrl(id)
      window.open(url, '_blank', 'noopener')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not open the certificate.'))
    }
  }

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">My Certificates</h1>
        <p className="text-muted-foreground">What you&apos;ve earned, and what&apos;s left to earn one.</p>
      </div>

      {requestsQuery.data && requestsQuery.data.length > 0 && (
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-medium text-muted-foreground">My requests</h2>
          {requestsQuery.data.map((request) => (
            <RequestCard key={request.id} request={request} />
          ))}
        </div>
      )}

      <div className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-muted-foreground">Earned</h2>
        {certificatesQuery.isLoading && <Skeleton className="h-24" />}
        {certificatesQuery.isSuccess && certificatesQuery.data.length === 0 && (
          <p className="text-sm text-muted-foreground">None yet.</p>
        )}
        {certificatesQuery.data?.map((certificate) => (
          <Card key={certificate.id}>
            <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
              <div className="flex items-center gap-3">
                <Award className="size-8 text-amber-500" />
                <div>
                  <p className="font-medium">{certificate.courseTitle}</p>
                  <p className="text-sm text-muted-foreground">
                    {certificate.certificateNo} &middot; issued {formatDate(certificate.issueDate)}
                  </p>
                </div>
                {certificate.status === 'REVOKED' && <Badge variant="destructive">Revoked</Badge>}
              </div>
              {certificate.status === 'ISSUED' && (
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => void handleView(certificate.id)}>
                    <ExternalLink className="size-3.5" />
                    View
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => void handleDownload(certificate.id, certificate.certificateNo)}
                  >
                    <Download className="size-3.5" />
                    Download
                  </Button>
                  <Button size="sm" variant="ghost" asChild>
                    <a href={verifyPath(certificate.verificationUrl)} target="_blank" rel="noreferrer">
                      Verify
                    </a>
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {studentId != null && uncertifiedProgress.length > 0 && (
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-medium text-muted-foreground">In progress</h2>
          {uncertifiedProgress.map((progress) => {
            const course = coursesQuery.data?.find((c) => c.id === progress.courseId)
            return (
              <EligibilityCard
                key={progress.courseId}
                studentId={studentId}
                courseId={progress.courseId}
                courseTitle={course?.title ?? `Course #${progress.courseId}`}
                openRequest={requestByCourse.get(progress.courseId)}
                onRequested={() => void queryClient.invalidateQueries({ queryKey: ['certificates'] })}
              />
            )
          })}
        </div>
      )}
    </div>
  )
}

function EligibilityCard({
  studentId,
  courseId,
  courseTitle,
  openRequest,
  onRequested,
}: {
  studentId: number
  courseId: number
  courseTitle: string
  openRequest: CertificateRequestResponse | undefined
  onRequested: () => void
}) {
  const query = useQuery({
    queryKey: ['certificates', 'eligibility', studentId, courseId],
    queryFn: () => certificateEligibility(studentId, courseId),
  })

  const request = useMutation({
    mutationFn: () => requestCertificate(courseId),
    onSuccess: () => {
      toast.success('Certificate request submitted successfully.')
      onRequested()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not submit the request.')),
  })

  // Pending or approved means waiting on the institute; a rejected one may be asked for again.
  const waiting = openRequest?.status === 'PENDING' || openRequest?.status === 'APPROVED'

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{courseTitle}</CardTitle>
        <CardDescription>Doc S7.3 completion conditions</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {query.isLoading && <Skeleton className="h-16" />}
        {query.data && (
          <>
            <ul className="flex flex-col gap-1 text-sm">
              {query.data.criteria.map((criterion) => (
                <CriterionRow key={criterion.key} criterion={criterion} />
              ))}
            </ul>
            {waiting ? (
              <p className="text-sm text-muted-foreground">
                Request {openRequest?.status === 'APPROVED' ? 'approved, certificate being issued' : 'pending approval'}.
              </p>
            ) : query.data.eligible ? (
              <Button size="sm" className="self-start" onClick={() => request.mutate()} disabled={request.isPending}>
                {request.isPending ? 'Submitting...' : 'Request certificate'}
              </Button>
            ) : query.data.outstandingWork.length > 0 ? (
              <p className="text-xs text-muted-foreground">Still outstanding: {query.data.outstandingWork.join(', ')}</p>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  )
}

const STATUS_LABEL: Record<CertificateRequestResponse['status'], string> = {
  PENDING: 'Pending Approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  ISSUED: 'Issued',
}

function RequestCard({ request }: { request: CertificateRequestResponse }) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-1 pt-6 text-sm">
        <div className="flex items-center justify-between gap-2">
          <p className="font-medium">Course: {request.courseTitle}</p>
          <Badge variant={request.status === 'REJECTED' ? 'destructive' : 'secondary'}>
            {STATUS_LABEL[request.status]}
          </Badge>
        </div>
        {request.batchName && <p className="text-muted-foreground">Batch: {request.batchName}</p>}
        <p className="text-muted-foreground">Certificate Status: {STATUS_LABEL[request.status]}</p>
        <p className="text-muted-foreground">Requested On: {formatDate(request.requestedAt)}</p>
        {request.status === 'REJECTED' && request.rejectionReason && (
          <p className="text-destructive">Reason: {request.rejectionReason}</p>
        )}
      </CardContent>
    </Card>
  )
}

/** The certificate's verification URL is built from the server's configured base; keep only the path. */
function verifyPath(url: string): string {
  try {
    const parsed = new URL(url)
    return `${parsed.pathname}${parsed.search}`
  } catch {
    return url
  }
}

function CriterionRow({ criterion }: { criterion: EligibilityCriterion }) {
  if (criterion.outcome === 'NOT_REQUIRED') return null
  const Icon = criterion.outcome === 'MET' ? CheckCircle2 : XCircle
  const color =
    criterion.outcome === 'MET'
      ? 'text-emerald-600'
      : criterion.outcome === 'UNAVAILABLE'
        ? 'text-muted-foreground'
        : 'text-destructive'
  return (
    <li className="flex items-center gap-2">
      <Icon className={`size-4 shrink-0 ${color}`} />
      <span>{criterion.name}</span>
      {criterion.detail && <span className="text-xs text-muted-foreground">({criterion.detail})</span>}
    </li>
  )
}
