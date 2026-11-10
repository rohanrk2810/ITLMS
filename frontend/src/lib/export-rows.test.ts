import { describe, expect, it } from 'vitest'

import type { AttemptResultResponse } from '@/api/assessments'
import type { CourseRequestResponse } from '@/api/course-requests'
import type { LessonWatch } from '@/api/courses'
import type { FeePlanResponse, OverdueItemResponse } from '@/api/finance'
import type { StudentSummaryResponse } from '@/api/students'
import type { LeadResponse } from '@/api/leads'
import type { StudentProgressReport } from '@/api/progress'
import type { DashboardSummaryResponse } from '@/api/reporting'
import type { StudentResponse } from '@/api/students'
import type { UserResponse } from '@/api/types'

import {
  attendanceReport,
  courseRequestsReport,
  dashboardReport,
  feePlansReport,
  feePositionReport,
  leadsReport,
  lessonWatchReport,
  overdueReport,
  progressReport,
  resultsReport,
  studentsReport,
  usersReport,
} from './export-rows'
import type { Report } from './report'

/** Every row must be as wide as the header, or the columns of a spreadsheet slide. */
function expectRectangular(report: Report) {
  for (const row of report.rows) {
    expect(row).toHaveLength(report.header.length)
  }
}

const student = {
  studentCode: 'STU-1', fullName: 'Asha Patil', email: 'a@x.test', phone: '+91 98765 43210', status: 'ACTIVE',
  dateOfBirth: '2004-05-01', gender: 'FEMALE', highestEducation: 'B.Sc', college: 'Fergusson', graduationYear: 2026,
  addressLine: '12 MG Road', city: 'Pune', state: 'MH', pincode: '411001', guardianName: 'Ramesh Patil',
  guardianPhone: '9800000000', emergencyContact: '9800000001', admissionDate: '2026-10-01', admissionSource: 'WALK_IN',
  remarks: null,
} as unknown as StudentResponse

describe('studentsReport', () => {
  it('carries the whole profile, address and guardian included', () => {
    const report = studentsReport([student])
    expectRectangular(report)
    expect(report.header).toEqual(expect.arrayContaining(['Address', 'City', 'Guardian', 'Guardian phone', 'Emergency contact']))
    expect(report.rows[0]).toEqual(expect.arrayContaining(['STU-1', 'Asha Patil', '12 MG Road', 'Ramesh Patil']))
  })

  it('says how many students and which filter it covers', () => {
    const report = studentsReport([student], { status: 'ACTIVE', query: 'patil' })
    expect(report.title).toBe('Students')
    expect(report.info).toContainEqual(['Students', '1'])
    expect(report.info).toContainEqual(['Status', 'ACTIVE'])
    expect(report.info).toContainEqual(['Search', 'patil'])
    expect(studentsReport([]).info).toContainEqual(['Status', 'All'])
  })

  it('no students is an empty report, not an error', () => {
    expect(studentsReport([]).rows).toEqual([])
  })
})

describe('leadsReport', () => {
  it('writes overdue as Yes or No and follows the header', () => {
    const lead = {
      name: 'Ravi', phone: '9800000002', email: null, source: 'WEBSITE', status: 'FOLLOW_UP', interestedCourseId: 3,
      nextFollowUpAt: '2026-11-05T10:30:00Z', overdue: true, followupCount: 2, notes: 'call back', lostReason: null,
      convertedStudentId: null, convertedAt: null, createdAt: '2026-10-20T08:00:00Z',
    } as unknown as LeadResponse
    const report = leadsReport([lead])
    expectRectangular(report)
    expect(report.rows[0][report.header.indexOf('Overdue')]).toBe('Yes')
    expect(report.rows[0][report.header.indexOf('Next follow-up')]).toBe('2026-11-05 10:30')
  })
})

