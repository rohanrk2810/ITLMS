import { describe, expect, it } from 'vitest'

import type { AttemptResultResponse } from '@/api/assessments'
import type { LeadResponse } from '@/api/leads'
import type { StudentProgressReport } from '@/api/progress'
import type { DashboardSummaryResponse } from '@/api/reporting'
import type { StudentResponse } from '@/api/students'

import { attendanceReport, dashboardReport, leadsReport, progressReport, resultsReport, studentsReport } from './export-rows'
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
