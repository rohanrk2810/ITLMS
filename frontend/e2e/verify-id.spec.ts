import { expect, test } from '@playwright/test'

import { mockApi } from './helpers'

const verification = (status: string) => ({
  cardNo: 'STU-2026-0042',
  status,
  fullName: 'Asha Patil',
  personType: 'STUDENT',
  designation: 'Student',
  validUntil: '2027-04-30',
  issuedBy: 'Test Institute',
})

test.describe('public ID card check (the page the QR code opens)', () => {
  test('a valid card shows the name, role and validity, and nothing private', async ({ page }) => {
    const api = await mockApi(page, { 'GET /api/id-cards/verify/STU-2026-0042': () => verification('VALID') })
    await page.goto('/verify-id/STU-2026-0042?code=K7QMX-2HWRP')
    await expect(page.getByText('This card is valid.')).toBeVisible()
    await expect(page.getByText('Asha Patil')).toBeVisible()
    await expect(page.getByText('Issued by Test Institute')).toBeVisible()
    expect(api.seen.find((r) => r.path.includes('/verify/'))?.query.get('code')).toBe('K7QMX-2HWRP')
  })

  test('works without signing in', async ({ page }) => {
    await mockApi(page, { 'GET /api/id-cards/verify/STU-2026-0042': () => verification('VALID') })
    await page.goto('/verify-id/STU-2026-0042?code=K7QMX-2HWRP')
    await expect(page).toHaveURL(/verify-id/)
    await expect(page).not.toHaveURL(/login/)
  })

  test('a withdrawn card says so', async ({ page }) => {
    await mockApi(page, { 'GET /api/id-cards/verify/STU-2026-0042': () => verification('REVOKED') })
    await page.goto('/verify-id/STU-2026-0042?code=K7QMX-2HWRP')
    await expect(page.getByText('This card has been withdrawn.')).toBeVisible()
  })

  test('an expired card says so', async ({ page }) => {
    await mockApi(page, { 'GET /api/id-cards/verify/STU-2026-0042': () => verification('EXPIRED') })
    await page.goto('/verify-id/STU-2026-0042?code=K7QMX-2HWRP')
    await expect(page.getByText('This card has expired.')).toBeVisible()
  })

  test('a wrong code is not confirmed', async ({ page }) => {
    await mockApi(page, {
      'GET /api/id-cards/verify/STU-2026-0042': (_req, route) =>
        route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'No ID card matches that number and code.' }) }),
    })
    await page.goto('/verify-id/STU-2026-0042?code=WRONG')
    await expect(page.getByText('No ID card matches that number and code.')).toBeVisible()
    await expect(page.getByText('This card is valid.')).toHaveCount(0)
  })

  test('without a code it asks for the card to be scanned again and calls nothing', async ({ page }) => {
    const api = await mockApi(page)
    await page.goto('/verify-id/STU-2026-0042')
    await expect(page.getByText('The code is missing')).toBeVisible()
    expect(api.seen.filter((r) => r.path.includes('/verify/'))).toHaveLength(0)
  })
})