describe('attendanceReport', () => {
  const rows = [
    { studentId: 1, studentName: 'Asha', attendedSessions: 9, totalSessions: 10, attendancePercent: 90, belowThreshold: false },
    { studentId: 2, studentName: 'Ravi', attendedSessions: 3, totalSessions: 10, attendancePercent: 30, belowThreshold: true },
  ]

  it('lists each student with the percentage and whether it is under the limit', () => {
    const report = attendanceReport(rows, { code: 'JFS-B1', course: 'Java', trainer: 'Ravi K' })
    expectRectangular(report)
    expect(report.rows[1].at(-1)).toBe('Yes')
    expect(report.rows[0].at(-1)).toBe('No')
  })

  it('names the batch, the course and the trainer, and counts who is below the limit', () => {
    const report = attendanceReport(rows, { code: 'JFS-B1', course: 'Java', trainer: null })
    expect(report.info).toContainEqual(['Batch', 'JFS-B1'])
    expect(report.info).toContainEqual(['Course', 'Java'])
    expect(report.info).toContainEqual(['Trainer', 'Not assigned'])
    expect(report.info).toContainEqual(['Below the limit', '1'])
    expect(report.fileName).toBe('attendance-jfs-b1')
  })
})

describe('resultsReport', () => {
  const base = {
    studentName: 'Asha', attemptNo: 1, status: 'SUBMITTED', startedAt: '2026-11-01T10:00:00Z', submittedAt: '2026-11-01T10:30:00Z',
    resultVisible: true, score: 8, totalMarks: 10, percentage: 80, passed: true, passPercentage: 40, violationCount: 1,
    cameraEventCount: 0, terminatedReason: null, typingReview: 'Most of the code was pasted',
  } as unknown as AttemptResultResponse

  it('shows marks and the pass result when they are visible, and the typing review', () => {
    const report = resultsReport([base], { title: 'Week 1 test' })
    expectRectangular(report)
    expect(report.rows[0]).toEqual(expect.arrayContaining([8, 10, 80, 'Yes', 'Most of the code was pasted']))
    expect(report.info).toContainEqual(['Test', 'Week 1 test'])
    expect(report.info).toContainEqual(['Passed', '1'])
  })

  it('leaves the marks blank while the result is not released, so the sheet never leaks it', () => {
    const report = resultsReport([{ ...base, resultVisible: false }], { title: 'Week 1 test' })
    expect(report.rows[0][report.header.indexOf('Score')]).toBe('')
    expect(report.rows[0][report.header.indexOf('Percentage')]).toBe('')
    expect(report.rows[0][report.header.indexOf('Passed')]).toBe('')
    expect(report.info).toContainEqual(['Not yet released', '1'])
    expect(report.info).toContainEqual(['Passed', '0'])
  })

  it('the file name is made from the test title and is safe', () => {
    expect(resultsReport([], { title: 'Java / OOP: Final (2026)!' }).fileName).toBe('results-java-oop-final-2026')
    expect(resultsReport([], { title: '***' }).fileName).toBe('results-test')
  })
})

describe('dashboardReport', () => {
  it('is a figure and a value on every row', () => {
    const summary = {
      generatedAt: '2026-11-01T10:00:00Z', studentsAdmittedTotal: 120, studentsAdmittedLast30Days: 9, enrollmentsTotal: 118,
      enrollmentsLast30Days: 8, revenueCollectedTotal: 900000, revenueCollectedLast30Days: 50000, feesBilledTotal: 1200000,
      installmentsOverdueTotal: 4, overdueAmountTotal: 40000, certificatesIssuedTotal: 30, submissionsEvaluatedTotal: 500,
      quizAttemptsTotal: 700, quizAttemptsPassed: 600, jobsPostedTotal: 6, placementsSelectedTotal: 12,
      attendance: { present: 10, absent: 2, late: 1, excused: 0, percent: 85 },
    } as DashboardSummaryResponse
    const report = dashboardReport(summary)
    expectRectangular(report)
    expect(report.rows).toContainEqual(['Students admitted (total)', 120])
    expect(report.rows).toContainEqual(['Amount overdue', 40000])
    expect(report.rows).toContainEqual(['Attendance %', 85])
    expect(report.info).toContainEqual(['Figures as of', '2026-11-01 10:00'])
  })
})

