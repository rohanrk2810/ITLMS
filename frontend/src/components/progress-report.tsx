import { AlertTriangle, ArrowRight, CheckCircle2, Circle } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { Band, CourseProgress, Indicator, StudentProgressReport, Suggestion } from '@/api/progress'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'

const BAND_TEXT: Record<Band, string> = {
  GOOD: 'text-emerald-600',
  FAIR: 'text-amber-600',
  LOW: 'text-destructive',
  NONE: 'text-muted-foreground',
}

const BAND_LABEL: Record<Band, string> = { GOOD: 'Good', FAIR: 'Fair', LOW: 'Needs work', NONE: 'No data yet' }

const SECTION_NAME: Record<string, string> = {
  enrollments: 'batches',
  courses: 'course progress',
  liveClasses: 'live classes',
  assessments: 'tests, coding and assignments',
  profile: 'profile',
}

/**
 * A student's complete record: headline numbers, what to do next, batches, courses, attendance, live classes, tests,
 * coding and assignments. Used for a student's own page and for staff looking at a student, so it says "the student"
 * rather than "you" where the two would differ.
 */
export function ProgressReportView({ report }: { report: StudentProgressReport }) {
  return (
    <div className="flex flex-col gap-6">
      {report.unavailable.length > 0 && (
        <p className="flex items-center gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm">
          <AlertTriangle className="size-4 shrink-0 text-amber-600" />
          Some information could not be loaded just now: {report.unavailable.map((s) => SECTION_NAME[s] ?? s).join(', ')}.
          What is shown is correct; reload in a moment for the rest.
        </p>
      )}

      <section aria-label="Headline numbers" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {report.indicators.map((indicator) => (
          <IndicatorCard key={indicator.key} indicator={indicator} />
        ))}
      </section>

      <Suggestions suggestions={report.suggestions} />

      <Enrollments report={report} />

      {report.courses && report.courses.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">Courses</h2>
          {report.courses.map((course) => (
            <CourseCard key={`${course.courseId}-${course.batchId}`} course={course} />
          ))}
        </section>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <TestsCard report={report} />
        <CodingCard report={report} />
        <AssignmentsCard report={report} />
        <LiveCard report={report} />
      </div>

      <p className="text-xs text-muted-foreground">Worked out from the institute&apos;s records at {new Date(report.generatedAt).toLocaleString()}.</p>
    </div>
  )
}

function IndicatorCard({ indicator }: { indicator: Indicator }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{indicator.label}</CardDescription>
        <CardTitle className={cn('text-3xl', BAND_TEXT[indicator.band])}>
          {indicator.percent == null ? '-' : `${indicator.percent}%`}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <Progress value={indicator.percent ?? 0} aria-label={`${indicator.label}: ${indicator.percent ?? 'no data'}`} />
        <p className="text-xs text-muted-foreground">{indicator.detail}</p>
        <span className={cn('text-xs font-medium', BAND_TEXT[indicator.band])}>{BAND_LABEL[indicator.band]}</span>
      </CardContent>
    </Card>
  )
}

const PRIORITY_DOT = ['', 'text-destructive', 'text-amber-500', 'text-muted-foreground']

