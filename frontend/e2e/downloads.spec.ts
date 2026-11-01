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

test.describe('finance desk and the video watch report', () => {
  const desk = (extra: Record<string, Handler> = {}): Record<string, Handler> => ({
    ...BRANDED,
    'GET /api/fees/dashboard': () => ({
      currency: 'INR', totalBilled: 100000, collected: 40000, pending: 60000, overdueAmount: 12000, overdueInstallments: 2,
      studentsWithOverdue: 2, activePlans: 2, settledPlans: 0, collectedThisMonth: 0, methodSplitThisMonth: [],
    }),
    'GET /api/fees/overdue': () => [
      { feePlanId: 10, installmentId: 1, studentId: 1, studentCode: 'STU-1', studentName: 'Asha Patil', installmentNo: 2, dueDate: '2026-10-01', daysOverdue: 31, amountOverdue: 7000 },
      { feePlanId: 11, installmentId: 2, studentId: 2, studentCode: 'STU-2', studentName: 'Ravi Kale', installmentNo: 1, dueDate: '2026-10-20', daysOverdue: 12, amountOverdue: 5000 },
    ],
    'GET /api/fee-plans': (request) => ({
      ...EMPTY_PAGE,
      content: [feePlan(10, 1, 'Asha Patil'), feePlan(11, 2, 'Ravi Kale')].filter((p) => !request.query.get('status') || p.status === request.query.get('status')),
      last: true,
    }),
    'GET /api/students': () => ({ ...EMPTY_PAGE, content: [{ id: 1, studentCode: 'STU-1', fullName: 'Asha Patil', email: 'a@x.test', phone: '+91 98765 43210', status: 'ACTIVE' }, { id: 3, studentCode: 'STU-3', fullName: 'Meera Joshi', email: 'm@x.test', phone: '9800000003', status: 'ACTIVE' }], last: true }),
    'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    ...extra,
  })
  const feePlan = (id: number, studentId: number, name: string) => ({
    id, studentId, studentCode: `STU-${studentId}`, studentName: name, courseId: 1, batchId: 9, currency: 'INR', totalFee: 60000,
    discount: 0, netFee: 60000, paid: 30000, outstanding: 30000, overdue: 6000, status: 'ACTIVE', nextDueDate: '2026-12-01',
    nextDueAmount: 10000, notes: null, installments: [], payments: null,
  })

  test('the finance desk downloads the overdue list as Excel with the letterhead, and notes it', async ({ page }) => {
    const api = await signedInAs(page, 'ADMIN', desk())
    await page.goto('/app/finance')
    const download = await choose(page, 'Download overdue', xlsxItem)
    const xlsx = await xlsxOf(download)
    expect(download.suggestedFilename()).toMatch(/^overdue-instalments-\d{4}-\d{2}-\d{2}\.xlsx$/)
    for (const text of ['GH Test College', 'Overdue instalments', 'Asha Patil', 'Ravi Kale']) expect(xlsx.strings, text).toContain(text)
    expect(xlsx.sheet).toContain('<v>7000</v>')
    await expect.poll(() => api.seen.find((r) => r.method === 'POST' && r.path === '/api/reports/exports')?.body).toMatchObject({ report: 'OVERDUE_FEES', rows: 2 })
  })

  test('the fee plans download follows the status chosen on the page', async ({ page }) => {
    const api = await signedInAs(page, 'COORDINATOR', desk())
    await page.goto('/app/finance')
    await page.getByRole('combobox').selectOption('SETTLED')
    const pending = page.waitForEvent('download', { timeout: 5000 }).catch(() => null)
    await page.getByRole('button', { name: 'Download fee plans' }).click()
    await page.getByRole('menuitem', { name: csvItem }).click()
    await pending
    await expect.poll(() => api.seen.some((r) => r.path === '/api/fee-plans' && r.query.get('status') === 'SETTLED')).toBe(true)
  })

  test('the fee position of every student includes a student who has no plan', async ({ page }) => {
    await signedInAs(page, 'ADMIN', desk())
    await page.goto('/app/finance')
    const csv = await textOf(await choose(page, "Download every student's fees", csvItem))
    const lines = csv.split('\r\n')
    expect(lines[0]).toContain('Student code,Name,Email,Phone')
    expect(lines.some((l) => l.includes('Asha Patil') && l.includes('30000'))).toBe(true)
    expect(lines.some((l) => l.includes('Meera Joshi'))).toBe(true) // no plan, still listed
    expect(csv).toContain('+91 98765 43210')
  })

  test('a person who may only see their own fees has no desk downloads', async ({ page }) => {
    await signedInAs(page, 'STUDENT', { ...BRANDED, 'GET /api/auth/me': () => userFor('STUDENT'), 'GET /api/fee-plans/mine': () => [] })
    await page.goto('/app/finance')
    await expect(page.getByRole('button', { name: 'Download overdue' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: /Download every student/ })).toHaveCount(0)
  })

  test('the video lesson watch report downloads with the lesson, the course and the students named', async ({ page }) => {
    const api = await signedInAs(page, 'TRAINER', {
      ...BRANDED,
      'GET /api/courses/1': () => ({
        course: { id: 1, title: 'Full Stack Java', code: 'JFS' },
        modules: [{ id: 1, courseId: 1, title: 'Module 1', sequenceNo: 1, lessons: [{ id: 3, title: 'Intro to Java', videoMonitoring: true }] }],
        enrolled: false, progress: null,
      }),
      'GET /api/progress/lessons/3/watch-report': () => [
        { studentId: 1, batchId: 9, watchedPercent: 100, playedSeconds: 600, durationSeconds: 600, faceAwaySeconds: 0, facePauses: 0, completed: true },
        { studentId: 2, batchId: 9, watchedPercent: 20, playedSeconds: 120, durationSeconds: 600, faceAwaySeconds: 30, facePauses: 2, completed: false },
      ],
      'GET /api/batches/9/students': () => [{ studentId: 1, studentName: 'Asha Patil' }, { studentId: 2, studentName: 'Ravi Kale' }],
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app/courses/1/lessons/3/watch-report')
    await expect(page.getByText('Who watched: Intro to Java')).toBeVisible()
    await expect(page.getByText('Ravi Kale')).toBeVisible() // names loaded before the file is built
    const xlsx = await xlsxOf(await choose(page, 'Download', xlsxItem))
    for (const text of ['Video lesson watch report', 'Intro to Java', 'Full Stack Java', 'Asha Patil', 'Ravi Kale', 'Completed', 'Watching']) {
      expect(xlsx.strings, text).toContain(text)
    }
    await expect.poll(() => api.seen.find((r) => r.method === 'POST' && r.path === '/api/reports/exports')?.body).toMatchObject({ report: 'WATCH_REPORT', rows: 2 })
  })
})

test.describe('the Users page download', () => {
  const account = (id: number, name: string, extras: string[] = []) =>
    userFor('TRAINER', { id, fullName: name, email: `${name.split(' ')[0].toLowerCase()}@x.test`, extraRoles: extras, profileCode: `EMP-${id}` })

  test('an administrator downloads every account page by page, as Excel with the letterhead', async ({ page }) => {
    const api = await signedInAs(page, 'ADMIN', {
      ...BRANDED,
      'GET /api/users': (request) => {
        const n = Number(request.query.get('page') ?? 0)
        return { ...EMPTY_PAGE, content: n === 0 ? [account(1, 'Ravi Kulkarni', ['COORDINATOR'])] : n === 1 ? [account(2, 'Meera Joshi')] : [], page: n, last: n >= 1 }
      },
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app/users')
    const xlsx = await xlsxOf(await choose(page, 'Download users', xlsxItem))
    for (const text of ['GH Test College', 'User accounts', 'Ravi Kulkarni', 'Meera Joshi', 'COORDINATOR']) expect(xlsx.strings, text).toContain(text)
    await expect.poll(() => api.seen.find((r) => r.method === 'POST' && r.path === '/api/reports/exports')?.body).toMatchObject({ report: 'USERS', rows: 2 })
  })

  test('the PDF is the printed page with the letterhead and the accounts', async ({ page }) => {
    await signedInAs(page, 'ADMIN', {
      ...BRANDED,
      'GET /api/users': () => ({ ...EMPTY_PAGE, content: [account(1, 'Ravi Kulkarni', ['FINANCE'])], last: true }),
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app/users')
    await page.getByRole('button', { name: 'Download users' }).click()
    await page.getByRole('menuitem', { name: pdfItem }).click()
    const sheet = page.frameLocator('iframe[title="Report print preview"]')
    await expect(sheet.locator('.name')).toHaveText('GH Test College')
    await expect(sheet.locator('h1.title')).toHaveText('User accounts')
    await expect(sheet.locator('table.data tbody tr').first()).toContainText('Ravi Kulkarni')
    await expect(sheet.locator('table.data tbody tr').first()).toContainText('FINANCE')
  })

  test('a coordinator may download the list too, as plain data', async ({ page }) => {
    await signedInAs(page, 'COORDINATOR', {
      ...BRANDED,
      'GET /api/users': () => ({ ...EMPTY_PAGE, content: [account(1, 'Ravi Kulkarni')], last: true }),
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app/users')
    const csv = await textOf(await choose(page, 'Download users', csvItem))
    expect(csv.split('\r\n')[0]).toBe('Name,Email,Phone,Main role,Extra duties,Status,Student or trainer code,Last sign-in,Created')
    expect(csv).toContain('Ravi Kulkarni')
    expect(csv).not.toMatch(/password|token/i)
  })

  test('the filter chosen on the page is what is downloaded', async ({ page }) => {
    const api = await signedInAs(page, 'ADMIN', {
      ...BRANDED,
      'GET /api/users': () => ({ ...EMPTY_PAGE, content: [account(1, 'Ravi Kulkarni')], last: true }),
      'POST /api/reports/exports': (_request, route) => route.fulfill({ status: 204 }),
    })
    await page.goto('/app/users')
    await page.getByPlaceholder('Search name, email, phone...').fill('ravi')
    await choose(page, 'Download users', csvItem)
    expect(api.seen.some((r) => r.path === '/api/users' && r.query.get('query') === 'ravi' && r.query.get('page') === '0')).toBe(true)
  })

  test('a person who is not allowed on the Users page cannot reach the download', async ({ page }) => {
    await signedInAs(page, 'TRAINER', { ...BRANDED })
    await page.goto('/app/users')
    await expect(page).toHaveURL(/\/app\/forbidden/)
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
