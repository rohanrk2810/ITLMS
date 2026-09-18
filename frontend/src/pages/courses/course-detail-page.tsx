import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, ChevronLeft, CircleDashed, FileText, Link2, PlayCircle, StickyNote } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { getCourseDetail, type LessonResponse } from '@/api/courses'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

const TYPE_ICON: Record<LessonResponse['type'], typeof PlayCircle> = {
  VIDEO: PlayCircle,
  PDF: FileText,
  NOTE: StickyNote,
  LINK: Link2,
  TEXT: StickyNote,
}

export function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const query = useQuery({
    queryKey: ['courses', 'detail', courseId],
    queryFn: () => getCourseDetail(courseId!),
    enabled: !!courseId,
  })

  if (query.isLoading) {
    return <Skeleton className="h-96 max-w-3xl" />
  }
  if (!query.data) {
    return null
  }

  const { course, modules, enrolled, progress } = query.data

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Link to="/app/courses" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        My courses
      </Link>

      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold">{course.title}</h1>
          <Badge variant="outline">{course.level}</Badge>
        </div>
        {course.summary && <p className="mt-1 text-muted-foreground">{course.summary}</p>}
      </div>

      {enrolled && progress && (
        <Card>
          <CardContent className="flex flex-col gap-2 pt-6">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                {progress.completedLessons} of {progress.totalLessons} lessons complete
              </span>
              <span className="font-medium">{Math.round(progress.progressPercent)}%</span>
            </div>
            <Progress value={progress.progressPercent} />
          </CardContent>
        </Card>
      )}

      {!enrolled && (
        <Card>
          <CardHeader>
            <CardDescription>
              You are not enrolled in this course, so lesson material is not shown - only the outline.
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      <div className="flex flex-col gap-4">
        {modules.map((module) => (
          <Card key={module.id}>
            <CardHeader>
              <CardTitle className="text-base">{module.title}</CardTitle>
              {module.description && <CardDescription>{module.description}</CardDescription>}
            </CardHeader>
            <CardContent className="flex flex-col gap-1">
              {module.lessons.map((lesson) => {
                const Icon = TYPE_ICON[lesson.type]
                const content = (
                  <div
                    className={cn(
                      'flex items-center gap-3 rounded-md px-3 py-2 text-sm',
                      lesson.accessible ? 'hover:bg-accent' : 'opacity-60',
                    )}
                  >
                    {lesson.completed ? (
                      <CheckCircle2 className="size-4 shrink-0 text-emerald-600" />
                    ) : (
                      <CircleDashed className="size-4 shrink-0 text-muted-foreground" />
                    )}
                    <Icon className="size-4 shrink-0 text-muted-foreground" />
                    <span className="flex-1">{lesson.title}</span>
                    {lesson.mandatory && (
                      <Badge variant="outline" className="text-[10px]">
                        Mandatory
                      </Badge>
                    )}
                    {lesson.durationMinutes && (
                      <span className="text-xs text-muted-foreground">{lesson.durationMinutes}m</span>
                    )}
                  </div>
                )
                return lesson.accessible ? (
                  <Link key={lesson.id} to={`/app/courses/${courseId}/lessons/${lesson.id}`}>
                    {content}
                  </Link>
                ) : (
                  <div key={lesson.id}>{content}</div>
                )
              })}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
