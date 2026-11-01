import { describe, expect, it } from 'vitest'

import { ALWAYS_SHOWN } from '@/lib/ui-config'

import { NAV_ITEMS } from './nav-items'

const ROLES = ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT', 'PLACEMENT', 'FINANCE']

describe('sidebar items', () => {
  it('every page appears once and sits under /app', () => {
    const paths = NAV_ITEMS.map((i) => i.to)
    expect(new Set(paths).size).toBe(paths.length)
    expect(paths.every((p) => p === '/app' || p.startsWith('/app/'))).toBe(true)
  })

  it('every item is open to at least one real role', () => {
    for (const item of NAV_ITEMS) {
      expect(item.roles.length, item.label).toBeGreaterThan(0)
      expect(item.roles.every((r) => ROLES.includes(r)), item.label).toBe(true)
    }
  })

  it('the dashboard is open to every role', () => {
    const dashboard = NAV_ITEMS.find((i) => i.to === '/app')
    expect([...(dashboard?.roles ?? [])].sort()).toEqual([...ROLES].sort())
  })

  it('the pages an admin can never hide exist in the menu', () => {
    const paths = NAV_ITEMS.map((i) => i.to)
    for (const always of ALWAYS_SHOWN) {
      expect(paths).toContain(always)
    }
  })

  it('the settings pages are for the admin alone, and people and admissions are not for students', () => {
    for (const to of ['/app/customize', '/app/monitoring']) {
      expect(NAV_ITEMS.find((i) => i.to === to)?.roles).toEqual(['ADMIN'])
    }
    expect(NAV_ITEMS.find((i) => i.to === '/app/users')?.roles).not.toContain('STUDENT')
    expect(NAV_ITEMS.find((i) => i.to === '/app/admissions')?.roles).not.toContain('STUDENT')
  })

  it('a student reaches their own ID card; a placement officer has no ID card page', () => {
    const idCards = NAV_ITEMS.find((i) => i.to === '/app/id-cards')
    expect(idCards?.roles).toContain('STUDENT')
    expect(idCards?.roles).not.toContain('PLACEMENT')
  })
})
