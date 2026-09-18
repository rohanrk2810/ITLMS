import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuthStore } from '@/stores/auth-store'

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

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Welcome back, {user?.firstName}</h1>
        <p className="text-muted-foreground">{user && ROLE_BLURB[user.role]}</p>
      </div>
      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle>This is the starting shell</CardTitle>
          <CardDescription>
            Sign-in, token refresh, the role-filtered navigation and the notification bell are wired up to the
            gateway. Each module in the sidebar will get its own page next.
          </CardDescription>
        </CardHeader>
      </Card>
    </div>
  )
}
