import { describe, expect, it } from 'vitest'

import { mapActionUrl } from './action-url'

describe('mapActionUrl (backend notification links to app routes)', () => {
  it.each([
    ['/student/dashboard', '/app'],
    ['/student/courses/12', '/app/courses/12'],
    ['/student/courses', '/app/courses'],
    ['/student/tests/7', '/app/assessments/tests/7'],
    ['/student/assignments/3', '/app/assessments/assignments/3'],
    ['/student/fees', '/app/finance'],
    ['/student/certificates', '/app/certificates'],
    ['/student/id-card', '/app/id-cards'],
    ['/live/55', '/app/live-classes/55'],
    ['/jobs/9', '/app/placements/9'],
    ['/admin/students/4', '/app/students/4'],
    ['/admin/leads/8', '/app/admissions/8'],
    ['/announcements', '/app/announcements'],
    ['/announcements/2', '/app/announcements/2'],
    ['/course-requests', '/app/course-requests'],
  ])('%s -> %s', (backend, app) => {
    expect(mapActionUrl(backend)).toBe(app)
  })

  it('accepts a trailing slash', () => {
    expect(mapActionUrl('/student/fees/')).toBe('/app/finance')
  })

  it('returns null, not the dashboard, for a path it does not know', () => {
    expect(mapActionUrl('/something/else')).toBeNull()
    expect(mapActionUrl('')).toBeNull()
    expect(mapActionUrl(null)).toBeNull()
    expect(mapActionUrl(undefined)).toBeNull()
  })
})
