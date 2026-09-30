import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { ChevronLeft, Loader2, Video } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import {
  type AnswerQuestionInput,
  type LiveQuestion,
  type LiveSessionResponse,
  answerQuestion,
  classQuestions,
  fetchRecording,
  getLiveSessionStatus,
} from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import { ContentProtection } from '@/components/content-protection'
import { hasRole, useAuthStore } from '@/stores/auth-store'
import { QuestionHistoryList } from './question-history-list'

const CodeEditor = lazy(() => import('@/components/code-editor'))
const HOST_ROLES = ['ADMIN', 'COORDINATOR', 'TRAINER'] as const

/**
 * A finished class, reviewed afterwards: the recording (once one exists) and every question asked during it, each
 * at the moment it was asked. Clicking a question jumps the recording there; a student who missed a question, or
 * the whole class, answers it here - flagged as answered from the recording, same as answering late in the room.
 */
export function ClassReviewPage() {
  const { classSessionId } = useParams<{ classSessionId: string }>()
  const user = useAuthStore((s) => s.user)
  const isHost = !!user && hasRole(user.role, HOST_ROLES)
  const videoRef = useRef<HTMLVideoElement>(null)

  const [session, setSession] = useState<LiveSessionResponse | null>(null)
  const [questions, setQuestions] = useState<LiveQuestion[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [videoUrl, setVideoUrl] = useState<string | null>(null)
  const [videoLoading, setVideoLoading] = useState(false)

  const refresh = () => {
    if (!classSessionId) return
    classQuestions(classSessionId).then(setQuestions).catch(() => undefined)
  }

  useEffect(() => {
    if (!classSessionId) return
    let cancelled = false
    Promise.all([getLiveSessionStatus(classSessionId), classQuestions(classSessionId)])
      .then(([s, qs]) => {
        if (cancelled) return
        setSession(s)
        setQuestions(qs)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(apiErrorMessage(err, 'Could not load this class.'))
      })
    return () => {
      cancelled = true
    }
  }, [classSessionId])

  const answeredCount = useMemo(() => questions?.filter((q) => q.myAnswer).length ?? 0, [questions])

  useEffect(() => {
    if (!session?.recordingUrl) return
    let cancelled = false
    let objectUrl: string | null = null
    setVideoLoading(true)
    fetchRecording(session.id)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url)
          return
        }
        objectUrl = url
        setVideoUrl(url)
      })
      .catch((err: unknown) => toast.error(apiErrorMessage(err, 'Could not load the recording.')))
      .finally(() => {
        if (!cancelled) setVideoLoading(false)
      })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [session?.id, session?.recordingUrl])

  function jumpTo(offsetSeconds: number) {
    const video = videoRef.current
    if (video) {
      video.currentTime = offsetSeconds
      void video.play()
    }
  }

  if (error) {
    return (
      <div className="flex max-w-2xl flex-col gap-4">
        <BackLink />
        <Card>
          <CardHeader>
            <CardTitle>Can&apos;t open this class</CardTitle>
            <CardDescription>{error}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  if (!session || !questions) {
    return <Skeleton className="h-[60vh] max-w-4xl" />
  }

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <BackLink />
      <div>
        <h1 className="text-2xl font-semibold">{session.topic ?? session.courseTitle}</h1>
        <p className="text-muted-foreground">
          {session.batchCode} &middot; {new Date(session.scheduledStartAt).toLocaleString(undefined, {
            weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
          })}
        </p>
      </div>

      {session.recordingUrl ? (
        <ContentProtection active={!isHost} watermarkLabel={user ? `${user.fullName} · ${user.email}` : ''}>
          {videoLoading && !videoUrl ? (
            <Skeleton className="aspect-video w-full" />
          ) : (
            // eslint-disable-next-line jsx-a11y/media-has-caption
            <video ref={videoRef} src={videoUrl ?? undefined} controls className="w-full rounded-lg border bg-black" />
          )}
        </ContentProtection>
      ) : (
        <Card>
          <CardContent className="flex items-center gap-3 pt-6 text-sm text-muted-foreground">
            <Video className="size-5" />
            No recording of this class yet. You can still answer the questions that were asked.
          </CardContent>
        </Card>
      )}

      {!isHost && questions.length > 0 && (
        <p className="text-sm text-muted-foreground">{answeredCount} of {questions.length} questions answered</p>
      )}

      {isHost ? (
        <QuestionHistoryList classSessionId={Number(classSessionId)} questions={questions} onRefresh={refresh} />
      ) : (
        <div className="flex flex-col gap-3">
          <h2 className="font-medium">Questions asked in this class</h2>
          {questions.length === 0 && <p className="text-sm text-muted-foreground">Nothing was asked in this class.</p>}
          {questions.map((q) => (
            <StudentQuestionCard key={q.id} question={q} onJump={session.recordingUrl ? jumpTo : undefined}
              onAnswered={(updated) => setQuestions((qs) => qs?.map((x) => (x.id === updated.id ? updated : x)) ?? qs)} />
          ))}
        </div>
      )}
    </div>
  )
}

