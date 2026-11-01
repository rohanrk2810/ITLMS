import { readFileSync } from 'node:fs'

import { expect, test, type Download, type Page } from '@playwright/test'
import { strFromU8, unzipSync } from 'fflate'

import { EMPTY_PAGE, signedInAs, userFor, type Handler } from './helpers'

// A 1 x 1 PNG: enough of a logo for the letterhead.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

const BRANDED: Record<string, Handler> = {
  'GET /api/public/branding': () => ({
    name: 'GH Test College',
    tagline: 'Jalgaon',
    primaryColor: null,
    contactEmail: 'office@college.test',
    contactPhone: '0257 1234567',
    website: null,
    address: '1 College Road, Jalgaon',
    signatoryName: null,
    signatoryTitle: null,
    logoUrl: '/api/public/branding/logo',
    faviconUrl: null,
    loginBackgroundUrl: null,
    appBackgroundUrl: null,
    studentHiddenMenu: [],
    uiConfig: '{}',
    version: 1,
  }),
  'GET /api/public/branding/logo': (_request, route) => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }),
}

async function textOf(download: Download) {
  const path = await download.path()
  return readFileSync(path, 'utf8').replace(/^﻿/, '')
}

/** The .xlsx is a zip; read the text cells and the sheet and say which files it holds. */
async function xlsxOf(download: Download) {
  const files = unzipSync(new Uint8Array(readFileSync(await download.path())))
  const text = (name: string) => (files[name] ? strFromU8(files[name]) : '')
  return {
    names: Object.keys(files),
    strings: text('xl/sharedStrings.xml'),
    sheet: text('xl/worksheets/sheet1.xml'),
  }
}

async function choose(page: Page, button: string | RegExp, item: string | RegExp) {
  await page.getByRole('button', { name: button }).click()
  const pending = page.waitForEvent('download')
  await page.getByRole('menuitem', { name: item }).click()
  return pending
}

const csvItem = /CSV \(plain data\)/
const xlsxItem = /Excel \(\.xlsx\)/
const pdfItem = /PDF or print/

const student = (overrides: Record<string, unknown> = {}) => ({
  id: 1, userId: 11, studentCode: 'STU-1', fullName: 'Asha Patil', email: 'a@x.test', phone: '+91 98765 43210',
  dateOfBirth: '2004-05-01', gender: 'FEMALE', highestEducation: 'B.Sc', college: 'Fergusson', graduationYear: 2026,
  addressLine: '12 MG Road', city: 'Pune', state: 'MH', pincode: '411001', guardianName: 'Ramesh Patil',
  guardianPhone: '9800000000', emergencyContact: '9800000001', admissionDate: '2026-10-01', admissionSource: 'WALK_IN',
  status: 'ACTIVE', remarks: null, createdAt: '2026-10-01T00:00:00Z', ...overrides,
})

const lead = (overrides: Record<string, unknown> = {}) => ({
  id: 1, name: 'Ravi Kale', phone: '9800000002', email: null, source: 'WEBSITE', status: 'FOLLOW_UP',
  interestedCourseId: 3, counselorUserId: null, nextFollowUpAt: '2026-11-05T10:30:00Z', overdue: true, notes: 'call back',
  lostReason: null, convertedStudentId: null, convertedAt: null, followupCount: 2, createdAt: '2026-10-20T08:00:00Z',
  ...overrides,
})

