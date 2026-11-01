import type { Page, Route } from '@playwright/test'

export type Role = 'ADMIN' | 'COORDINATOR' | 'TRAINER' | 'STUDENT' | 'PLACEMENT' | 'FINANCE'

export function userFor(role: Role, overrides: Record<string, unknown> = {}) {
  const names: Record<Role, string> = {
    ADMIN: 'Anita',
    COORDINATOR: 'Chetan',
    TRAINER: 'Ravi',
    STUDENT: 'Asha',
    PLACEMENT: 'Pooja',
    FINANCE: 'Farah',
  }
  const firstName = names[role]
  return {
    id: 1,
    firstName,
    lastName: 'Test',
    fullName: `${firstName} Test`,
    email: `${firstName.toLowerCase()}@example.com`,
    phone: '9000000000',
    role,
    roleDisplayName: role,
    status: 'ACTIVE',
    profileId: role === 'STUDENT' ? 30 : null,
    profileCode: null,
    mustChangePassword: false,
    lastLoginAt: null,
    createdAt: '2026-10-01T00:00:00Z',
    hiddenMenu: null,
    extraRoles: [],
    ...overrides,
  }
}

export function authResponse(role: Role, overrides: Record<string, unknown> = {}) {
  const user = userFor(role, overrides)
  return {
    accessToken: `access-${role}`,
    refreshToken: `refresh-${role}`,
    tokenType: 'Bearer',
    expiresIn: 1800,
    mustChangePassword: user.mustChangePassword,
    user,
  }
}

const BRANDING = {
  name: 'Test Institute',
  tagline: null,
  primaryColor: null,
  contactEmail: null,
  contactPhone: null,
  website: null,
  address: null,
  signatoryName: null,
  signatoryTitle: null,
  logoUrl: null,
  faviconUrl: null,
  loginBackgroundUrl: null,
  appBackgroundUrl: null,
  studentHiddenMenu: [],
  uiConfig: '{}',
  version: 1,
}

const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }

export interface SeenRequest {
  method: string
  path: string
  query: URLSearchParams
  body: unknown
}

export type Handler = (request: SeenRequest, route: Route) => unknown | Promise<unknown>

/**
 * Answers every call to the API. `handlers` are keyed "METHOD /path" and return the JSON body (or call
 * `route.fulfill` themselves); anything not listed is a 404 and is remembered in `unmocked`, so a test can check
 * that a page asked for nothing it did not expect.
 */
export async function mockApi(page: Page, handlers: Record<string, Handler> = {}) {
  const seen: SeenRequest[] = []
  const unmocked: string[] = []

  const defaults: Record<string, Handler> = {
    'GET /api/public/branding': () => BRANDING,
    'GET /api/auth/captcha-config': () => ({ enabled: false, siteKey: '' }),
    'GET /api/auth/social-config': () => ({ googleClientId: '', whatsappEnabled: false }),
    'GET /api/notifications/unread-count': () => ({ unread: 0 }),
    'GET /api/notifications': () => EMPTY_PAGE,
    'POST /api/auth/logout': () => ({ message: 'Signed out' }),
  }

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()
    let body: unknown = null
    try {
      body = request.postDataJSON()
    } catch {
      body = request.postData()
    }
    const seenRequest: SeenRequest = { method, path: url.pathname, query: url.searchParams, body }
    seen.push(seenRequest)

    const handler = handlers[`${method} ${url.pathname}`] ?? defaults[`${method} ${url.pathname}`]
    if (!handler) {
      unmocked.push(`${method} ${url.pathname}`)
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Not mocked in the e2e tests' }) })
      return
    }
    const result = await handler(seenRequest, route)
    if (result === undefined) {
      return // the handler answered the route itself
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(result) })
  })

  return { seen, unmocked }
}

/** Starts the page already signed in as `role`: a stored refresh token that the mock turns into a session. */
export async function signedInAs(page: Page, role: Role, handlers: Record<string, Handler> = {}, userOverrides = {}) {
  await page.addInitScript(() => localStorage.setItem('itilms.refreshToken', 'stored-refresh-token'))
  return mockApi(page, {
    'POST /api/auth/refresh': () => authResponse(role, userOverrides),
    ...handlers,
  })
}

export { EMPTY_PAGE }
