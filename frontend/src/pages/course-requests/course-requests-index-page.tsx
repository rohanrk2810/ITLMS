import { CourseRequestsReviewPage } from '@/pages/course-requests/course-requests-review-page'
import { MyCourseRequestsPage } from '@/pages/course-requests/my-course-requests-page'
import { useAuthStore } from '@/stores/auth-store'

/** A student sees their own requests; administrators and coordinators see the queue to decide. */
export function CourseRequestsIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <MyCourseRequestsPage /> : <CourseRequestsReviewPage />
}
