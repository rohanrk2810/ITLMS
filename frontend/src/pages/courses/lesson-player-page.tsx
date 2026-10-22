import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiClient } from '@/api/client'
import { getCourseDetail, type LessonResponse, recordLessonProgress } from '@/api/courses'
import { EmbeddedVideo } from '@/components/embedded-video'
import { LessonVideo, type PlaybackReport, type VideoSource } from '@/components/lesson-video'
import { PracticeEditor } from '@/components/practice-editor'
import { youtubeIdOf } from '@/lib/youtube'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

function flattenLessons(modules: { lessons: LessonResponse[] }[]): LessonResponse[] {
  return modules.flatMap((m) => m.lessons)
}

/** Turns a YouTube / Vimeo / Google Drive share link into an embeddable URL; null for direct video files. */
function toEmbedUrl(raw: string): string | null {
  const trimmed = raw.trim()
  let url: URL
  try {
    url = new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`)
  } catch {
    return null
  }
  const host = url.hostname.replace(/^(www|m|music)\./, '')
  if (host === 'youtu.be') {
    return `https://www.youtube.com/embed/${url.pathname.slice(1)}`
  }
  if (host === 'youtube.com') {
    const id = url.searchParams.get('v')
    if (id) return `https://www.youtube.com/embed/${id}`
    const m = url.pathname.match(/^\/(?:embed|shorts|live)\/([\w-]+)/)
    if (m) return `https://www.youtube.com/embed/${m[1]}`
  }
  if (host === 'vimeo.com') {
    const m = url.pathname.match(/^\/(\d+)/)
    if (m) return `https://player.vimeo.com/video/${m[1]}`
  }
  if (host === 'drive.google.com') {
    const m = url.pathname.match(/\/file\/d\/([\w-]+)/)
    const id = m?.[1] ?? url.searchParams.get('id')
    if (id) return `https://drive.google.com/file/d/${id}/preview`
  }
  return null
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

  const lessons = courseQuery.data ? flattenLessons(courseQuery.data.modules) : []
  const index = lessons.findIndex((l) => String(l.id) === lessonId)
  const lesson = index >= 0 ? lessons[index] : undefined
  const youtubeId = lesson?.type === 'VIDEO' && lesson.contentUrl ? youtubeIdOf(lesson.contentUrl) : null
  // YouTube is played through its player API and a direct file in our own player, so both can be counted and
  // monitored; other links (Google Drive, Vimeo) stay in a plain frame.
  const embedUrl = lesson?.type === 'VIDEO' && lesson.contentUrl ? toEmbedUrl(lesson.contentUrl) : null
  const cameraWatched = !!lesson?.videoMonitoring && lesson.completed !== null
  // Only a lesson the instructor chose to monitor goes through the monitoring player; every other YouTube, Vimeo or
  // Drive link plays exactly as it always did, in the plain embedded player.
  const videoSource: VideoSource | null = youtubeId && cameraWatched
    ? { kind: 'youtube', id: youtubeId }
    : lesson?.type === 'VIDEO' && lesson.contentUrl && !embedUrl
      ? { kind: 'file', src: lesson.contentUrl }
      : embedUrl && cameraWatched && lesson
        ? { kind: 'embed', src: embedUrl, title: lesson.title, durationSeconds: (lesson.durationMinutes ?? 0) * 60 }
        : null
  // A video watched through the camera completes by itself once it has really been watched, so there is no button.
  // The exception is a frame whose length is unknown: nothing can measure it, so the button stays.
  const mustWatch =
    !!videoSource && cameraWatched && !(videoSource.kind === 'embed' && videoSource.durationSeconds <= 0)
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

  async function saveProgress(watchedSeconds?: number, completed?: boolean, playback?: Partial<PlaybackReport>) {
    if (!lesson) return
    try {
      await recordLessonProgress(lesson.id, { watchedSeconds, completed, ...playback })
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    } catch {
      // Progress is saved again on the next tick or the next explicit action; one failed beat isn't worth a toast.
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
        ) : mustWatch ? (
          <span className="text-right text-sm text-muted-foreground">Watch the video through to complete this lesson</span>
        ) : (
          <Button size="sm" onClick={handleMarkComplete}>
            Mark as complete
          </Button>
        )}
      </div>

      <div className="overflow-hidden rounded-lg border bg-card">
        {lesson.type === 'VIDEO' && lesson.contentUrl && videoSource && (
          <LessonVideo
            key={lesson.id}
            source={videoSource}
            watchedPercent={lesson.watchedPercent ?? 0}
            monitored={lesson.videoMonitoring && lesson.completed !== null}
            pauseOnNoFace={lesson.pauseOnNoFace}
            autoResume={lesson.autoResume}
            graceSeconds={lesson.faceGraceSeconds}
            onReport={(report) => void saveProgress(report.watchedSeconds, undefined, report)}
            onEnded={(report) => void saveProgress(report.watchedSeconds, true, report)}
          />
        )}
        {lesson.type === 'VIDEO' && lesson.contentUrl && !videoSource && toEmbedUrl(lesson.contentUrl) && (
          <EmbeddedVideo key={lesson.id} src={toEmbedUrl(lesson.contentUrl)!} title={lesson.title} />
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
        <PracticeEditor
          key={lesson.id}
          lessonId={lesson.id}
          language={lesson.codeLanguage}
          starterCode={lesson.starterCode}
          allowLanguageChoice={lesson.allowLanguageChoice}
        />
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
