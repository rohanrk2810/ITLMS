import { useQueries, useQuery } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { getRoster } from '@/api/batches'
import { getCourseDetail, getLessonWatchReport } from '@/api/courses'
import { ExportMenu } from '@/components/export-menu'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { lessonWatchReport } from '@/lib/export-rows'

function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

/** ADMIN / COORDINATOR / TRAINER: who watched a video lesson, how much of it, and how often the face went missing. */
export function LessonWatchReportPage() {
  const { courseId, lessonId } = useParams<{ courseId: string; lessonId: string }>()

  const course = useQuery({
    queryKey: ['courses', 'detail', courseId],
    queryFn: () => getCourseDetail(courseId!),
    enabled: !!courseId,
  })
  const report = useQuery({
    queryKey: ['lesson-watch-report', lessonId],
    queryFn: () => getLessonWatchReport(lessonId!),
    enabled: !!lessonId,
  })

  // Student names live with the batches, so read each batch's roster once.
  const batchIds = [...new Set((report.data ?? []).map((r) => r.batchId).filter((id): id is number => id != null))]
  const rosters = useQueries({
    queries: batchIds.map((id) => ({ queryKey: ['batches', String(id), 'roster'], queryFn: () => getRoster(id) })),
  })
  const names = new Map<number, string>()
  rosters.forEach((roster) => roster.data?.forEach((e) => names.set(e.studentId, e.studentName)))

  const lesson = course.data?.modules.flatMap((m) => m.lessons).find((l) => String(l.id) === lessonId)
  const rows = [...(report.data ?? [])].sort((a, b) => a.watchedPercent - b.watchedPercent)

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <Link
        to={`/app/courses/${courseId}`}
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" />
        Back to course
      </Link>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-2">
            <CardTitle>Who watched: {lesson?.title ?? 'video lesson'}</CardTitle>
            <ExportMenu
              label="Download"
              report="WATCH_REPORT"
              detail={(lesson?.title ?? `lesson ${lessonId}`).slice(0, 120)}
              build={async () =>
                lessonWatchReport(report.data ?? [], names, {
                  title: lesson?.title ?? `Lesson ${lessonId}`,
                  course: course.data?.course.title,
                  monitored: lesson?.videoMonitoring,
                })
              }
            />
          </div>
          <CardDescription>
            Share of the video actually played (skipping ahead does not count), time the camera found no face while it
            played, and times it paused for that. Shown least-watched first.
            {lesson && !lesson.videoMonitoring && ' Camera monitoring is off for this lesson, so face figures stay at zero.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {report.isLoading && <Skeleton className="h-24" />}
          {report.data && rows.length === 0 && (
            <p className="text-sm text-muted-foreground">No student is enrolled in this course yet.</p>
          )}
          {rows.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Student</TableHead>
                  <TableHead>Watched</TableHead>
                  <TableHead>Time played</TableHead>
                  <TableHead>Face away</TableHead>
                  <TableHead>Auto-pauses</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.studentId}>
                    <TableCell className="font-medium">{names.get(row.studentId) ?? `Student #${row.studentId}`}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-20 overflow-hidden rounded bg-muted">
                          <div className="h-full bg-primary" style={{ width: `${row.watchedPercent}%` }} />
                        </div>
                        {row.watchedPercent}%
                      </div>
                    </TableCell>
                    <TableCell>
                      {row.durationSeconds > 0
                        ? `${formatSeconds(row.playedSeconds)} / ${formatSeconds(row.durationSeconds)}`
                        : '-'}
                    </TableCell>
                    <TableCell>{formatSeconds(row.faceAwaySeconds)}</TableCell>
                    <TableCell>{row.facePauses}</TableCell>
                    <TableCell>
                      {row.completed ? (
                        <Badge>Completed</Badge>
                      ) : row.watchedPercent > 0 ? (
                        <Badge variant="secondary">Watching</Badge>
                      ) : (
                        <Badge variant="outline">Not started</Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
