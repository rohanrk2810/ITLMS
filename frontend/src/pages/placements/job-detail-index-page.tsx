import { JobApplyDetailPage } from '@/pages/placements/job-apply-detail-page'
import { JobPipelinePage } from '@/pages/placements/job-pipeline-page'
import { useAuthStore } from '@/stores/auth-store'

export function JobDetailIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <JobApplyDetailPage /> : <JobPipelinePage />
}
