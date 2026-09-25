import type { CourseRequestStatus } from '@/api/course-requests'
import { Badge } from '@/components/ui/badge'

const LABEL: Record<CourseRequestStatus, string> = {
  PENDING: 'Waiting',
  APPROVED: 'Approved',
  REJECTED: 'Not approved',
  CANCELLED: 'Withdrawn',
}

export function RequestStatusBadge({ status }: { status: CourseRequestStatus }) {
  const variant = status === 'APPROVED' ? 'default' : status === 'REJECTED' ? 'destructive' : 'secondary'
  return <Badge variant={variant}>{LABEL[status]}</Badge>
}
