/**
 * Backend notification and email links use the paths each service knows
 * about internally - `/student/...`, `/live/...`, `/jobs/...`,
 * `/finance/...`, `/admin/...`, `/announcements/...` - which never matched
 * this app's own `/app/...` routes. This is the one place that translates
 * between them, so an actionUrl from any service lands somewhere real
 * instead of the 404 page.
 */
const RULES: Array<{ test: RegExp; to: (match: RegExpMatchArray) => string }> = [
  { test: /^\/student\/dashboard\/?$/, to: () => '/app' },
  { test: /^\/student\/courses\/(\d+)\/?$/, to: (m) => `/app/courses/${m[1]}` },
  { test: /^\/student\/courses\/?$/, to: () => '/app/courses' },
  { test: /^\/student\/batches\/(\d+)\/?$/, to: () => '/app/live-classes' },
  { test: /^\/student\/timetable\/?$/, to: () => '/app/live-classes' },
  { test: /^\/student\/assignments\/(\d+)\/?$/, to: (m) => `/app/assessments/assignments/${m[1]}` },
  { test: /^\/student\/tests\/(\d+)\/?$/, to: (m) => `/app/assessments/tests/${m[1]}` },
  { test: /^\/student\/results\/?$/, to: () => '/app/assessments' },
  { test: /^\/student\/fees\/?$/, to: () => '/app/finance' },
  { test: /^\/student\/certificates\/?$/, to: () => '/app/certificates' },
  { test: /^\/student\/placements\/?$/, to: () => '/app/placements' },
  { test: /^\/finance\/overdue\/?$/, to: () => '/app/finance' },
  { test: /^\/live\/(\d+)\/?$/, to: (m) => `/app/live-classes/${m[1]}` },
  { test: /^\/jobs\/(\d+)\/?$/, to: (m) => `/app/placements/${m[1]}` },
  { test: /^\/admin\/leads\/(\d+)\/?$/, to: (m) => `/app/admissions/${m[1]}` },
  { test: /^\/announcements\/(\d+)\/?$/, to: (m) => `/app/announcements/${m[1]}` },
  { test: /^\/announcements\/?$/, to: () => '/app/announcements' },
]

/**
 * Null means "not one of the shapes above" - distinct from a match that
 * happens to point at `/app` itself, so a caller can fall through to a 404
 * instead of silently landing on the dashboard for an unrelated bad path.
 */
export function mapActionUrl(url: string | null | undefined): string | null {
  if (!url) return null
  for (const rule of RULES) {
    const match = url.match(rule.test)
    if (match) return rule.to(match)
  }
  return null
}
