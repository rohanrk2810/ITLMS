import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { getRoster, getSession, getSessionAttendance, markAttendance } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

const STATUSES = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED']

type Marks = Record<number, { status: string; remark: string }>

export function AttendanceMarkPage() {
  const { batchId, sessionId } = useParams<{ batchId: string; sessionId: string }>()
  const navigate = useNavigate()
  const [marks, setMarks] = useState<Marks>({})
  const [correctionReason, setCorrectionReason] = useState('')

  const sessionQuery = useQuery({
    queryKey: ['sessions', sessionId],
    queryFn: () => getSession(sessionId!),
    enabled: !!sessionId,
  })
  const rosterQuery = useQuery({
    queryKey: ['batches', batchId, 'roster'],
    queryFn: () => getRoster(batchId!),
    enabled: !!batchId,
  })
  const existingQuery = useQuery({
    queryKey: ['sessions', sessionId, 'attendance'],
    queryFn: () => getSessionAttendance(sessionId!),
    enabled: !!sessionId,
  })

  const alreadyMarked = (existingQuery.data?.length ?? 0) > 0

  useEffect(() => {
    if (!existingQuery.data || existingQuery.data.length === 0) return
    setMarks((prev) => {
      const next = { ...prev }
      for (const record of existingQuery.data) {
        next[record.studentId] = { status: record.status, remark: record.remark ?? '' }
      }
      return next
    })
  }, [existingQuery.data])

  const mutation = useMutation({
    mutationFn: () => {
      const roster = rosterQuery.data ?? []
      const entries = roster.map((enrollment) => ({
        studentId: enrollment.studentId,
        status: marks[enrollment.studentId]?.status ?? 'PRESENT',
        remark: marks[enrollment.studentId]?.remark || undefined,
      }))
      return markAttendance(sessionId!, entries, alreadyMarked ? correctionReason : undefined)
    },
    onSuccess: () => {
      toast.success('Attendance saved')
      void navigate(`/app/batches/${batchId}`)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save attendance.')),
  })

  function setStatus(studentId: number, status: string) {
    setMarks((prev) => ({ ...prev, [studentId]: { status, remark: prev[studentId]?.remark ?? '' } }))
  }

  if (sessionQuery.isLoading || rosterQuery.isLoading) {
    return <Skeleton className="h-96 max-w-2xl" />
  }
  const session = sessionQuery.data
  const roster = rosterQuery.data ?? []

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <Link
        to={`/app/batches/${batchId}`}
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" />
        Back to batch
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">Mark attendance</h1>
        {session && (
          <p className="text-muted-foreground">
            {formatDate(session.sessionDate)} &middot; {session.topic ?? session.courseTitle}
          </p>
        )}
      </div>

      {alreadyMarked && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm">This register was already saved</CardTitle>
            <CardDescription>Re-marking is a correction and needs a reason (Doc S14).</CardDescription>
          </CardHeader>
          <CardContent>
            <Input
              placeholder="Reason for the correction"
              value={correctionReason}
              onChange={(event) => setCorrectionReason(event.target.value)}
            />
          </CardContent>
        </Card>
      )}

      <div className="flex flex-col gap-3">
        {roster.map((enrollment) => (
          <Card key={enrollment.id}>
            <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
              <div>
                <p className="font-medium">{enrollment.studentName}</p>
                <p className="text-xs text-muted-foreground">{enrollment.studentCode}</p>
              </div>
              <RadioGroup
                value={marks[enrollment.studentId]?.status ?? 'PRESENT'}
                onValueChange={(value) => setStatus(enrollment.studentId, value)}
                className="flex flex-row gap-4"
              >
                {STATUSES.map((status) => (
                  <Label key={status} className="flex items-center gap-1.5 text-sm font-normal">
                    <RadioGroupItem value={status} />
                    {status}
                  </Label>
                ))}
              </RadioGroup>
            </CardContent>
          </Card>
        ))}
        {roster.length === 0 && <p className="text-sm text-muted-foreground">No active students in this batch.</p>}
      </div>

      <Button
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending || roster.length === 0 || (alreadyMarked && !correctionReason.trim())}
        className="self-start"
      >
        {mutation.isPending ? 'Saving...' : 'Save register'}
      </Button>
    </div>
  )
}