function Suggestions({ suggestions }: { suggestions: Suggestion[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">What to do next</CardTitle>
        <CardDescription>Worked out from the numbers above, most important first.</CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="flex flex-col divide-y">
          {suggestions.map((suggestion, index) => (
            <li key={`${suggestion.type}-${index}`} className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
              {suggestion.type === 'ON_TRACK' ? (
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" />
              ) : (
                <Circle className={cn('mt-0.5 size-4 shrink-0 fill-current', PRIORITY_DOT[suggestion.priority] ?? '')} />
              )}
              <div className="flex-1">
                <p className="text-sm font-medium">{suggestion.title}</p>
                <p className="text-xs text-muted-foreground">{suggestion.detail}</p>
              </div>
              {suggestion.type !== 'ON_TRACK' && (
                <Link to={suggestion.link} className="flex shrink-0 items-center gap-1 text-xs text-primary hover:underline">
                  Open <ArrowRight className="size-3" />
                </Link>
              )}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

function Enrollments({ report }: { report: StudentProgressReport }) {
  if (report.enrollments.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Batches</CardTitle>
          <CardDescription>Not enrolled in any batch yet.</CardDescription>
        </CardHeader>
      </Card>
    )
  }
  const attendanceOf = new Map(report.attendance.byBatch.map((b) => [b.batchId, b]))
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Batches and attendance</CardTitle>
        <CardDescription>
          {report.attendance.percent == null
            ? 'No classes marked yet.'
            : `Overall attendance ${report.attendance.percent}%: attended ${report.attendance.attended} of ${report.attendance.counted} classes` +
              (report.attendance.excused > 0 ? ` (${report.attendance.excused} excused absence(s) not counted).` : '.')}
        </CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="py-2 pr-3 font-medium">Batch</th>
              <th className="py-2 pr-3 font-medium">Course</th>
              <th className="py-2 pr-3 font-medium">Trainer</th>
              <th className="py-2 pr-3 font-medium">Status</th>
              <th className="py-2 pr-3 font-medium">Dates</th>
              <th className="py-2 font-medium">Attendance</th>
            </tr>
          </thead>
          <tbody>
            {report.enrollments.map((e) => {
              const attendance = attendanceOf.get(e.batchId)
              return (
                <tr key={e.enrollmentId} className="border-b last:border-0">
                  <td className="py-2 pr-3">
                    {e.batchName ?? `Batch #${e.batchId}`}
                    {e.batchCode && <span className="text-muted-foreground"> ({e.batchCode})</span>}
                  </td>
                  <td className="py-2 pr-3">{e.courseTitle ?? `Course #${e.courseId}`}</td>
                  <td className="py-2 pr-3">{e.trainerName ?? '-'}</td>
                  <td className="py-2 pr-3">
                    <Badge variant={e.status === 'ACTIVE' ? 'default' : 'secondary'}>{e.status}</Badge>
                  </td>
                  <td className="py-2 pr-3 whitespace-nowrap">
                    {e.startDate ? formatDate(e.startDate) : '-'}
                    {e.endDate ? ` to ${formatDate(e.endDate)}` : ''}
                  </td>
                  <td className="py-2">
                    {attendance?.percent == null ? '-' : `${attendance.percent}% (${attendance.attended}/${attendance.counted})`}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </CardContent>
    </Card>
  )
}

function CourseCard({ course }: { course: CourseProgress }) {
  const pending = course.modules.filter((m) => m.lessons > 0 && m.completed < m.lessons)
  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="text-base">
            <Link to={`/app/courses/${course.courseId}`} className="hover:underline">
              {course.courseTitle ?? `Course #${course.courseId}`}
            </Link>
          </CardTitle>
          <Badge variant={course.status === 'ACTIVE' ? 'default' : 'secondary'}>{course.status}</Badge>
        </div>
        <CardDescription>
          {course.completedLessons} of {course.totalLessons} lessons finished
          {course.videoLessons > 0 && ` · ${course.videoLessonsCompleted} of ${course.videoLessons} recorded lessons`}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Progress value={course.progressPercent} aria-label={`${course.courseTitle ?? 'Course'} progress`} />
        {course.modules.length > 0 && (
          <ul className="grid gap-1 text-sm sm:grid-cols-2">
            {course.modules.map((m) => (
              <li key={m.moduleId} className="flex items-center justify-between gap-2">
                <span className={cn(m.lessons > 0 && m.completed >= m.lessons && 'text-muted-foreground')}>{m.title}</span>
                <span className="text-xs text-muted-foreground">
                  {m.lessons === 0 ? 'no lessons' : m.completed >= m.lessons ? 'done' : `${m.completed}/${m.lessons}`}
                </span>
              </li>
            ))}
          </ul>
        )}
        {pending.length > 0 && course.status === 'ACTIVE' && (
          <p className="text-xs text-muted-foreground">
            {pending.length} module(s) still to finish, starting with {pending[0].title}.
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function TestsCard({ report }: { report: StudentProgressReport }) {
  const tests = report.tests
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Tests</CardTitle>
        <CardDescription>
          {tests == null
            ? 'Not available just now.'
            : tests.attempted === 0
              ? 'No tests taken yet.'
              : `Passed ${tests.passed} of ${tests.attempted}` +
                (tests.averagePercent != null ? ` · average ${tests.averagePercent}%` : '') +
                (tests.bestPercent != null ? ` · best ${tests.bestPercent}%` : '')}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 text-sm">
        {tests && tests.resultsPending > 0 && (
          <p className="text-xs text-muted-foreground">{tests.resultsPending} result(s) will show once the trainer releases them.</p>
        )}
        {tests && tests.terminated > 0 && (
          <p className="text-xs text-destructive">{tests.terminated} attempt(s) were ended for leaving the test window.</p>
        )}
        {tests && tests.pending.length > 0 && (
          <List title="To take" items={tests.pending.map((p) => ({ key: p.id, label: p.title, to: `/app/assessments/tests/${p.id}`, note: p.dueAt ? `closes ${formatDate(p.dueAt)}` : 'open now' }))} />
        )}
        {tests && tests.below.length > 0 && (
          <List title="Below the pass mark" items={tests.below.map((w) => ({ key: w.id, label: w.title, to: `/app/assessments/tests/${w.id}`, note: `${w.percent}% (pass ${w.passPercent}%)` }))} />
        )}
      </CardContent>
    </Card>
  )
}

function CodingCard({ report }: { report: StudentProgressReport }) {
  const coding = report.coding
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Coding</CardTitle>
        <CardDescription>
          {coding == null
            ? 'Not available just now.'
            : coding.questionsAttempted === 0
              ? 'No coding questions attempted yet.'
              : `${coding.testCasesPassed} of ${coding.testCasesTotal} test cases passed across ${coding.questionsAttempted} question(s)` +
                (coding.averagePercent != null ? ` · average ${coding.averagePercent}%` : '')}
        </CardDescription>
      </CardHeader>
      <CardContent className="text-sm">
        {coding && coding.lowest.length > 0 && (
          <List title="Not fully solved" items={coding.lowest.map((q) => ({ key: q.questionId, label: q.question, note: `${q.passed}/${q.total} test cases` }))} />
        )}
      </CardContent>
    </Card>
  )
}

function AssignmentsCard({ report }: { report: StudentProgressReport }) {
  const work = report.assignments
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Assignments</CardTitle>
        <CardDescription>
          {work == null
            ? 'Not available just now.'
            : work.assigned === 0
              ? 'No assignments set yet.'
              : `${work.submitted} of ${work.assigned} handed in · ${work.evaluated} marked` +
                (work.averagePercent != null ? ` · average ${work.averagePercent}%` : '')}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 text-sm">
        {work && (work.returned > 0 || work.late > 0) && (
          <p className="text-xs text-muted-foreground">
            {work.returned > 0 && `${work.returned} sent back for rework. `}
            {work.late > 0 && `${work.late} handed in late.`}
          </p>
        )}
        {work && work.pending.length > 0 && (
          <List title="To hand in" items={work.pending.map((p) => ({ key: p.id, label: p.title, to: `/app/assessments/assignments/${p.id}`, note: p.dueAt ? `${p.overdue ? 'was due' : 'due'} ${formatDate(p.dueAt)}` : 'no deadline', alert: p.overdue }))} />
        )}
      </CardContent>
    </Card>
  )
}

function LiveCard({ report }: { report: StudentProgressReport }) {
  const live = report.liveClasses
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Live classes</CardTitle>
        <CardDescription>
          {live == null
            ? 'Not available just now.'
            : live.sessionsHeld === 0
              ? 'No live classes held yet.'
              : `Joined ${live.sessionsJoined} of ${live.sessionsHeld} · ${live.minutesInRoom} minutes in the room` +
                (live.averageAttendancePercent != null ? ` · ${live.averageAttendancePercent}% of each class on average` : '')}
        </CardDescription>
      </CardHeader>
      <CardContent className="text-xs text-muted-foreground">
        {live?.lastJoinedAt && <>Last joined {formatDate(live.lastJoinedAt)}.</>}
      </CardContent>
    </Card>
  )
}

function List({
  title,
  items,
}: {
  title: string
  items: { key: number; label: string; to?: string; note?: string; alert?: boolean }[]
}) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium text-muted-foreground">{title}</p>
      <ul className="flex flex-col gap-1">
        {items.map((item) => (
          <li key={item.key} className="flex items-center justify-between gap-2">
            {item.to ? (
              <Link to={item.to} className="hover:underline">
                {item.label}
              </Link>
            ) : (
              <span>{item.label}</span>
            )}
            {item.note && <span className={cn('text-xs', item.alert ? 'text-destructive' : 'text-muted-foreground')}>{item.note}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}
