import { useQuery } from '@tanstack/react-query'
import { BookOpen } from 'lucide-react'
import { Link } from 'react-router-dom'

import { lookupCourses, myCourseProgress, searchCourses } from '@/api/courses'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'

export function MyCoursesPage() {
  const progressQuery = useQuery({ queryKey: ['courses', 'my-progress'], queryFn: myCourseProgress })

  const courseIds = progressQuery.data?.map((p) => p.courseId) ?? []
  const coursesQuery = useQuery({
    queryKey: ['courses', 'lookup', courseIds],
    queryFn: () => lookupCourses(courseIds),
    enabled: progressQuery.isSuccess && courseIds.length > 0,
  })

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">My courses</h1>
        <p className="text-muted-foreground">Everything you&apos;re enrolled in, and how far you&apos;ve got.</p>
      </div>

      {progressQuery.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-40" />
          ))}
        </div>
      )}

      {progressQuery.isSuccess && progressQuery.data.length === 0 && (
        <Card className="max-w-md">
          <CardHeader>
            <CardTitle>No courses yet</CardTitle>
            <CardDescription>
              Once you&apos;re enrolled in a batch, it will show up here. Pick a course below to ask to join.
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {progressQuery.data?.map((progress) => {
          const course = coursesQuery.data?.find((c) => c.id === progress.courseId)
          return (
            <Link key={progress.courseId} to={`/app/courses/${progress.courseId}`}>
              <Card className="h-full transition-colors hover:border-primary">
                <CardHeader>
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="line-clamp-2">{course?.title ?? `Course #${progress.courseId}`}</CardTitle>
                    <BookOpen className="size-5 shrink-0 text-muted-foreground" />
                  </div>
                  <CardDescription className="line-clamp-2">{course?.summary}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">
                      {progress.completedLessons} of {progress.totalLessons} lessons
                    </span>
                    <span className="font-medium">{Math.round(progress.progressPercent)}%</span>
                  </div>
                  <Progress value={progress.progressPercent} />
                  {progress.allLessonsComplete && <Badge variant="secondary">All lessons complete</Badge>}
                </CardContent>
              </Card>
            </Link>
          )
        })}
      </div>

      <BrowseCourses enrolledIds={courseIds} />
    </div>
  )
}

/** Published courses the student is not in yet. Opening one shows its outline and a request-to-join form. */
function BrowseCourses({ enrolledIds }: { enrolledIds: number[] }) {
  const query = useQuery({
    queryKey: ['courses', 'browse'],
    queryFn: () => searchCourses({ status: 'PUBLISHED' }),
  })
  const courses = (query.data?.content ?? []).filter((c) => !enrolledIds.includes(c.id))

  if (query.isSuccess && courses.length === 0) return null

  return (
    <section className="flex flex-col gap-3">
      <div>
        <h2 className="text-lg font-semibold">Browse courses</h2>
        <p className="text-sm text-muted-foreground">
          Open a course to see what it covers, then ask to join. <Link to="/app/course-requests" className="text-primary hover:underline">Your requests</Link>
        </p>
      </div>
      {query.isLoading && <Skeleton className="h-32" />}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {courses.map((course) => (
          <Link key={course.id} to={`/app/courses/${course.id}`}>
            <Card className="h-full transition-colors hover:border-primary">
              <CardHeader>
                <CardTitle className="line-clamp-2">{course.title}</CardTitle>
                <CardDescription className="line-clamp-2">{course.summary}</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <Badge variant="outline">{course.level}</Badge>
                {course.durationHours != null && <span>{course.durationHours} hours</span>}
                {course.technologyStack && <span className="line-clamp-1">{course.technologyStack}</span>}
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </section>
  )
}
