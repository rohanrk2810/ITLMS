import { expect, test } from '@playwright/test'

import { signedInAs, userFor } from './helpers'

function card(id: number, overrides: Record<string, unknown> = {}) {
  return {
    id,
    cardNo: `STU-2026-${String(id).padStart(4, '0')}`,
    verificationCode: 'K7QMX-2HWRP',
    verificationUrl: 'https://example.test/verify-id/x',
    personType: 'STUDENT',
    personId: 30,
    userId: 7,
    personCode: `STU-${id}`,
    fullName: 'Asha Patil',
    designation: 'Student',
    batchId: 9,
    batchName: 'Morning Batch',
    hasPhoto: false,
    issuedOn: '2026-11-01',
    validUntil: '2027-04-30',
    status: 'ISSUED',
    revokedReason: null,
    ...overrides,
  }
}

test.describe('ID cards: staff', () => {
  test('the admin sees every card with its status, and can print the ones in force', async ({ page }) => {
    await signedInAs(page, 'ADMIN', {
      'GET /api/id-cards': () => [
        card(1),
        card(2, { fullName: 'Ravi Kulkarni', personType: 'TRAINER', designation: 'Trainer', cardNo: 'TRN-2026-0002' }),
        card(3, { fullName: 'Meera Joshi', status: 'REVOKED', revokedReason: 'Lost' }),
      ],
    })
    await page.goto('/app/id-cards')
    await expect(page.getByRole('heading', { name: 'ID cards' })).toBeVisible()
    await expect(page.getByText('Asha Patil')).toBeVisible()
    await expect(page.getByText('Ravi Kulkarni')).toBeVisible()
    await expect(page.getByText('Meera Joshi')).toBeVisible()
    await expect(page.getByText('REVOKED')).toBeVisible()
    // A withdrawn card cannot be selected for printing.
    await expect(page.getByRole('checkbox', { name: 'Select Meera Joshi' })).toBeDisabled()
    await expect(page.getByRole('checkbox', { name: 'Select Asha Patil' })).toBeEnabled()
    await expect(page.getByRole('button', { name: /Print selected \(0\)/ })).toBeDisabled()
    await page.getByRole('checkbox', { name: 'Select Asha Patil' }).click()
    await expect(page.getByRole('button', { name: /Print selected \(1\)/ })).toBeEnabled()
  })

  test('searching asks the server for that name', async ({ page }) => {
    const api = await signedInAs(page, 'COORDINATOR', { 'GET /api/id-cards': () => [] })
    await page.goto('/app/id-cards')
    await expect(page.getByText('No cards yet')).toBeVisible()
    await page.getByPlaceholder('Search name, card number or code').fill('patil')
    await expect.poll(() => api.seen.some((r) => r.path === '/api/id-cards' && r.query.get('q') === 'patil')).toBe(true)
  })

  test('a staff card is issued with the name and title that were typed', async ({ page }) => {
    const api = await signedInAs(page, 'ADMIN', {
      'GET /api/id-cards': () => [],
      'POST /api/id-cards': (request) => card(9, { personType: 'STAFF', fullName: (request.body as { fullName: string }).fullName, cardNo: 'STF-2026-0009' }),
    })
    await page.goto('/app/id-cards')
    await page.getByRole('button', { name: 'Issue a card' }).click()
    await page.getByRole('tab', { name: 'Staff' }).click()
    const issue = page.getByRole('button', { name: 'Issue card' })
    await expect(issue).toBeDisabled() // no name yet
    await page.getByLabel('Name').fill('Meera Joshi')
    await page.getByLabel('Title (for example Accountant)').fill('Accountant')
    await expect(issue).toBeEnabled()
    await issue.click()
    await expect(page.getByText('Card STF-2026-0009 issued')).toBeVisible()
    const posted = api.seen.find((r) => r.method === 'POST' && r.path === '/api/id-cards')
    expect(posted?.body).toMatchObject({ personType: 'STAFF', fullName: 'Meera Joshi', designation: 'Accountant' })
  })

  test('a refusal from the server is shown to the person issuing the card', async ({ page }) => {
    await signedInAs(page, 'ADMIN', {
      'GET /api/id-cards': () => [],
      'POST /api/id-cards': (_req, route) =>
        route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'A student who has dropped out cannot be given an ID card.' }),
        }),
    })
    await page.goto('/app/id-cards')
    await page.getByRole('button', { name: 'Issue a card' }).click()
    await page.getByRole('tab', { name: 'Staff' }).click()
    await page.getByLabel('Name').fill('Someone')
    await page.getByRole('button', { name: 'Issue card' }).click()
    await expect(page.getByText('A student who has dropped out cannot be given an ID card.')).toBeVisible()
  })

  test('withdrawing a card needs a reason', async ({ page }) => {
    const api = await signedInAs(page, 'ADMIN', {
      'GET /api/id-cards': () => [card(1)],
      'POST /api/id-cards/1/revoke': () => card(1, { status: 'REVOKED', revokedReason: 'Lost' }),
    })
    await page.goto('/app/id-cards')
    await page.getByRole('button', { name: 'Withdraw' }).click()
    const confirm = page.getByRole('button', { name: 'Withdraw the card' })
    await expect(confirm).toBeDisabled()
    await page.getByLabel(/Reason/).fill('Lost')
    await confirm.click()
    await expect(page.getByText('Card withdrawn')).toBeVisible()
    expect(api.seen.find((r) => r.path === '/api/id-cards/1/revoke')?.body).toEqual({ reason: 'Lost' })
  })
})

test.describe('ID cards: the person it belongs to', () => {
  test('a student sees their own card and can download it', async ({ page }) => {
    await signedInAs(page, 'STUDENT', {
      'GET /api/auth/me': () => userFor('STUDENT'),
      'GET /api/id-cards/me': () => [card(1)],
    })
    await page.goto('/app/id-cards')
    await expect(page.getByRole('heading', { name: 'My ID card' })).toBeVisible()
    await expect(page.getByText('Asha Patil')).toBeVisible()
    await expect(page.getByText('STU-2026-0001', { exact: false })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible()
    // The staff-only controls are not on this page.
    await expect(page.getByRole('button', { name: 'Issue a card' })).toHaveCount(0)
  })

  test('a withdrawn card shows why, and cannot be downloaded', async ({ page }) => {
    await signedInAs(page, 'STUDENT', {
      'GET /api/auth/me': () => userFor('STUDENT'),
      'GET /api/id-cards/me': () => [card(1, { status: 'REVOKED', revokedReason: 'Replaced by a new card' })],
    })
    await page.goto('/app/id-cards')
    await expect(page.getByText('Replaced by a new card')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Download' })).toHaveCount(0)
  })

  test('with no card yet, the student is told to ask the office', async ({ page }) => {
    await signedInAs(page, 'STUDENT', { 'GET /api/auth/me': () => userFor('STUDENT'), 'GET /api/id-cards/me': () => [] })
    await page.goto('/app/id-cards')
    await expect(page.getByText(/do not have an ID card yet/)).toBeVisible()
  })
})
