import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { getDashboardSummary } from '@/api/reporting'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatMoney } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const ROLE_BLURB: Record<string, string> = {
  ADMIN: 'You have full access across every module.',
  COORDINATOR: 'Admissions, batches, courses and academic records are yours to run.',
  TRAINER: 'Your batches, live classes and evaluations live here.',
  STUDENT: 'Your courses, attendance, assessments and fees, in one place.',
  PLACEMENT: 'Companies, job postings and the interview pipeline.',
  FINANCE: 'Fee plans, payments, receipts and overdue reminders.',
}

export function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const showInstituteSummary = hasRole(user?.role, ['ADMIN', 'COORDINATOR'])

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Welcome back, {user?.firstName}</h1>
        <p className="text-muted-foreground">{user && ROLE_BLURB[user.role]}</p>
      </div>

      {showInstituteSummary ? <InstituteSummary /> : <RoleQuickLinks role={user?.role} />}
    </div>
  )
}

function InstituteSummary() {
  const query = useQuery({ queryKey: ['dashboard', 'summary'], queryFn: getDashboardSummary })

  if (query.isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-24" />
        ))}
      </div>
    )
  }
  const s = query.data
  if (!s) return null

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Stat label="Students admitted" value={s.studentsAdmittedTotal} hint={`+${s.studentsAdmittedLast30Days} in 30 days`} />
        <Stat label="Enrollments" value={s.enrollmentsTotal} hint={`+${s.enrollmentsLast30Days} in 30 days`} />
        <Stat label="Revenue collected" value={formatMoney(s.revenueCollectedTotal)} hint={`${formatMoney(s.revenueCollectedLast30Days)} in 30 days`} />
        <Stat
          label="Overdue"
          value={formatMoney(s.overdueAmountTotal)}
          hint={`${s.installmentsOverdueTotal} installments`}
          tone={s.installmentsOverdueTotal > 0 ? 'destructive' : undefined}
        />
        <Stat label="Certificates issued" value={s.certificatesIssuedTotal} />
        <Stat label="Quiz attempts" value={s.quizAttemptsTotal} hint={`${s.quizAttemptsPassed} passed`} />
        <Stat label="Attendance" value={`${s.attendance.percent}%`} hint={`${s.attendance.present} present, ${s.attendance.absent} absent`} />
        <Stat label="Placements" value={s.placementsSelectedTotal} hint={`${s.jobsPostedTotal} jobs posted`} />
      </div>
      <Link to="/app/audit-logs" className="text-sm font-medium text-foreground hover:underline">
        View the audit trail &rarr;
      </Link>
    </div>
  )
}

function Stat({ label, value, hint, tone }: { label: string; value: string | number; hint?: string; tone?: 'destructive' }) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`text-xl font-semibold ${tone === 'destructive' ? 'text-destructive' : ''}`}>{value}</p>
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}

const QUICK_LINKS: Record<string, { label: string; to: string; description: string }[]> = {
  TRAINER: [
    { label: 'Batches', to: '/app/batches', description: 'Your roster, timetable and attendance.' },
    { label: 'Courses', to: '/app/courses', description: 'Build modules and lessons.' },
    { label: 'Assessments', to: '/app/assessments', description: 'Tests and assignments to mark.' },
  ],
  STUDENT: [
    { label: 'My courses', to: '/app/courses', description: 'Pick up where you left off.' },
    { label: 'Assessments', to: '/app/assessments', description: 'Tests to sit, assignments to hand in.' },
    { label: 'Fees', to: '/app/finance', description: 'What you owe and what you’ve paid.' },
  ],
  PLACEMENT: [{ label: 'Placements', to: '/app/placements', description: 'Companies, jobs and applications.' }],
  FINANCE: [{ label: 'Finance', to: '/app/finance', description: 'The finance desk and overdue list.' }],
}

function RoleQuickLinks({ role }: { role?: string }) {
  const links = (role && QUICK_LINKS[role]) || []
  if (links.length === 0) return null

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {links.map((link) => (
        <Link key={link.to} to={link.to}>
          <Card className="h-full transition-colors hover:border-primary">
            <CardHeader>
              <CardTitle className="text-base">{link.label}</CardTitle>
              <CardDescription>{link.description}</CardDescription>
            </CardHeader>
          </Card>
        </Link>
      ))}
    </div>
  )
}
