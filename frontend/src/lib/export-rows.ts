import type { AttemptResultResponse } from '@/api/assessments'
import type { LessonWatch } from '@/api/courses'
import type { FeePlanResponse, OverdueItemResponse } from '@/api/finance'
import type { LeadResponse } from '@/api/leads'
import type { StudentProgressReport } from '@/api/progress'
import type { DashboardSummaryResponse } from '@/api/reporting'
import type { StudentResponse, StudentSummaryResponse } from '@/api/students'

import type { Cell, Report } from './report'

/**
 * Each report an administrator can download or print: its title, the facts it covers and its columns. Pure
 * functions, so what a file contains is tested without a browser. The page fetches the data; these decide the
 * layout; lib/report-output.ts saves or prints it.
 */

const yesNo = (value: boolean | null | undefined) => (value == null ? '' : value ? 'Yes' : 'No')
const day = (iso: string | null | undefined) => (iso ? iso.slice(0, 10) : '')
const dateTime = (iso: string | null | undefined) => (iso ? iso.slice(0, 16).replace('T', ' ') : '')

export function studentsReport(students: StudentResponse[], filter: { status?: string; query?: string } = {}): Report {
  return {
    title: 'Students',
    fileName: 'students',
    info: [
      ['Students', String(students.length)],
      ['Status', filter.status || 'All'],
      ['Search', filter.query || 'None'],
    ],
    header: [
      'Student code', 'Name', 'Email', 'Phone', 'Status', 'Date of birth', 'Gender', 'Highest education', 'College',
      'Graduation year', 'Address', 'City', 'State', 'Pincode', 'Guardian', 'Guardian phone', 'Emergency contact',
      'Admission date', 'Admission source', 'Remarks',
    ],
    rows: students.map((s) => [
      s.studentCode, s.fullName, s.email, s.phone, s.status, s.dateOfBirth, s.gender, s.highestEducation, s.college,
      s.graduationYear, s.addressLine, s.city, s.state, s.pincode, s.guardianName, s.guardianPhone, s.emergencyContact,
      s.admissionDate, s.admissionSource, s.remarks,
    ]),
  }
}

export function leadsReport(leads: LeadResponse[], filter: { status?: string; query?: string } = {}): Report {
  return {
    title: 'Admission leads',
    fileName: 'leads',
    info: [
      ['Leads', String(leads.length)],
      ['Status', filter.status || 'All'],
      ['Search', filter.query || 'None'],
    ],
    header: [
      'Name', 'Phone', 'Email', 'Source', 'Status', 'Interested course id', 'Next follow-up', 'Overdue', 'Follow-ups',
      'Notes', 'Lost reason', 'Became student id', 'Converted on', 'Created',
    ],
    rows: leads.map((l) => [
      l.name, l.phone, l.email, l.source, l.status, l.interestedCourseId, dateTime(l.nextFollowUpAt), yesNo(l.overdue),
      l.followupCount, l.notes, l.lostReason, l.convertedStudentId, day(l.convertedAt), day(l.createdAt),
    ]),
  }
}

export interface BatchAttendanceRow {
  studentId: number
  studentName: string
  attendedSessions: number
  totalSessions: number
  attendancePercent: number | null
  belowThreshold?: boolean
}

export function attendanceReport(
  rows: BatchAttendanceRow[],
  batch: { code: string; course?: string | null; trainer?: string | null },
): Report {
  return {
    title: 'Batch attendance',
    fileName: `attendance-${batch.code.toLowerCase()}`,
    info: [
      ['Batch', batch.code],
      ['Course', batch.course || '-'],
      ['Trainer', batch.trainer || 'Not assigned'],
      ['Students', String(rows.length)],
      ['Below the limit', String(rows.filter((r) => r.belowThreshold).length)],
    ],
    header: ['Student id', 'Student', 'Sessions attended', 'Sessions counted', 'Attendance %', 'Below the institute limit'],
    rows: rows.map((r) => [
      r.studentId, r.studentName, r.attendedSessions, r.totalSessions, r.attendancePercent, yesNo(r.belowThreshold),
    ]),
  }
}

