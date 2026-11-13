import { useSearchParams } from 'react-router-dom'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { CourseRequestsIndexPage } from '@/pages/course-requests/course-requests-index-page'
import { ManageCoursesPage } from '@/pages/courses/manage-courses-page'
import { MyCoursesPage } from '@/pages/courses/my-courses-page'
import { hasRole, useAuthStore } from '@/stores/auth-store'

/**
 * A student sees what they're enrolled in; everyone else sees the catalog they build.
 * Administrators, coordinators and students also get a "Course requests" tab: a student's own
 * requests, or the queue that staff decide. The tab is `?tab=requests`, so a link can open it.
 */
export function CoursesIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  const roles = useAuthStore((state) => state.roles)
  const [params, setParams] = useSearchParams()

  const courses = role === 'STUDENT' ? <MyCoursesPage /> : <ManageCoursesPage />
  if (!hasRole(roles, ['ADMIN', 'COORDINATOR', 'STUDENT'])) return courses

  const tab = params.get('tab') === 'requests' ? 'requests' : 'courses'
  return (
    <Tabs
      value={tab}
      onValueChange={(next) => setParams(next === 'requests' ? { tab: 'requests' } : {}, { replace: true })}
      className="gap-6"
    >
      <TabsList>
        <TabsTrigger value="courses">Courses</TabsTrigger>
        <TabsTrigger value="requests">Course requests</TabsTrigger>
      </TabsList>
      <TabsContent value="courses">{courses}</TabsContent>
      <TabsContent value="requests">
        <CourseRequestsIndexPage />
      </TabsContent>
    </Tabs>
  )
}
