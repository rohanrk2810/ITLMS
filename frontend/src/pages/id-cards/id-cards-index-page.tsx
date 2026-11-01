import { hasRole, useAuthStore } from '@/stores/auth-store'

import { IdCardManagementPage } from './id-card-management-page'
import { MyIdCardPage } from './my-id-card-page'

/** One address, two screens: staff run the cards; a student or trainer sees their own. */
export function IdCardsIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return hasRole(role, ['ADMIN', 'COORDINATOR']) ? <IdCardManagementPage /> : <MyIdCardPage />
}