export function resultsReport(results: AttemptResultResponse[], quiz: { title: string }): Report {
  const released = results.filter((r) => r.resultVisible)
  const slug = quiz.title.replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase() || 'test'
  return {
    title: 'Test results',
    fileName: `results-${slug}`,
    info: [
      ['Test', quiz.title],
      ['Attempts', String(results.length)],
      ['Passed', String(released.filter((r) => r.passed).length)],
      ['Not yet released', String(results.length - released.length)],
    ],
    header: [
      'Student', 'Attempt', 'Status', 'Started', 'Submitted', 'Score', 'Out of', 'Percentage', 'Pass mark %', 'Passed',
      'Rule violations', 'Camera events', 'Ended because', 'Typing review',
    ],
    rows: results.map((r) => [
      r.studentName, r.attemptNo, r.status, dateTime(r.startedAt), dateTime(r.submittedAt),
      r.resultVisible ? r.score : '', r.totalMarks, r.resultVisible ? r.percentage : '', r.passPercentage,
      r.resultVisible ? yesNo(r.passed) : '', r.violationCount, r.cameraEventCount, r.terminatedReason, r.typingReview,
    ]),
  }
}

export function dashboardReport(s: DashboardSummaryResponse): Report {
  const rows: Cell[][] = [
    ['Students admitted (total)', s.studentsAdmittedTotal],
    ['Students admitted (last 30 days)', s.studentsAdmittedLast30Days],
    ['Enrollments (total)', s.enrollmentsTotal],
    ['Enrollments (last 30 days)', s.enrollmentsLast30Days],
    ['Fees billed', s.feesBilledTotal],
    ['Revenue collected (total)', s.revenueCollectedTotal],
    ['Revenue collected (last 30 days)', s.revenueCollectedLast30Days],
    ['Instalments overdue', s.installmentsOverdueTotal],
    ['Amount overdue', s.overdueAmountTotal],
    ['Certificates issued', s.certificatesIssuedTotal],
    ['Assignments evaluated', s.submissionsEvaluatedTotal],
    ['Test attempts', s.quizAttemptsTotal],
    ['Test attempts passed', s.quizAttemptsPassed],
    ['Jobs posted', s.jobsPostedTotal],
    ['Students placed', s.placementsSelectedTotal],
    ['Attendance: present', s.attendance.present],
    ['Attendance: absent', s.attendance.absent],
    ['Attendance: late', s.attendance.late],
    ['Attendance: excused', s.attendance.excused],
    ['Attendance %', s.attendance.percent],
  ]
  return {
    title: 'Institute figures',
    fileName: 'institute-figures',
    info: [['Figures as of', dateTime(s.generatedAt)]],
    header: ['Figure', 'Value'],
    rows,
  }
}

/**
 * One student's progress report in sections. The facts above the table are the student's own details, laid out like the
 * top of a grade card. A section the server could not load is named at the end.
 */
