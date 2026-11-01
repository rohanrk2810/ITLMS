import { expect, test } from '@playwright/test'

import { EMPTY_PAGE, signedInAs, userFor, type Handler } from './helpers'

const trainerWith = (extraRoles: string[]) => userFor('TRAINER', { id: 7, fullName: 'Ravi Kulkarni', profileId: 3, extraRoles })

const nav = (page: import('@playwright/test').Page) => page.getByRole('navigation').first()

test.describe('a trainer who also holds the coordinator duty', () => {
  test('sees the coordinator pages and the trainer pages together', async ({ page }) => {
    await signedInAs(page, 'TRAINER', {}, { extraRoles: ['COORDINATOR'] })
    await page.goto('/app')
    for (const label of ['Admissions', 'Users', 'Course requests', 'Batches', 'Live classes', 'Students']) {
      await expect(nav(page).getByRole('link', { name: label, exact: true }), label).toBeVisible()
    }
    // A duty is not a promotion: the admin-only pages stay closed.
    await expect(nav(page).getByRole('link', { name: 'Customize', exact: true })).toHaveCount(0)
    await expect(nav(page).getByRole('link', { name: 'Monitoring', exact: true })).toHaveCount(0)
  })

  test('a trainer without the duty does not see the coordinator pages, and cannot open them by address', async ({ page }) => {
    await signedInAs(page, 'TRAINER')
    await page.goto('/app')
    await expect(nav(page).getByRole('link', { name: 'Batches', exact: true })).toBeVisible()
    await expect(nav(page).getByRole('link', { name: 'Admissions', exact: true })).toHaveCount(0)
    await page.goto('/app/admissions')
    await expect(page).toHaveURL(/\/app\/forbidden/)
  })

  test('can open the coordinator page and use its download', async ({ page }) => {
    await signedInAs(page, 'TRAINER', { 'GET /api/leads': () => EMPTY_PAGE }, { extraRoles: ['COORDINATOR'] })
    await page.goto('/app/admissions')
    await expect(page.getByRole('heading', { name: 'Admissions' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Download leads' })).toBeVisible()
  })

  test('the user menu says both duties', async ({ page }) => {
    await signedInAs(page, 'TRAINER', {}, { extraRoles: ['COORDINATOR'], roleDisplayName: 'Trainer' })
    await page.goto('/app')
    await page.getByRole('button', { name: /Ravi|Test/ }).first().click()
    await expect(page.getByText('Trainer + Coordinator')).toBeVisible()
  })
})

test.describe('a change of duties reaches the person at once', () => {
  test('a duty given while someone is working shows up on their next request, without signing in again', async ({ page }) => {
    let granted = false
    let stale = true // the gateway answers the first request after the grant with "your duties changed"
    const api = await signedInAs(page, 'TRAINER', {
      'POST /api/auth/refresh': () => ({
        ...{ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 1800, mustChangePassword: false },
        user: trainerWith(granted ? ['COORDINATOR'] : []),
      }),
      'GET /api/announcements': (_request, route) => {
        if (granted && stale) {
          stale = false
          return route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'PERMISSIONS_CHANGED', message: 'Your duties changed. Renewing your session.' }),
          })
        }
        return EMPTY_PAGE
      },
    })
    await page.goto('/app')
    await expect(nav(page).getByRole('link', { name: 'Admissions', exact: true })).toHaveCount(0)

    granted = true // the administrator ticks Coordinator for Ravi
    await nav(page).getByRole('link', { name: 'Announcements', exact: true }).click()

    await expect(nav(page).getByRole('link', { name: 'Admissions', exact: true })).toBeVisible()
    expect(api.seen.filter((r) => r.path === '/api/announcements')).toHaveLength(2) // refused once, then repeated
    expect(api.seen.filter((r) => r.path === '/api/auth/refresh').length).toBeGreaterThanOrEqual(2) // sign-in, then the renewal
  })

  test('a duty withdrawn takes the pages away on the next request, and the address no longer opens', async ({ page }) => {
    let withdrawn = false
    let stale = true
    await signedInAs(page, 'TRAINER', {
      'POST /api/auth/refresh': () => ({
        accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 1800, mustChangePassword: false,
        user: trainerWith(withdrawn ? [] : ['COORDINATOR']),
      }),
      'GET /api/announcements': (_request, route) => {
        if (withdrawn && stale) {
          stale = false
          return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'PERMISSIONS_CHANGED', message: 'x' }) })
        }
        return EMPTY_PAGE
      },
      'GET /api/leads': () => EMPTY_PAGE,
    })
    await page.goto('/app')
    await expect(nav(page).getByRole('link', { name: 'Admissions', exact: true })).toBeVisible()

    withdrawn = true // the administrator takes the duty back
    await nav(page).getByRole('link', { name: 'Announcements', exact: true }).click()

    await expect(nav(page).getByRole('link', { name: 'Admissions', exact: true })).toHaveCount(0)
    await page.goto('/app/admissions')
    await expect(page).toHaveURL(/\/app\/forbidden/)
  })
})

