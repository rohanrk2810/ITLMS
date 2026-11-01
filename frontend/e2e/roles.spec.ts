import { expect, test } from '@playwright/test'

import { signedInAs, userFor, type Role } from './helpers'

/** The sidebar and the route guards must agree about who may see what. */
const MENU: Array<{ role: Role; sees: string[]; hidden: string[] }> = [
  {
    role: 'ADMIN',
    sees: ['Dashboard', 'Admissions', 'Users', 'Customize', 'ID cards'],
    hidden: ['My progress'],
  },
  {
    role: 'COORDINATOR',
    sees: ['Admissions', 'Users', 'ID cards'],
    hidden: ['Customize', 'My progress'],
  },
  {
    role: 'TRAINER',
    sees: ['Students', 'Batches', 'ID cards'],
    hidden: ['Admissions', 'Users', 'Customize'],
  },
  {
    role: 'STUDENT',
    sees: ['Dashboard', 'My progress', 'Courses', 'ID cards'],
    hidden: ['Admissions', 'Users', 'Customize', 'Students'],
  },
  {
    role: 'PLACEMENT',
    sees: ['Dashboard', 'Placements'],
    hidden: ['Admissions', 'Users', 'ID cards', 'Finance'],
  },
]

for (const { role, sees, hidden } of MENU) {
  test(`${role}: the sidebar shows what the role may open, and nothing else`, async ({ page }) => {
    await signedInAs(page, role, { 'GET /api/auth/me': () => userFor(role, role === 'STUDENT' ? { profileId: 30 } : {}) })
    await page.goto('/app')
    const nav = page.getByRole('navigation').first()
    for (const label of sees) {
      await expect(nav.getByRole('link', { name: label, exact: true }), `${role} should see ${label}`).toBeVisible()
    }
    for (const label of hidden) {
      await expect(nav.getByRole('link', { name: label, exact: true }), `${role} should not see ${label}`).toHaveCount(0)
    }
  })
}

test.describe('route guards', () => {
  test('a student who types an admin address is turned away', async ({ page }) => {
    await signedInAs(page, 'STUDENT', { 'GET /api/auth/me': () => userFor('STUDENT') })
    await page.goto('/app/admissions')
    await expect(page).toHaveURL(/\/app\/forbidden/)
    await expect(page.getByRole('heading', { name: 'Not available for your role' })).toBeVisible()
  })

  test('only the admin can open Customize', async ({ page }) => {
    await signedInAs(page, 'COORDINATOR')
    await page.goto('/app/customize')
    await expect(page).toHaveURL(/\/app\/forbidden/)
  })

  test('a page that does not exist says so', async ({ page }) => {
    await signedInAs(page, 'ADMIN')
    await page.goto('/app/no-such-page')
    await expect(page.getByText(/not found|doesn.t exist|404/i).first()).toBeVisible()
  })
})