export function progressReport(r: StudentProgressReport): Report {
  const rows: Cell[][] = []
  const section = (title: string) => rows.push([`# ${title}`, ''])
  const add = (label: string, value: Cell) => rows.push([label, value])

  section('Indicators')
  for (const i of r.indicators) add(i.label, i.percent == null ? `no data (${i.detail})` : `${i.percent}% (${i.detail})`)

  section('Enrollments')
  for (const e of r.enrollments) {
    add(`${e.courseTitle ?? 'Course'} / ${e.batchCode ?? e.batchId}`, `${e.status}; ${e.batchStatus ?? ''}; ${e.trainerName ?? 'no trainer'}`)
  }

  if (r.courses) {
    section('Course progress')
    for (const c of r.courses) {
      add(c.courseTitle ?? `Course ${c.courseId}`, `${c.progressPercent}% (${c.completedLessons}/${c.totalLessons} lessons; ${c.videoLessonsCompleted}/${c.videoLessons} videos)`)
    }
  }

  section('Attendance')
  add('Overall %', r.attendance.percent)
  add('Attended', r.attendance.attended)
  add('Counted', r.attendance.counted)
  add('Absent', r.attendance.absent)
  add('Excused', r.attendance.excused)
  for (const b of r.attendance.byBatch) add(`Batch ${b.batchCode ?? b.batchId}`, b.percent == null ? 'no data' : `${b.percent}% (${b.attended}/${b.counted})`)

  if (r.liveClasses) {
    section('Live classes')
    add('Classes held', r.liveClasses.sessionsHeld)
    add('Classes joined', r.liveClasses.sessionsJoined)
    add('Minutes in the room', r.liveClasses.minutesInRoom)
    add('Average attendance %', r.liveClasses.averageAttendancePercent)
  }

  if (r.tests) {
    section('Tests')
    add('Attempted', r.tests.attempted)
    add('Passed', r.tests.passed)
    add('Average %', r.tests.averagePercent)
    add('Best %', r.tests.bestPercent)
    add('Ended by the rules', r.tests.terminated)
    for (const t of r.tests.below) add(`Below the pass mark: ${t.title}`, `${t.percent}% (pass ${t.passPercent}%)`)
    for (const p of r.tests.pending) add(`Not taken: ${p.title}`, p.overdue ? 'overdue' : p.dueAt ? `due ${day(p.dueAt)}` : '')
  }

  if (r.coding) {
    section('Coding')
    add('Questions attempted', r.coding.questionsAttempted)
    add('Test cases passed', `${r.coding.testCasesPassed}/${r.coding.testCasesTotal}`)
    add('Average %', r.coding.averagePercent)
  }

  if (r.assignments) {
    section('Assignments')
    add('Assigned', r.assignments.assigned)
    add('Submitted', r.assignments.submitted)
    add('Evaluated', r.assignments.evaluated)
    add('Average %', r.assignments.averagePercent)
    add('Late', r.assignments.late)
    add('Returned for rework', r.assignments.returned)
  }

  section('Suggestions')
  for (const s of [...r.suggestions].sort((a, b) => a.priority - b.priority)) add(`${s.priority}. ${s.title}`, s.detail)

  if (r.unavailable.length > 0) {
    section('Could not be loaded just now')
    for (const name of r.unavailable) add(name, 'unavailable')
  }

  const code = r.profile?.studentCode ?? String(r.studentId)
  return {
    title: 'Student progress report',
    fileName: `progress-${code.toLowerCase()}`,
    info: [
      ["Student's name", r.profile?.fullName ?? '-'],
      ['Student code', code],
      ['Email', r.profile?.email ?? '-'],
      ['Phone', r.profile?.phone ?? '-'],
      ['Status', r.profile?.status ?? '-'],
      ['Report made', dateTime(r.generatedAt)],
    ],
    header: ['Item', 'Value'],
    rows,
  }
}

const sum = (values: number[]) => values.reduce((a, b) => a + b, 0)
/** A total only means something in one currency; with several, say so rather than add rupees to dollars. */
const total = (items: { currency: string }[], pick: (i: never) => number) => {
  const currencies = new Set(items.map((i) => i.currency))
  return currencies.size > 1 ? 'mixed currencies' : String(sum(items.map((i) => pick(i as never))))
}

