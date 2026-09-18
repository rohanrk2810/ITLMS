import { ManageCoursesPage } from '@/pages/courses/manage-courses-page'
import { MyCoursesPage } from '@/pages/courses/my-courses-page'
import { useAuthStore } from '@/stores/auth-store'

/** A student sees what they're enrolled in; everyone else sees the catalog they build. */
export function CoursesIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <MyCoursesPage /> : <ManageCoursesPage />
}
