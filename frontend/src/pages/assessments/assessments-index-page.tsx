import { ManageTestsPage } from '@/pages/assessments/manage-tests-page'
import { TestsPage } from '@/pages/assessments/tests-page'
import { useAuthStore } from '@/stores/auth-store'

export function AssessmentsIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <TestsPage /> : <ManageTestsPage />
}
