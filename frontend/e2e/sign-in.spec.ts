import { expect, test } from '@playwright/test'

import { authResponse, mockApi } from './helpers'

async function signIn(page: import('@playwright/test').Page, identifier: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email or phone').fill(identifier)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test.describe('sign in', () => {
  test('the sign-in form is what an anonymous visitor sees', async ({ page }) => {
    await mockApi(page)
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'Sign in' }).or(page.getByText('Sign in').first())).toBeVisible()
    await expect(page.getByLabel('Email or phone')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
  })

  test('a wrong password shows the server message and stays on the page', async ({ page }) => {
    await mockApi(page, {
      'POST /api/auth/login': (_req, route) =>
        route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Invalid email or password.' }),
        }),
    })
    await signIn(page, 'asha@example.com', 'wrong')
    await expect(page.getByText('Invalid email or password.')).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })

  test('the typed details are what is sent to the server', async ({ page }) => {
    const api = await mockApi(page, { 'POST /api/auth/login': () => authResponse('ADMIN') })
    await signIn(page, 'anita@example.com', 'Secret#123')
    await expect(page).toHaveURL(/\/app$/)
    const call = api.seen.find((r) => r.path === '/api/auth/login')
    expect(call?.body).toMatchObject({ identifier: 'anita@example.com', password: 'Secret#123' })
  })

  test('an admin lands on the dashboard, greeted by name', async ({ page }) => {
    await mockApi(page, { 'POST /api/auth/login': () => authResponse('ADMIN') })
    await signIn(page, 'anita@example.com', 'Secret#123')
    await expect(page.getByRole('heading', { name: 'Welcome back, Anita' })).toBeVisible()
  })

  test('a temporary password sends the user to set a new one before anything else', async ({ page }) => {
    await mockApi(page, { 'POST /api/auth/login': () => authResponse('STUDENT', { mustChangePassword: true }) })
    await signIn(page, 'asha@example.com', 'Temp#1234')
    await expect(page).toHaveURL(/\/app\/change-password/)
    await expect(page.getByText('Set a new password')).toBeVisible()
  })

  test('someone who is not signed in is sent to the login page, not shown a private page', async ({ page }) => {
    await mockApi(page)
    await page.goto('/app/id-cards')
    await expect(page).toHaveURL(/\/login/)
  })

  test('signing out ends the session', async ({ page }) => {
    await mockApi(page, { 'POST /api/auth/login': () => authResponse('ADMIN') })
    await signIn(page, 'anita@example.com', 'Secret#123')
    await expect(page).toHaveURL(/\/app$/)
    await page.getByRole('button', { name: /Anita/ }).click()
    await page.getByRole('menuitem', { name: /sign out|log out/i }).click()
    await expect(page).toHaveURL(/\/login/)
    await page.goto('/app')
    await expect(page).toHaveURL(/\/login/)
  })
})