test.describe('students: Excel, PDF and CSV', () => {
  const handlers = (extra: Record<string, Handler> = {}): Record<string, Handler> => ({
    ...BRANDED,
    'GET /api/students': () => EMPTY_PAGE,
    'GET /api/students/export': () => [student(), student({ id: 2, studentCode: 'STU-2', fullName: 'Ravi Kale', city: 'Mumbai' })],
    ...extra,
  })

  test('the admin downloads every student as CSV, named for today', async ({ page }) => {
    const api = await signedInAs(page, 'ADMIN', handlers())
    await page.goto('/app/students')
    const download = await choose(page, 'Download all students', csvItem)
    expect(download.suggestedFilename()).toMatch(/^students-\d{4}-\d{2}-\d{2}\.csv$/)
    const lines = (await textOf(download)).split('\r\n')
    expect(lines[0]).toContain('Student code,Name,Email,Phone,Status')
    expect(lines[0]).toContain('Address')
    expect(lines).toHaveLength(3)
    expect(lines[1]).toContain('12 MG Road')
    expect(lines[1]).toContain('+91 98765 43210') // a phone number is not mangled
    expect(api.seen.some((r) => r.path === '/api/students/export')).toBe(true)
  })

  test('the Excel file carries the college letterhead, the title and the students', async ({ page }) => {
    await signedInAs(page, 'COORDINATOR', handlers())
    await page.goto('/app/students')
    const download = await choose(page, 'Download all students', xlsxItem)
    expect(download.suggestedFilename()).toMatch(/^students-\d{4}-\d{2}-\d{2}\.xlsx$/)
    const xlsx = await xlsxOf(download)
    expect(xlsx.names).toContain('xl/worksheets/sheet1.xml')
    expect(xlsx.names.some((n) => n.startsWith('xl/media/'))).toBe(true) // the logo
    for (const text of ['GH Test College', 'Jalgaon', '1 College Road, Jalgaon', 'Students', 'Asha Patil', 'Ravi Kale', '12 MG Road']) {
      expect(xlsx.strings, text).toContain(text)
    }
  })

  test('the PDF is the printed college page: letterhead, rule, title, facts, then the table', async ({ page }) => {
    await signedInAs(page, 'ADMIN', handlers())
    await page.goto('/app/students')
    await page.getByRole('button', { name: 'Download all students' }).click()
    await page.getByRole('menuitem', { name: pdfItem }).click()
    const sheet = page.frameLocator('iframe[title="Report print preview"]')
    await expect(sheet.locator('.name')).toHaveText('GH Test College')
    await expect(sheet.locator('.tagline')).toHaveText('Jalgaon')
    await expect(sheet.locator('img.logo')).toHaveAttribute('src', /\/api\/public\/branding\/logo$/)
    await expect(sheet.locator('hr.rule')).toHaveCount(1)
    await expect(sheet.locator('h1.title')).toHaveText('Students')
    await expect(sheet.locator('table.info th')).toHaveText(['Students', 'Status', 'Search'])
    await expect(sheet.locator('table.info td')).toHaveText(['2', 'All', 'None'])
    await expect(sheet.locator('table.data tbody tr')).toHaveCount(2)
    await expect(sheet.locator('table.data tbody tr').first()).toContainText('Asha Patil')
  })

  test('a student name that is a spreadsheet formula is made harmless in the CSV', async ({ page }) => {
    await signedInAs(page, 'COORDINATOR', handlers({ 'GET /api/students/export': () => [student({ fullName: '=HYPERLINK("http://evil.example","open")' })] }))
    await page.goto('/app/students')
    const csv = await textOf(await choose(page, 'Download all students', csvItem))
    expect(csv).toContain(`"'=HYPERLINK(""http://evil.example"",""open"")"`)
    expect(csv).not.toMatch(/^=|,=HYPERLINK/m)
  })

  test('a name with markup prints as text in the PDF page', async ({ page }) => {
    await signedInAs(page, 'ADMIN', handlers({ 'GET /api/students/export': () => [student({ fullName: '<img src=x onerror=alert(1)>' })] }))
    await page.goto('/app/students')
    await page.getByRole('button', { name: 'Download all students' }).click()
    await page.getByRole('menuitem', { name: pdfItem }).click()
    const sheet = page.frameLocator('iframe[title="Report print preview"]')
    await expect(sheet.locator('table.data tbody tr').first()).toContainText('<img src=x onerror=alert(1)>')
    await expect(sheet.locator('table.data img')).toHaveCount(0)
  })

  test('a trainer does not get the all-students download', async ({ page }) => {
    await signedInAs(page, 'TRAINER', { ...BRANDED, 'GET /api/students': () => EMPTY_PAGE })
    await page.goto('/app/students')
    await expect(page.getByRole('heading', { name: 'Students' })).toBeVisible()
    await expect(page.getByRole('button', { name: /Download all students/ })).toHaveCount(0)
  })

  test('with no students there is nothing to save, and the person is told', async ({ page }) => {
    await signedInAs(page, 'ADMIN', handlers({ 'GET /api/students/export': () => [] }))
    await page.goto('/app/students')
    await page.getByRole('button', { name: 'Download all students' }).click()
    await page.getByRole('menuitem', { name: xlsxItem }).click()
    await expect(page.getByText('There is nothing to download yet.')).toBeVisible()
  })
})

