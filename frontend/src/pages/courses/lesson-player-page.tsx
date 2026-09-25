import { type SyntheticEvent, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiClient } from '@/api/client'
import { getCourseDetail, type LessonResponse, recordLessonProgress } from '@/api/courses'
import { PracticeEditor } from '@/components/practice-editor'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

/** How often watch time is saved while a video plays, so a closed tab loses at most this much. */
const WATCH_SAVE_INTERVAL_MS = 15_000

function flattenLessons(modules: { lessons: LessonResponse[] }[]): LessonResponse[] {
  return modules.flatMap((m) => m.lessons)
}

export function LessonPlayerPage() {
  const { courseId, lessonId } = useParams<{ courseId: string; lessonId: string }>()
  const queryClient = useQueryClient()

  const courseQuery = useQuery({
    queryKey: ['courses', 'detail', courseId],
    queryFn: () => getCourseDetail(courseId!),
    enabled: !!courseId,
  })

  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  const lastSavedSecondsRef = useRef(0)

  const lessons = courseQuery.data ? flattenLessons(courseQuery.data.modules) : []
  const index = lessons.findIndex((l) => String(l.id) === lessonId)
  const lesson = index >= 0 ? lessons[index] : undefined
  const prev = index > 0 ? lessons[index - 1] : undefined
  const next = index >= 0 && index < lessons.length - 1 ? lessons[index + 1] : undefined

  useEffect(() => {
    if (lesson?.type !== 'PDF' || !lesson.contentFileRef) {
      setPdfUrl(null)
      return
    }
    let objectUrl: string | null = null
    let cancelled = false
    apiClient
      .get<ArrayBuffer>(`/api/files/${lesson.contentFileRef}/download`, { responseType: 'arraybuffer' })
      .then(({ data }) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
        setPdfUrl(objectUrl)
      })
      .catch(() => toast.error('Could not load the PDF.'))
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [lesson?.id, lesson?.type, lesson?.contentFileRef])

  async function saveProgress(watchedSeconds?: number, completed?: boolean) {
    if (!lesson) return
    try {
      await recordLessonProgress(lesson.id, { watchedSeconds, completed })
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    } catch {
      // Progress is saved again on the next tick or the next explicit action; one failed beat isn't worth a toast.
    }
  }

  function handleTimeUpdate(event: SyntheticEvent<HTMLVideoElement>) {
    const seconds = Math.floor(event.currentTarget.currentTime)
    if (seconds - lastSavedSecondsRef.current >= WATCH_SAVE_INTERVAL_MS / 1000) {
      lastSavedSecondsRef.current = seconds
      void saveProgress(seconds, undefined)
    }
  }

  async function handleMarkComplete() {
    await saveProgress(undefined, true)
    toast.success('Marked complete')
  }

  if (courseQuery.isLoading) {
    return <Skeleton className="h-96 max-w-4xl" />
  }
  if (!lesson) {
    return <p className="text-muted-foreground">This lesson could not be found.</p>
  }

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <Link
        to={`/app/courses/${courseId}`}
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" />
        Back to course
      </Link>

      <div className="flex items-start justify-between gap-4">
        <h1 className="text-xl font-semibold">{lesson.title}</h1>
        {lesson.completed ? (
          <span className="flex items-center gap-1 text-sm text-emerald-600">
            <CheckCircle2 className="size-4" />
            Completed
          </span>
        ) : (
          <Button size="sm" onClick={handleMarkComplete}>
            Mark as complete
          </Button>
        )}
      </div>

      <div className="overflow-hidden rounded-lg border bg-card">
        {lesson.type === 'VIDEO' && lesson.contentUrl && (
          <video
            key={lesson.id}
            src={lesson.contentUrl}
            controls
            className="aspect-video w-full bg-black"
            onTimeUpdate={handleTimeUpdate}
            onEnded={() => void saveProgress(undefined, true)}
          />
        )}
        {lesson.type === 'PDF' &&
          (pdfUrl ? (
            <iframe title={lesson.title} src={pdfUrl} className="h-[70vh] w-full" />
          ) : (
            <div className="p-6 text-sm text-muted-foreground">Loading document...</div>
          ))}
        {(lesson.type === 'NOTE' || lesson.type === 'TEXT') && lesson.textContent && (
          <div className="max-w-none p-6 text-sm whitespace-pre-wrap">{lesson.textContent}</div>
        )}
        {lesson.type === 'LINK' && lesson.contentUrl && (
          <div className="p-6">
            <Button asChild variant="outline">
              <a href={lesson.contentUrl} target="_blank" rel="noreferrer">
                Open resource
                <ExternalLink />
              </a>
            </Button>
          </div>
        )}
      </div>

      {lesson.accessible && lesson.codeLanguage && (
        <PracticeEditor key={lesson.id} lessonId={lesson.id} language={lesson.codeLanguage} starterCode={lesson.starterCode} />
      )}

      <div className="flex items-center justify-between">
        {prev ? (
          <Button asChild variant="outline">
            <Link to={`/app/courses/${courseId}/lessons/${prev.id}`}>
              <ChevronLeft />
              {prev.title}
            </Link>
          </Button>
        ) : (
          <span />
        )}
        {next ? (
          <Button asChild>
            <Link to={`/app/courses/${courseId}/lessons/${next.id}`}>
              {next.title}
              <ChevronRight />
            </Link>
          </Button>
        ) : (
          <span />
        )}
      </div>
    </div>
  )
}
