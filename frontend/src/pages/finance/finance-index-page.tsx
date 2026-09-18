import { FinanceDeskPage } from '@/pages/finance/finance-desk-page'
import { FinancePage } from '@/pages/finance/finance-page'
import { hasRole, useAuthStore } from '@/stores/auth-store'

export function FinanceIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return hasRole(role, ['ADMIN', 'COORDINATOR', 'FINANCE']) ? <FinanceDeskPage /> : <FinancePage />
}
