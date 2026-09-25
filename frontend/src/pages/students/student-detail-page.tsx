import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { getStudentProgress } from '@/api/progress'
import { getStudent, updateStudentStatus } from '@/api/students'
import { ProgressReportView } from '@/components/progress-report'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

const STATUSES = ['ACTIVE', 'ALUMNI', 'DROPPED', 'SUSPENDED']

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'default',
  ALUMNI: 'secondary',
  DROPPED: 'destructive',
  SUSPENDED: 'destructive',
}

export function StudentDetailPage() {
  const { studentId } = useParams<{ studentId: string }>()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: ['students', studentId],
    queryFn: () => getStudent(studentId!),
    enabled: !!studentId,
  })

  const statusMutation = useMutation({
    mutationFn: (status: string) => updateStudentStatus(studentId!, status, `Changed via admin console`),
    onSuccess: () => {
      toast.success('Status updated')
      void queryClient.invalidateQueries({ queryKey: ['students', studentId] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change the status.')),
  })

  if (query.isLoading) return <Skeleton className="h-96 max-w-2xl" />
  const student = query.data
  if (!student) return null

  return (
    <div className="flex max-w-5xl flex-col gap-6">
      <Link to="/app/students" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Students
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{student.fullName}</h1>
          <p className="text-muted-foreground">
            {student.studentCode} &middot; {student.email} &middot; {student.phone}
          </p>
        </div>
        <select
          value={student.status}
          onChange={(event) => statusMutation.mutate(event.target.value)}
          disabled={statusMutation.isPending}
          className="h-9 rounded-md border bg-transparent px-3 text-sm"
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      <Badge variant={STATUS_VARIANT[student.status] ?? 'outline'} className="w-fit">
        {student.status}
      </Badge>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Profile</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
          <Field label="Admission date" value={student.admissionDate && formatDate(student.admissionDate)} />
          <Field label="Admission source" value={student.admissionSource} />
          <Field label="Date of birth" value={student.dateOfBirth && formatDate(student.dateOfBirth)} />
          <Field label="Gender" value={student.gender} />
          <Field label="Education" value={student.highestEducation} />
          <Field label="College" value={student.college} />
          <Field label="Address" value={[student.addressLine, student.city, student.state, student.pincode].filter(Boolean).join(', ')} />
          <Field label="Guardian" value={student.guardianName && `${student.guardianName} (${student.guardianPhone ?? '—'})`} />
          <Field label="Emergency contact" value={student.emergencyContact} />
        </CardContent>
      </Card>

      {student.remarks && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Remarks</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">{student.remarks}</CardContent>
        </Card>
      )}

      <StudentProgress studentId={student.id} />
    </div>
  )
}

/** The student's complete record and progress, for the staff and trainers who may see it. */
function StudentProgress({ studentId }: { studentId: number }) {
  const query = useQuery({ queryKey: ['progress', 'student', studentId], queryFn: () => getStudentProgress(studentId) })

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">Progress report</h2>
      {query.isLoading && <Skeleton className="h-64" />}
      {query.isError && (
        <p className="text-sm text-muted-foreground">
          {apiErrorMessage(query.error, 'The progress report could not be loaded.')}
        </p>
      )}
      {query.data && <ProgressReportView report={query.data} />}
    </section>
  )
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium">{value || '—'}</p>
    </div>
  )
}
