import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Award, CheckCircle2, Download, XCircle } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  certificateEligibility,
  certificatePdfObjectUrl,
  claimCertificate,
  type EligibilityCriterion,
  myCertificates,
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
  const progressQuery = useQuery({ queryKey: ['courses', 'my-progress'], queryFn: myCourseProgress })

  const certifiedCourseIds = new Set(certificatesQuery.data?.map((c) => c.courseId))
  const uncertifiedProgress = progressQuery.data?.filter((p) => !certifiedCourseIds.has(p.courseId)) ?? []
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

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">Certificates</h1>
        <p className="text-muted-foreground">What you&apos;ve earned, and what&apos;s left to earn one.</p>
      </div>

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
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => void handleDownload(certificate.id, certificate.certificateNo)}
                >
                  <Download className="size-3.5" />
                  Download
                </Button>
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
                onClaimed={() => void queryClient.invalidateQueries({ queryKey: ['certificates'] })}
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
  onClaimed,
}: {
  studentId: number
  courseId: number
  courseTitle: string
  onClaimed: () => void
}) {
  const query = useQuery({
    queryKey: ['certificates', 'eligibility', studentId, courseId],
    queryFn: () => certificateEligibility(studentId, courseId),
  })

  const claim = useMutation({
    mutationFn: () => claimCertificate(courseId),
    onSuccess: () => {
      toast.success('Certificate issued')
      onClaimed()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not claim the certificate.')),
  })

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
            {query.data.eligible ? (
              <Button size="sm" className="self-start" onClick={() => claim.mutate()} disabled={claim.isPending}>
                {claim.isPending ? 'Claiming...' : 'Claim certificate'}
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
