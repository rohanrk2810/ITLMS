import { JobBoardPage } from '@/pages/placements/job-board-page'
import { PlacementDeskPage } from '@/pages/placements/placement-desk-page'
import { useAuthStore } from '@/stores/auth-store'

/** A student sees the job board and their own applications; placement staff and admin see the desk. */
export function PlacementsIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <JobBoardPage /> : <PlacementDeskPage />
}
