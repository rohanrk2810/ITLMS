import { useCallback, useEffect, useRef, useState } from 'react'
import { CheckCircle2, ChevronLeft, XCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import {
  type AttemptResultResponse,
  type AttemptViewResponse,
  getMyAttempts,
  type SubmitAnswer,
  saveAttemptAnswers,
  startOrResumeAttempt,
  submitAttempt,
} from '@/api/assessments'
import { apiErrorMessage } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'

const AUTOSAVE_INTERVAL_MS = 20_000

type Answers = Record<number, Set<number>>

function toSubmitAnswers(answers: Answers): SubmitAnswer[] {
  return Object.entries(answers).map(([questionId, options]) => ({
    questionId: Number(questionId),
    selectedOptionIds: [...options],
  }))
}

function formatClock(totalSeconds: number): string {
  const m = Math.floor(Math.max(0, totalSeconds) / 60)
  const s = Math.max(0, totalSeconds) % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function QuizAttemptPage() {
  const { quizId } = useParams<{ quizId: string }>()

  const [view, setView] = useState<'loading' | 'live' | 'results' | 'error'>('loading')
  const [paper, setPaper] = useState<AttemptViewResponse | null>(null)
  const [answers, setAnswers] = useState<Answers>({})
  const [secondsRemaining, setSecondsRemaining] = useState(0)
  const [pastAttempts, setPastAttempts] = useState<AttemptResultResponse[]>([])
  const [result, setResult] = useState<AttemptResultResponse | null>(null)
  const [errorMessage, setErrorMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const answersRef = useRef<Answers>({})
  useEffect(() => {
    answersRef.current = answers
  }, [answers])

  const submitNow = useCallback(async () => {
    if (!paper || submitting) return
    setSubmitting(true)
    try {
      const outcome = await submitAttempt(paper.attemptId, toSubmitAnswers(answersRef.current))
      setResult(outcome)
      setView('results')
      toast.success('Test submitted')
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not submit the test. Your saved answers are still safe.'))
      setSubmitting(false)
    }
  }, [paper, submitting])

  // Load: start (or resume) an attempt; a test that is no longer open falls back to past results.
  useEffect(() => {
    if (!quizId) return
    let cancelled = false
    setView('loading')
    startOrResumeAttempt(quizId)
      .then((view) => {
        if (cancelled) return
        setPaper(view)
        setSecondsRemaining(view.secondsRemaining)
        setAnswers({})
        setView('live')
      })
      .catch(() => {
        getMyAttempts(quizId)
          .then((attempts) => {
            if (cancelled) return
            setPastAttempts(attempts)
            setView('results')
          })
          .catch((error: unknown) => {
            if (cancelled) return
            setErrorMessage(apiErrorMessage(error, 'This test is not available to you.'))
            setView('error')
          })
      })
    return () => {
      cancelled = true
    }
  }, [quizId])

  // The countdown, and autosave every 20s while it runs.
  useEffect(() => {
    if (view !== 'live' || !paper) return
    const tick = setInterval(() => setSecondsRemaining((s) => Math.max(0, s - 1)), 1000)
    const save = setInterval(() => {
      void saveAttemptAnswers(paper.attemptId, toSubmitAnswers(answersRef.current)).catch(() => undefined)
    }, AUTOSAVE_INTERVAL_MS)
    return () => {
      clearInterval(tick)
      clearInterval(save)
    }
  }, [view, paper])

  // Time's up: submit whatever was saved.
  useEffect(() => {
    if (view === 'live' && secondsRemaining === 0) {
      void submitNow()
    }
  }, [view, secondsRemaining, submitNow])

  function selectSingle(questionId: number, optionId: number) {
    setAnswers((prev) => ({ ...prev, [questionId]: new Set([optionId]) }))
  }

  function toggleMulti(questionId: number, optionId: number) {
    setAnswers((prev) => {
      const next = new Set(prev[questionId] ?? [])
      if (next.has(optionId)) next.delete(optionId)
      else next.add(optionId)
      return { ...prev, [questionId]: next }
    })
  }

  if (view === 'loading') {
    return <Skeleton className="h-96 max-w-2xl" />
  }

  if (view === 'error') {
    return (
      <div className="flex max-w-2xl flex-col gap-4">
        <BackLink />
        <Card>
          <CardHeader>
            <CardTitle>Can&apos;t open this test</CardTitle>
            <CardDescription>{errorMessage}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  if (view === 'results') {
    const shown = result ?? pastAttempts[0]
    if (!shown) {
      return (
        <div className="flex max-w-2xl flex-col gap-4">
          <BackLink />
          <Card>
            <CardHeader>
              <CardTitle>Not open</CardTitle>
              <CardDescription>This test isn&apos;t open right now, and you have no attempts yet.</CardDescription>
            </CardHeader>
          </Card>
        </div>
      )
    }
    return (
      <div className="flex max-w-2xl flex-col gap-4">
        <BackLink />
        <ResultCard attempt={shown} />
        {pastAttempts.length > 1 && (
          <p className="text-sm text-muted-foreground">
            You have {pastAttempts.length} attempts on this test. Showing the most recent.
          </p>
        )}
      </div>
    )
  }

  if (!paper) return null

  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <BackLink />

      <div className="sticky top-0 z-10 flex items-center justify-between rounded-lg border bg-card px-4 py-3 shadow-sm">
        <div>
          <h1 className="font-semibold">{paper.title}</h1>
          <p className="text-xs text-muted-foreground">
            Attempt {paper.attemptNo} of {paper.attemptsAllowed} &middot; {paper.totalMarks} marks
          </p>
        </div>
        <Badge variant={secondsRemaining < 60 ? 'destructive' : 'secondary'} className="font-mono text-sm">
          {formatClock(secondsRemaining)}
        </Badge>
      </div>

      {paper.instructions && <p className="text-sm text-muted-foreground">{paper.instructions}</p>}

      {paper.questions.map((question, qIndex) => (
        <Card key={question.id}>
          <CardHeader>
            <CardTitle className="text-base font-medium">
              {qIndex + 1}. {question.questionText}
            </CardTitle>
            <CardDescription>
              {question.marks} {question.marks === 1 ? 'mark' : 'marks'}
              {question.type === 'MULTI_CHOICE' && ' · choose all that apply'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {question.type === 'MULTI_CHOICE' ? (
              <div className="flex flex-col gap-2">
                {question.options.map((option) => (
                  <label key={option.id} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={answers[question.id]?.has(option.id) ?? false}
                      onCheckedChange={() => toggleMulti(question.id, option.id)}
                    />
                    {option.optionText}
                  </label>
                ))}
              </div>
            ) : (
              <RadioGroup
                value={[...(answers[question.id] ?? [])][0]?.toString() ?? ''}
                onValueChange={(value) => selectSingle(question.id, Number(value))}
              >
                {question.options.map((option) => (
                  <label key={option.id} className="flex items-center gap-2 text-sm">
                    <RadioGroupItem value={option.id.toString()} />
                    {option.optionText}
                  </label>
                ))}
              </RadioGroup>
            )}
          </CardContent>
        </Card>
      ))}

      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {Object.keys(answers).length} of {paper.questions.length} answered
        </p>
        <Button onClick={() => void submitNow()} disabled={submitting}>
          {submitting ? 'Submitting...' : 'Submit test'}
        </Button>
      </div>
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/app/assessments" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ChevronLeft className="size-4" />
      Tests
    </Link>
  )
}

function ResultCard({ attempt }: { attempt: AttemptResultResponse }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>{attempt.quizTitle}</CardTitle>
          {attempt.resultVisible && attempt.passed != null && (
            <Badge variant={attempt.passed ? 'default' : 'destructive'} className="gap-1">
              {attempt.passed ? <CheckCircle2 className="size-3.5" /> : <XCircle className="size-3.5" />}
              {attempt.passed ? 'Passed' : 'Not passed'}
            </Badge>
          )}
        </div>
        <CardDescription>
          Attempt {attempt.attemptNo} &middot; {attempt.status}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {attempt.resultVisible ? (
          <p className="text-2xl font-semibold">
            {attempt.score} / {attempt.totalMarks}{' '}
            <span className="text-base font-normal text-muted-foreground">({attempt.percentage}%)</span>
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">
            Your answers were received. The trainer hasn&apos;t released results for this test yet.
          </p>
        )}

        {attempt.answers?.map((a, i) => (
          <div key={a.questionId} className="border-t pt-3 text-sm">
            <p className="font-medium">
              {i + 1}. {a.questionText}
            </p>
            <div className="mt-1 flex flex-col gap-1">
              {a.options.map((option) => {
                const wasSelected = a.selectedOptionIds.includes(option.id)
                const isCorrect = a.correctOptionIds.includes(option.id)
                return (
                  <span
                    key={option.id}
                    className={
                      isCorrect
                        ? 'text-emerald-600'
                        : wasSelected
                          ? 'text-destructive'
                          : 'text-muted-foreground'
                    }
                  >
                    {wasSelected ? '✓ ' : '○ '}
                    {option.optionText}
                    {isCorrect && ' (correct)'}
                  </span>
                )
              })}
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {a.marksAwarded}/{a.marks} marks
            </p>
            {a.explanation && <p className="mt-1 text-xs text-muted-foreground italic">{a.explanation}</p>}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