describe('progressReport', () => {
  const data = {
    studentId: 1,
    profile: { fullName: 'Asha Patil', studentCode: 'STU-1', email: 'a@x.test', phone: '9800000000', status: 'ACTIVE' },
    generatedAt: '2026-11-01T10:00:00Z',
    indicators: [
      { key: 'attendance', label: 'Attendance', percent: 80, band: 'GOOD', detail: '8 of 10' },
      { key: 'tests', label: 'Tests', percent: null, band: 'NONE', detail: 'none taken' },
    ],
    enrollments: [], courses: null,
    attendance: { percent: 80, attended: 8, counted: 10, absent: 2, excused: 0, byBatch: [] },
    liveClasses: null, tests: null, coding: null, assignments: null,
    suggestions: [
      { type: 'TEST', priority: 2, title: 'Retake the quiz', detail: 'Below the pass mark', link: '/app/assessments' },
      { type: 'ATTENDANCE', priority: 1, title: 'Attend more classes', detail: 'Under 85%', link: '/app/live-classes' },
    ],
    unavailable: ['coding'],
  } as unknown as StudentProgressReport

  it("puts the student's details at the top, like a grade card, and the sections below", () => {
    const report = progressReport(data)
    expectRectangular(report)
    expect(report.info).toContainEqual(["Student's name", 'Asha Patil'])
    expect(report.info).toContainEqual(['Student code', 'STU-1'])
    expect(JSON.stringify(report.rows)).toContain('# Indicators')
    expect(report.fileName).toBe('progress-stu-1')
  })

  it('a number with nothing behind it reads "no data", never 0%', () => {
    const report = progressReport(data)
    expect(report.rows).toContainEqual(['Tests', 'no data (none taken)'])
    expect(report.rows).toContainEqual(['Attendance', '80% (8 of 10)'])
  })

  it('lists the most urgent suggestion first and says what could not be loaded', () => {
    const report = progressReport(data)
    const first = report.rows.findIndex((r) => String(r[0]).startsWith('1. '))
    const second = report.rows.findIndex((r) => String(r[0]).startsWith('2. '))
    expect(first).toBeGreaterThan(-1)
    expect(first).toBeLessThan(second)
    expect(report.rows).toContainEqual(['coding', 'unavailable'])
  })
})

const plan = (id: number, studentId: number, over: Partial<FeePlanResponse> = {}) =>
  ({
    id, studentId, studentCode: `STU-${studentId}`, studentName: `Student ${studentId}`, currency: 'INR', totalFee: 60000,
    discount: 5000, netFee: 55000, paid: 20000, outstanding: 35000, overdue: 10000, status: 'ACTIVE', nextDueDate: '2026-12-01',
    nextDueAmount: 10000, ...over,
  }) as unknown as FeePlanResponse

describe('feePositionReport', () => {
  const students = [
    { id: 1, studentCode: 'STU-1', fullName: 'Asha Patil', email: 'a@x.test', phone: '+91 98765 43210', status: 'ACTIVE' },
    { id: 2, studentCode: 'STU-2', fullName: 'Ravi Kale', email: 'r@x.test', phone: '9800000002', status: 'ACTIVE' },
  ] as unknown as StudentSummaryResponse[]

  it('gives a student without any plan a row of their own, with the fee columns empty', () => {
    const report = feePositionReport(students, [plan(10, 1)])
    expectRectangular(report)
    expect(report.rows).toHaveLength(2)
    expect(report.rows[0][report.header.indexOf('Outstanding')]).toBe(35000)
    expect(report.rows[1][report.header.indexOf('Outstanding')]).toBe('')
    expect(report.rows[1][1]).toBe('Ravi Kale')
  })

  it('a student with two plans gets two rows', () => {
    expect(feePositionReport(students, [plan(10, 1), plan(11, 1, { status: 'SETTLED' })]).rows).toHaveLength(3)
  })

  it('adds the money up in one currency, and does not add rupees to dollars', () => {
    const same = feePositionReport(students, [plan(10, 1), plan(11, 2)])
    expect(same.info).toContainEqual(['Outstanding', '70000'])
    expect(same.info).toContainEqual(['Overdue', '20000'])
    const mixed = feePositionReport(students, [plan(10, 1), plan(11, 2, { currency: 'USD' })])
    expect(mixed.info).toContainEqual(['Outstanding', 'mixed currencies'])
  })
})