test.describe('other reports', () => {
  test('leads are read page by page, saved, and the download is noted in the audit log', async ({ page }) => {
    const api = await signedInAs(page, 'COORDINATOR', {
      ...BRANDED,
      'GET /api/leads': (request) => {
        const n = Number(request.query.get('page') ?? 0)
        return {
          ...EMPTY_PAGE,
          content: n === 0 ? [lead(), lead({ id: 2, name: 'Meera Joshi' })] : n === 1 ? [lead({ id: 3, name: 'Omkar Rao' })] : [],
          page: n,
          last: n >= 1,
        }
      },
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app/admissions')
    const download = await choose(page, 'Download leads', csvItem)
    expect(download.suggestedFilename()).toMatch(/^leads-\d{4}-\d{2}-\d{2}\.csv$/)
    const lines = (await textOf(download)).split('\r\n')
    expect(lines).toHaveLength(4) // header and three leads from two pages
    expect(lines.join('\n')).toContain('Omkar Rao')
    await expect
      .poll(() => api.seen.find((r) => r.method === 'POST' && r.path === '/api/reports/exports')?.body)
      .toMatchObject({ report: 'LEADS', rows: 3 })
  })

  test('the dashboard figures download as Excel with the letterhead', async ({ page }) => {
    await signedInAs(page, 'ADMIN', {
      ...BRANDED,
      'GET /api/dashboard/summary': () => ({
        generatedAt: '2026-11-01T10:00:00Z', studentsAdmittedTotal: 120, studentsAdmittedLast30Days: 9, enrollmentsTotal: 118,
        enrollmentsLast30Days: 8, revenueCollectedTotal: 900000, revenueCollectedLast30Days: 50000, feesBilledTotal: 1200000,
        installmentsOverdueTotal: 4, overdueAmountTotal: 40000, certificatesIssuedTotal: 30, submissionsEvaluatedTotal: 500,
        quizAttemptsTotal: 700, quizAttemptsPassed: 600, jobsPostedTotal: 6, placementsSelectedTotal: 12,
        attendance: { present: 10, absent: 2, late: 1, excused: 0, percent: 85 },
      }),
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app')
    const xlsx = await xlsxOf(await choose(page, 'Download these figures', xlsxItem))
    expect(xlsx.strings).toContain('GH Test College')
    expect(xlsx.strings).toContain('Institute figures')
    expect(xlsx.strings).toContain('Students admitted (total)')
    expect(xlsx.sheet).toContain('<v>120</v>') // a number stays a number, not text
  })
})

test.describe('student progress report', () => {
  const report = {
    studentId: 30,
    profile: { fullName: 'Asha Patil', studentCode: 'STU-30', email: 'a@x.test', phone: '9800000000', status: 'ACTIVE' },
    generatedAt: '2026-11-01T10:00:00Z',
    indicators: [{ key: 'attendance', label: 'Attendance', percent: 80, band: 'GOOD', detail: '8 of 10 classes' }],
    enrollments: [], courses: null,
    attendance: { percent: 80, attended: 8, counted: 10, absent: 2, excused: 0, byBatch: [] },
    liveClasses: null, tests: null, coding: null, assignments: null,
    suggestions: [{ type: 'A', priority: 1, title: 'Attend more classes', detail: 'Under 85%', link: '/app/live-classes' }],
    unavailable: [],
  }
  const asStudent = (): Record<string, Handler> => ({
    ...BRANDED,
    'GET /api/auth/me': () => userFor('STUDENT'),
    'GET /api/reports/me/progress': () => report,
  })

  test('a student downloads their own report as CSV without it being logged as a staff download', async ({ page }) => {
    const api = await signedInAs(page, 'STUDENT', asStudent())
    await page.goto('/app/my-progress')
    const csv = await textOf(await choose(page, 'Download or print', csvItem))
    expect(csv).toContain('Item,Value')
    expect(csv).toContain('# Indicators')
    expect(csv).toContain('Attendance,80% (8 of 10 classes)')
    expect(api.seen.some((r) => r.path === '/api/reports/exports')).toBe(false)
  })

  test("the printed report opens like a grade card: the student's details in a table under the title", async ({ page }) => {
    await signedInAs(page, 'STUDENT', asStudent())
    await page.goto('/app/my-progress')
    await page.getByRole('button', { name: 'Download or print' }).click()
    await page.getByRole('menuitem', { name: pdfItem }).click()
    const sheet = page.frameLocator('iframe[title="Report print preview"]')
    await expect(sheet.locator('.name')).toHaveText('GH Test College')
    await expect(sheet.locator('h1.title')).toHaveText('Student progress report')
    await expect(sheet.locator('table.info th')).toHaveText(["Student's name", 'Student code', 'Email', 'Phone', 'Status', 'Report made'])
    await expect(sheet.locator('table.info td').first()).toHaveText('Asha Patil')
    await expect(sheet.locator('tr.section')).toContainText(['Indicators', 'Attendance', 'Suggestions'])
    await expect(sheet.locator('table.data')).toContainText('Attend more classes')
  })
})
