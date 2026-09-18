import { CourseDetailPage } from '@/pages/courses/course-detail-page'
import { CourseEditorPage } from '@/pages/courses/course-editor-page'
import { useAuthStore } from '@/stores/auth-store'

export function CourseDetailIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <CourseDetailPage /> : <CourseEditorPage />
}