describe('feePlansReport', () => {
  it('lists each plan with its figures and says which status it covers', () => {
    const report = feePlansReport([plan(10, 1), plan(11, 2, { status: 'SETTLED', outstanding: 0 })], 'ACTIVE')
    expectRectangular(report)
    expect(report.title).toBe('Fee plans')
    expect(report.info).toContainEqual(['Status', 'ACTIVE'])
    expect(report.info).toContainEqual(['Fee plans', '2'])
    expect(report.info).toContainEqual(['Outstanding', '35000'])
    expect(feePlansReport([]).info).toContainEqual(['Status', 'All'])
  })
})

describe('overdueReport', () => {
  const item = (n: number, studentId: number, days: number, amount: number) =>
    ({ feePlanId: n, installmentId: n, studentId, studentCode: `STU-${studentId}`, studentName: `Student ${studentId}`, installmentNo: n, dueDate: '2026-10-01', daysOverdue: days, amountOverdue: amount }) as OverdueItemResponse

  it('shows the longest overdue first, and counts students once', () => {
    const report = overdueReport([item(1, 1, 5, 1000), item(2, 1, 40, 2000), item(3, 2, 12, 500)])
    expectRectangular(report)
    expect(report.rows.map((r) => r[report.header.indexOf('Days overdue')])).toEqual([40, 12, 5])
    expect(report.info).toContainEqual(['Instalments', '3'])
    expect(report.info).toContainEqual(['Students', '2'])
    expect(report.info).toContainEqual(['Amount overdue', '3500'])
  })
})

describe('lessonWatchReport', () => {
  const watch = (studentId: number, percent: number, completed = false) =>
    ({ studentId, batchId: 9, watchedPercent: percent, playedSeconds: percent * 6, durationSeconds: 600, faceAwaySeconds: 12, facePauses: 1, completed }) as LessonWatch

  it('puts the least watched first and names each student, or numbers one it cannot name', () => {
    const report = lessonWatchReport([watch(1, 100, true), watch(2, 0), watch(3, 40)], new Map([[1, 'Asha Patil'], [2, 'Ravi Kale']]), {
      title: 'Intro to Java', course: 'Full Stack Java',
    })
    expectRectangular(report)
    expect(report.rows.map((r) => r[0])).toEqual(['Ravi Kale', 'Student #3', 'Asha Patil'])
    expect(report.rows.map((r) => r.at(-1))).toEqual(['Not started', 'Watching', 'Completed'])
    expect(report.info).toContainEqual(['Lesson', 'Intro to Java'])
    expect(report.info).toContainEqual(['Completed', '1'])
    expect(report.info).toContainEqual(['Not started', '1'])
    expect(report.fileName).toBe('watch-intro-to-java')
  })

  it('says when the camera was not watching, so zero face figures are not misread', () => {
    const off = lessonWatchReport([watch(1, 50)], new Map(), { title: 'L', monitored: false })
    expect(off.info).toContainEqual(['Camera monitoring', 'Off'])
  })
})