test.describe('the administrator gives and takes duties on the Users page', () => {
  const usersPage = (users: Record<string, unknown>[]) => ({ ...EMPTY_PAGE, content: users, totalElements: users.length })

  test('ticks Coordinator for a trainer, and the row shows the extra duty', async ({ page }) => {
    let extras: string[] = []
    const handlers: Record<string, Handler> = {
      'GET /api/users': () => usersPage([trainerWith(extras)]),
      'PUT /api/users/7/duties': (request) => {
        extras = (request.body as { extraRoles: string[] }).extraRoles
        return trainerWith(extras)
      },
    }
    const api = await signedInAs(page, 'ADMIN', handlers)
    await page.goto('/app/users')
    const row = page.getByRole('row', { name: /Ravi Kulkarni/ })
    await expect(row.getByText('+ Coordinator')).toHaveCount(0)

    await row.getByRole('button', { name: 'Duties' }).click()
    await expect(page.getByRole('dialog')).toContainText('Main role')
    await page.getByRole('checkbox', { name: /Coordinator/ }).click()
    await page.getByRole('button', { name: 'Save' }).click()

    await expect(page.getByText('Ravi Kulkarni now also has: Coordinator.')).toBeVisible()
    await expect(row.getByText('+ Coordinator')).toBeVisible()
    expect(api.seen.find((r) => r.method === 'PUT' && r.path === '/api/users/7/duties')?.body).toEqual({ extraRoles: ['COORDINATOR'] })
  })

  test('unticking everything withdraws the duties', async ({ page }) => {
    let extras = ['COORDINATOR', 'FINANCE']
    const api = await signedInAs(page, 'ADMIN', {
      'GET /api/users': () => usersPage([trainerWith(extras)]),
      'PUT /api/users/7/duties': (request) => {
        extras = (request.body as { extraRoles: string[] }).extraRoles
        return trainerWith(extras)
      },
    })
    await page.goto('/app/users')
    const row = page.getByRole('row', { name: /Ravi Kulkarni/ })
    await expect(row.getByText('+ Coordinator')).toBeVisible()
    await expect(row.getByText('+ Finance')).toBeVisible()

    await row.getByRole('button', { name: 'Duties' }).click()
    await expect(page.getByRole('checkbox', { name: /Coordinator/ })).toBeChecked()
    await page.getByRole('checkbox', { name: /Coordinator/ }).click()
    await page.getByRole('checkbox', { name: /Finance/ }).click()
    await page.getByRole('button', { name: 'Save' }).click()

    await expect(page.getByText('Ravi Kulkarni has no extra duties now.')).toBeVisible()
    await expect(row.getByText('+ Coordinator')).toHaveCount(0)
    expect(api.seen.find((r) => r.method === 'PUT' && r.path === '/api/users/7/duties')?.body).toEqual({ extraRoles: [] })
  })

  test('a refusal from the server is shown, and the main role is never offered as an extra duty', async ({ page }) => {
    await signedInAs(page, 'ADMIN', {
      'GET /api/users': () =>
        usersPage([userFor('COORDINATOR', { id: 8, fullName: 'Chetan Joshi' })]),
      'PUT /api/users/8/duties': (_request, route) =>
        route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ message: 'Not allowed for this person.' }) }),
    })
    await page.goto('/app/users')
    await page.getByRole('row', { name: /Chetan Joshi/ }).getByRole('button', { name: 'Duties' }).click()
    await expect(page.getByRole('checkbox', { name: /Coordinator/ })).toHaveCount(0) // already his main role
    await page.getByRole('checkbox', { name: /Finance/ }).click()
    await page.getByRole('button', { name: 'Save' }).click()
    await expect(page.getByText('Not allowed for this person.')).toBeVisible()
  })

  test('students and administrators have no Duties button', async ({ page }) => {
    await signedInAs(page, 'ADMIN', {
      'GET /api/users': () =>
        usersPage([userFor('STUDENT', { id: 9, fullName: 'Asha Patil' }), userFor('ADMIN', { id: 10, fullName: 'Anita Rao' })]),
    })
    await page.goto('/app/users')
    await expect(page.getByRole('row', { name: /Asha Patil/ })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Duties' })).toHaveCount(0)
  })

  test('a coordinator can see the Users page but not change duties', async ({ page }) => {
    await signedInAs(page, 'COORDINATOR', { 'GET /api/users': () => usersPage([trainerWith(['FINANCE'])]) })
    await page.goto('/app/users')
    const row = page.getByRole('row', { name: /Ravi Kulkarni/ })
    await expect(row.getByText('+ Finance')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Duties' })).toHaveCount(0)
  })
})
