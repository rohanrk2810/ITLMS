import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'

import { getMyProgress } from '@/api/progress'
import type { DashboardSummaryResponse } from '@/api/reporting'
import { BarChart, DoughnutChart, SEMANTIC } from '@/components/charts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatMoney } from '@/lib/format'

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

/** A student's own picture on their dashboard: course progress, attendance, tests, coding and assignments. */
export function StudentCharts() {
  const query = useQuery({ queryKey: ['progress', 'me'], queryFn: getMyProgress, staleTime: 60_000 })

  if (query.isLoading) {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <Skeleton className="h-64" />
        <Skeleton className="h-64" />
      </div>
    )
  }
  const r = query.data
  if (!r) return null

  const courses = r.courses ?? []
  const attendance = r.attendance
  const tests = r.tests
  const coding = r.coding
  const assignments = r.assignments

  const cards: ReactNode[] = []

  if (courses.length > 0) {
    cards.push(
      <ChartCard key="courses" title="Course progress">
        <BarChart
          horizontal
          max={100}
          unit="%"
          labels={courses.map((c) => c.courseTitle ?? `Course ${c.courseId}`)}
          series={[{ label: 'Progress', values: courses.map((c) => Math.round(c.progressPercent)) }]}
          height={Math.max(140, 50 + courses.length * 34)}
          ariaLabel={`Lesson progress per course: ${courses
            .map((c) => `${c.courseTitle ?? c.courseId} ${Math.round(c.progressPercent)} percent`)
            .join(', ')}.`}
        />
      </ChartCard>,
    )
  }

  if (attendance.counted > 0) {
    cards.push(
      <ChartCard key="attendance" title={`Attendance${attendance.percent != null ? ` (${Math.round(attendance.percent)}%)` : ''}`}>
        <DoughnutChart
          labels={['Present', 'Absent', 'Excused']}
          values={[attendance.attended, attendance.absent, attendance.excused]}
          colors={[SEMANTIC.good, SEMANTIC.bad, SEMANTIC.neutral]}
          ariaLabel={`Attendance: ${attendance.attended} present, ${attendance.absent} absent, ${attendance.excused} excused.`}
        />
      </ChartCard>,
    )
  }

  if (tests && tests.attempted > 0) {
    const notPassed = Math.max(0, tests.attempted - tests.passed)
    cards.push(
      <ChartCard key="tests" title="Tests">
        <DoughnutChart
          labels={['Passed', 'Not passed']}
          values={[tests.passed, notPassed]}
          colors={[SEMANTIC.good, SEMANTIC.bad]}
          ariaLabel={`Tests: ${tests.passed} passed, ${notPassed} not passed.`}
        />
        {tests.averagePercent != null && (
          <p className="mt-2 text-xs text-muted-foreground">
            Average {Math.round(tests.averagePercent)}%
            {tests.bestPercent != null && `, best ${Math.round(tests.bestPercent)}%`}
          </p>
        )}
      </ChartCard>,
    )
  }

  if (coding && coding.testCasesTotal > 0) {
    const failed = coding.testCasesTotal - coding.testCasesPassed
    cards.push(
      <ChartCard key="coding" title="Coding test cases">
        <DoughnutChart
          labels={['Passed', 'Failed']}
          values={[coding.testCasesPassed, failed]}
          colors={[SEMANTIC.good, SEMANTIC.bad]}
          ariaLabel={`Coding: ${coding.testCasesPassed} of ${coding.testCasesTotal} test cases passed.`}
        />
      </ChartCard>,
    )
  }

  if (assignments && assignments.assigned > 0) {
    cards.push(
      <ChartCard key="assignments" title="Assignments">
        <BarChart
          labels={['Assigned', 'Submitted', 'Evaluated', 'Late']}
          series={[
            {
              label: 'Assignments',
              values: [assignments.assigned, assignments.submitted, assignments.evaluated, assignments.late],
              color: [SEMANTIC.neutral, '#2563eb', SEMANTIC.good, SEMANTIC.warn],
            },
          ]}
          height={200}
          ariaLabel={`Assignments: ${assignments.assigned} assigned, ${assignments.submitted} submitted, ${assignments.evaluated} evaluated, ${assignments.late} late.`}
        />
      </ChartCard>,
    )
  }

  if (cards.length === 0) return null
  return <div className="grid gap-4 md:grid-cols-2">{cards}</div>
}

/** The institute's numbers as pictures, for administrators and coordinators. */
export function InstituteCharts({ s }: { s: DashboardSummaryResponse }) {
  const dueLater = Math.max(0, s.feesBilledTotal - s.revenueCollectedTotal - s.overdueAmountTotal)
  const notPassed = Math.max(0, s.quizAttemptsTotal - s.quizAttemptsPassed)

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <ChartCard title="Attendance">
        <DoughnutChart
          labels={['Present', 'Late', 'Excused', 'Absent']}
          values={[s.attendance.present, s.attendance.late, s.attendance.excused, s.attendance.absent]}
          colors={[SEMANTIC.good, SEMANTIC.warn, SEMANTIC.neutral, SEMANTIC.bad]}
          ariaLabel={`Institute attendance: ${s.attendance.present} present, ${s.attendance.late} late, ${s.attendance.excused} excused, ${s.attendance.absent} absent.`}
        />
      </ChartCard>

      <ChartCard title="Fees">
        <DoughnutChart
          labels={['Collected', 'Overdue', 'Not yet due']}
          values={[s.revenueCollectedTotal, s.overdueAmountTotal, dueLater]}
          colors={[SEMANTIC.good, SEMANTIC.bad, SEMANTIC.neutral]}
          ariaLabel={`Fees: ${formatMoney(s.revenueCollectedTotal)} collected, ${formatMoney(s.overdueAmountTotal)} overdue, ${formatMoney(dueLater)} not yet due.`}
        />
      </ChartCard>

      <ChartCard title="Test attempts">
        <DoughnutChart
          labels={['Passed', 'Not passed']}
          values={[s.quizAttemptsPassed, notPassed]}
          colors={[SEMANTIC.good, SEMANTIC.bad]}
          ariaLabel={`Test attempts: ${s.quizAttemptsPassed} passed, ${notPassed} not passed.`}
        />
      </ChartCard>

      <ChartCard title="Admissions and enrolments: last 30 days and before">
        <BarChart
          stacked
          labels={['Students admitted', 'Enrolments']}
          series={[
            {
              label: 'Before the last 30 days',
              values: [
                Math.max(0, s.studentsAdmittedTotal - s.studentsAdmittedLast30Days),
                Math.max(0, s.enrollmentsTotal - s.enrollmentsLast30Days),
              ],
              color: SEMANTIC.neutral,
            },
            {
              label: 'Last 30 days',
              values: [s.studentsAdmittedLast30Days, s.enrollmentsLast30Days],
              color: '#2563eb',
            },
          ]}
          height={220}
          ariaLabel={`Students admitted: ${s.studentsAdmittedLast30Days} in the last 30 days of ${s.studentsAdmittedTotal} in all. Enrolments: ${s.enrollmentsLast30Days} of ${s.enrollmentsTotal}.`}
        />
      </ChartCard>
    </div>
  )
}