describe('usersReport', () => {
  const user = (over: Record<string, unknown>) =>
    ({
      id: 1, fullName: 'Ravi Kulkarni', email: 'r@x.test', phone: '9800000001', role: 'TRAINER', roleDisplayName: 'Trainer',
      status: 'ACTIVE', profileCode: 'EMP-3', lastLoginAt: '2026-11-01T08:15:00Z', createdAt: '2026-09-02T00:00:00Z',
      extraRoles: ['COORDINATOR', 'FINANCE'], hiddenMenu: null, ...over,
    }) as unknown as UserResponse

  it('lists each account with its main role and the extra duties it holds', () => {
    const report = usersReport([user({}), user({ id: 2, fullName: 'Asha Patil', role: 'STUDENT', roleDisplayName: 'Student', extraRoles: [], status: 'INACTIVE', lastLoginAt: null })])
    expectRectangular(report)
    expect(report.title).toBe('User accounts')
    expect(report.rows[0]).toEqual(expect.arrayContaining(['Ravi Kulkarni', 'Trainer', 'COORDINATOR, FINANCE', 'EMP-3', '2026-11-01 08:15']))
    expect(report.rows[1][report.header.indexOf('Extra duties')]).toBe('')
    expect(report.rows[1][report.header.indexOf('Last sign-in')]).toBe('')
  })

  it('counts the accounts that can sign in, and says which filter it covers', () => {
    const report = usersReport([user({}), user({ id: 2, status: 'BLOCKED' })], { role: 'TRAINER', query: 'ravi' })
    expect(report.info).toContainEqual(['Accounts', '2'])
    expect(report.info).toContainEqual(['Active', '1'])
    expect(report.info).toContainEqual(['Role', 'TRAINER'])
    expect(report.info).toContainEqual(['Search', 'ravi'])
  })

  it('carries nothing secret: no hash, token or menu setting is in any column', () => {
    const report = usersReport([user({ passwordHash: 'x', hiddenMenu: ['/app/files'] })])
    const text = JSON.stringify([report.header, report.rows]).toLowerCase()
    expect(text).not.toContain('password')
    expect(text).not.toContain('token')
    expect(text).not.toContain('/app/files')
  })

  it('an older answer without the duties field still lists the account', () => {
    expect(usersReport([user({ extraRoles: undefined })]).rows[0][4]).toBe('')
  })
})

describe('courseRequestsReport', () => {
  const request = (over: Partial<CourseRequestResponse>): CourseRequestResponse => ({
    id: 1, status: 'PENDING', studentId: 1, studentCode: 'STU-1', studentName: 'Asha Patil', studentEmail: 'a@x.in', studentPhone: '9000000001',
    courseId: 5, courseCode: 'JFS', courseTitle: 'Java Full Stack', preferredBatchId: null, message: null, decidedBy: null,
    decidedByName: null, decidedAt: null, decisionNote: null, approvedBatchId: null, enrollmentId: null, createdAt: '2026-11-01T10:00:00Z',
    ...over,
  })
  const requests = [
    request({ id: 1, status: 'APPROVED', decidedByName: 'Ravi Admin', decidedAt: '2026-11-02T09:30:00Z', approvedBatchId: 3 }),
    request({ id: 2, status: 'APPROVED', decidedByName: 'Ravi Admin', decidedAt: '2026-11-03T09:30:00Z', approvedBatchId: 3 }),
    request({ id: 3, status: 'REJECTED', decidedByName: 'Meera Coordinator', decidedAt: '2026-11-03T11:00:00Z', decisionNote: 'Batch is full' }),
    request({ id: 4, status: 'REJECTED', decidedByName: 'Ravi Admin', decisionNote: 'Fee not paid' }),
    request({ id: 5, status: 'PENDING' }),
    request({ id: 6, status: 'CANCELLED' }),
  ]

  it('counts the requests by outcome', () => {
    const info = Object.fromEntries(courseRequestsReport(requests).info)

    expect(info.Requests).toBe('6')
    expect(info.Approved).toBe('2')
    expect(info['Not approved']).toBe('2')
    expect(info.Waiting).toBe('1')
    expect(info['Withdrawn by the student']).toBe('1')
  })

  it('says how many each person approved and turned down, the busiest first', () => {
    const info = courseRequestsReport(requests).info.filter(([label]) => label.startsWith('Decided by'))

    expect(info).toEqual([
      ['Decided by Ravi Admin', '2 approved, 1 not approved'],
      ['Decided by Meera Coordinator', '0 approved, 1 not approved'],
    ])
  })

  it('has a row for each request with who decided it, when, why and the batch', () => {
    const report = courseRequestsReport(requests)

    expect(report.rows).toHaveLength(6)
    expect(report.header).toContain('Decided by')
    const approved = report.rows[0]
    expect(approved).toContain('Approved')
    expect(approved).toContain('Ravi Admin')
    expect(approved).toContain('2026-11-02 09:30')
    expect(approved).toContain('Batch 3')
    expect(report.rows[2]).toContain('Batch is full')
    expect(report.rows[2]).toContain('Not approved')
  })

  it('no requests is an empty report, not an error', () => {
    const report = courseRequestsReport([])
    expect(report.rows).toEqual([])
    expect(Object.fromEntries(report.info).Requests).toBe('0')
  })
})
