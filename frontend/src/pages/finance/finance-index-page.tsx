import { FinanceDeskPage } from '@/pages/finance/finance-desk-page'
import { FinancePage } from '@/pages/finance/finance-page'
import { hasRole, useAuthStore } from '@/stores/auth-store'

export function FinanceIndexPage() {
  const roles = useAuthStore((state) => state.roles)
  return hasRole(roles, ['ADMIN', 'COORDINATOR', 'FINANCE']) ? <FinanceDeskPage /> : <FinancePage />
}