function StudentQuestionCard({ question, onJump, onAnswered }: {
  question: LiveQuestion
  onJump?: (offsetSeconds: number) => void
  onAnswered: (q: LiveQuestion) => void
}) {
  const [selected, setSelected] = useState<number[]>([])
  const [text, setText] = useState('')
  const [code, setCode] = useState(question.starterCode ?? '')
  const [busy, setBusy] = useState(false)

  function toggle(i: number) {
    if (question.type === 'MULTIPLE_SELECT') {
      setSelected((s) => (s.includes(i) ? s.filter((x) => x !== i) : [...s, i]))
    } else {
      setSelected([i])
    }
  }

  async function submit() {
    const input: AnswerQuestionInput = {}
    if (question.type === 'MCQ' || question.type === 'MULTIPLE_SELECT' || question.type === 'TRUE_FALSE') {
      if (selected.length === 0) return toast.error('Pick an answer.')
      input.selected = selected
    } else if (question.type === 'CODING') {
      if (!code.trim()) return toast.error('Write your code first.')
      input.code = code
      input.language = question.language ?? undefined
    } else {
      if (!text.trim()) return toast.error('Write your answer.')
      input.text = text
    }
    setBusy(true)
    try {
      onAnswered(await answerQuestion(question.id, input))
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not submit your answer.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardContent className="flex flex-col gap-2 pt-6">
        <div className="flex items-start justify-between gap-2">
          <p className="text-sm font-medium">{question.prompt}</p>
          {onJump && (
            <button type="button" className="shrink-0 font-mono text-xs text-muted-foreground hover:underline" onClick={() => onJump(question.offsetSeconds)}>
              {question.offsetLabel}
            </button>
          )}
        </div>

        {question.myAnswer ? (
          <p className="text-sm text-muted-foreground">
            {question.myAnswer.correct == null
              ? 'You answered this. The trainer will review it.'
              : question.myAnswer.correct
                ? `Correct · +${question.myAnswer.awardedMarks ?? 0} marks`
                : 'Not quite right.'}
          </p>
        ) : (
          <>
            {(question.type === 'MCQ' || question.type === 'TRUE_FALSE') && (
              <RadioGroup value={String(selected[0] ?? '')} onValueChange={(v) => setSelected([Number(v)])}>
                {(question.options ?? []).map((opt, i) => (
                  <label key={i} className="flex items-center gap-2 text-sm">
                    <RadioGroupItem value={String(i)} /> {opt}
                  </label>
                ))}
              </RadioGroup>
            )}
            {question.type === 'MULTIPLE_SELECT' && (
              <div className="flex flex-col gap-1">
                {(question.options ?? []).map((opt, i) => (
                  <label key={i} className="flex items-center gap-2 text-sm">
                    <Checkbox checked={selected.includes(i)} onCheckedChange={() => toggle(i)} /> {opt}
                  </label>
                ))}
              </div>
            )}
            {(question.type === 'SHORT_ANSWER' || question.type === 'OTHER') && (
              <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="Your answer" maxLength={4000} />
            )}
            {question.type === 'CODING' && (
              <Suspense fallback={<div className="flex h-20 items-center justify-center"><Loader2 className="size-4 animate-spin" /></div>}>
                <CodeEditor language="PYTHON" value={code} onChange={setCode} height={180} ariaLabel="Your code" />
              </Suspense>
            )}
            <Button size="sm" disabled={busy} onClick={() => void submit()} className="self-end">
              {busy && <Loader2 className="size-4 animate-spin" />} Submit
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function BackLink() {
  return (
    <Link to="/app/live-classes" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ChevronLeft className="size-4" />
      Live classes
    </Link>
  )
}