/** Every student with their contact details and fee position, one row per fee plan (a student without a plan still gets a row). */
export function feePositionReport(students: StudentSummaryResponse[], plans: FeePlanResponse[]): Report {
  const rows = students.flatMap((student) => {
    const mine = plans.filter((plan) => plan.studentId === student.id)
    const who: Cell[] = [student.studentCode, student.fullName, student.email, student.phone, student.status]
    return mine.length === 0
      ? [[...who, '', '', '', '', '', '', ''] as Cell[]]
      : mine.map((plan) => [...who, plan.status, plan.currency, plan.netFee, plan.paid, plan.outstanding, plan.overdue, plan.nextDueDate] as Cell[])
  })
  return {
    title: 'Fee position of every student',
    fileName: 'students-fees',
    info: [
      ['Students', String(students.length)],
      ['Fee plans', String(plans.length)],
      ['Outstanding', total(plans, (p: FeePlanResponse) => p.outstanding)],
      ['Overdue', total(plans, (p: FeePlanResponse) => p.overdue)],
    ],
    header: ['Student code', 'Name', 'Email', 'Phone', 'Student status', 'Plan status', 'Currency', 'Net fee', 'Paid', 'Outstanding', 'Overdue', 'Next due'],
    rows,
  }
}

export function feePlansReport(plans: FeePlanResponse[], status = ''): Report {
  return {
    title: 'Fee plans',
    fileName: 'fee-plans',
    info: [
      ['Fee plans', String(plans.length)],
      ['Status', status || 'All'],
      ['Net fee', total(plans, (p: FeePlanResponse) => p.netFee)],
      ['Paid', total(plans, (p: FeePlanResponse) => p.paid)],
      ['Outstanding', total(plans, (p: FeePlanResponse) => p.outstanding)],
    ],
    header: ['Student code', 'Student', 'Currency', 'Total fee', 'Discount', 'Net fee', 'Paid', 'Outstanding', 'Overdue', 'Status', 'Next due', 'Next amount'],
    rows: plans.map((p) => [
      p.studentCode, p.studentName, p.currency, p.totalFee, p.discount, p.netFee, p.paid, p.outstanding, p.overdue, p.status,
      p.nextDueDate, p.nextDueAmount,
    ]),
  }
}

export function overdueReport(items: OverdueItemResponse[]): Report {
  return {
    title: 'Overdue instalments',
    fileName: 'overdue-instalments',
    info: [
      ['Instalments', String(items.length)],
      ['Students', String(new Set(items.map((i) => i.studentId)).size)],
      ['Amount overdue', String(sum(items.map((i) => i.amountOverdue)))],
    ],
    header: ['Student code', 'Student', 'Instalment', 'Due date', 'Days overdue', 'Amount overdue'],
    rows: [...items]
      .sort((a, b) => b.daysOverdue - a.daysOverdue)
      .map((i) => [i.studentCode, i.studentName, i.installmentNo, i.dueDate, i.daysOverdue, i.amountOverdue]),
  }
}

/** Who watched a video lesson, how much of it, and how often the face went missing; least watched first. */
export function lessonWatchReport(
  watches: LessonWatch[],
  names: ReadonlyMap<number, string>,
  lesson: { title: string; course?: string | null; monitored?: boolean },
): Report {
  const sorted = [...watches].sort((a, b) => a.watchedPercent - b.watchedPercent)
  const status = (w: LessonWatch) => (w.completed ? 'Completed' : w.watchedPercent > 0 ? 'Watching' : 'Not started')
  return {
    title: 'Video lesson watch report',
    fileName: `watch-${lesson.title.replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase() || 'lesson'}`,
    info: [
      ['Lesson', lesson.title],
      ['Course', lesson.course || '-'],
      ['Students', String(watches.length)],
      ['Completed', String(watches.filter((w) => w.completed).length)],
      ['Not started', String(watches.filter((w) => !w.completed && w.watchedPercent === 0).length)],
      ['Camera monitoring', lesson.monitored === false ? 'Off' : 'On'],
    ],
    header: ['Student', 'Watched %', 'Time played (s)', 'Video length (s)', 'Face away (s)', 'Auto-pauses', 'Status'],
    rows: sorted.map((w) => [
      names.get(w.studentId) ?? `Student #${w.studentId}`, w.watchedPercent, w.playedSeconds, w.durationSeconds, w.faceAwaySeconds,
      w.facePauses, status(w),
    ]),
  }
}
