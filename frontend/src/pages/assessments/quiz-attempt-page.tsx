import { useCallback, useEffect, useRef, useState } from 'react'
import { CheckCircle2, ChevronLeft, ShieldAlert, XCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import {
  type AttemptResultResponse,
  type AttemptViewResponse,
  availableQuizzes,
  getAttemptResult,
  getMyAttempts,
  reportViolation,
  type StudentQuizResponse,
  type SubmitAnswer,
  saveAttemptAnswers,
  startOrResumeAttempt,
  submitAttempt,
  type ViolationType,
} from '@/api/assessments'
import { apiErrorMessage } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { CameraCheck, CameraPreview } from '@/components/camera-panel'
import type { CodeLanguageCode } from '@/api/code'
import { CodingQuestion } from '@/components/coding-question'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import { CAMERA_PROBLEM_TEXT, CameraError, openCamera, stopStream } from '@/lib/face-detector'
import { useCameraMonitor } from '@/lib/use-camera-monitor'
import { enterFullscreen, leaveFullscreen, useTestGuard } from '@/lib/use-test-guard'
import { cn } from '@/lib/utils'

const AUTOSAVE_INTERVAL_MS = 20_000

type Answers = Record<number, Set<number>>
type Languages = Record<number, CodeLanguageCode>
type Texts = Record<number, string>
type Tested = Record<number, { passed: number; total: number }>

/**
 * Everything the student has answered, in the shape the API takes. A coding box still holding its untouched
 * starter code is not an answer, and neither is blank text; choice questions send options, the rest text.
 */
function toSubmitAnswers(
  paper: AttemptViewResponse,
  answers: Answers,
  texts: Texts,
  languages: Languages = {},
): SubmitAnswer[] {
  const submit: SubmitAnswer[] = []
  for (const question of paper.questions) {
    if (question.type === 'SHORT_ANSWER' || question.type === 'CODING') {
      const text = texts[question.id] ?? ''
      const untouched = question.type === 'CODING' && text === (question.starterCode ?? '')
      const chosen = question.allowLanguageChoice ? languages[question.id] : undefined
      if (text.trim() && !untouched) submit.push({ questionId: question.id, answerText: text, codeLanguage: chosen })
    } else {
      const options = answers[question.id]
      if (options) submit.push({ questionId: question.id, selectedOptionIds: [...options] })
    }
  }
  return submit
}

function countAnswered(paper: AttemptViewResponse, answers: Answers, texts: Texts): number {
  return toSubmitAnswers(paper, answers, texts).filter((a) => (a.selectedOptionIds?.length ?? 0) > 0 || a.answerText).length
}

function ordinal(n: number): string {
  const suffix = n % 100 >= 11 && n % 100 <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' } as Record<number, string>)[n % 10] ?? 'th'
  return `${n}${suffix}`
}

function formatClock(totalSeconds: number): string {
  const m = Math.floor(Math.max(0, totalSeconds) / 60)
  const s = Math.max(0, totalSeconds) % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function QuizAttemptPage() {
  const { quizId } = useParams<{ quizId: string }>()

  const [view, setView] = useState<'loading' | 'intro' | 'live' | 'results' | 'error'>('loading')
  const [intro, setIntro] = useState<StudentQuizResponse | null>(null)
  const [warning, setWarning] = useState<string | null>(null)
  const [needFullscreen, setNeedFullscreen] = useState(false)
  const [paper, setPaper] = useState<AttemptViewResponse | null>(null)
  const [answers, setAnswers] = useState<Answers>({})
  const [secondsRemaining, setSecondsRemaining] = useState(0)
  const [pastAttempts, setPastAttempts] = useState<AttemptResultResponse[]>([])
  const [result, setResult] = useState<AttemptResultResponse | null>(null)
  const [errorMessage, setErrorMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [texts, setTexts] = useState<Texts>({})
  const [languages, setLanguages] = useState<Languages>({})
  const [tested, setTested] = useState<Tested>({})
  const answersRef = useRef<Answers>({})
  const textsRef = useRef<Texts>({})
  const languagesRef = useRef<Languages>({})
  useEffect(() => {
    answersRef.current = answers
    textsRef.current = texts
    languagesRef.current = languages
  }, [answers, texts, languages])

  const submitNow = useCallback(async () => {
    if (!paper || submitting) return
    setSubmitting(true)
    try {
      const outcome = await submitAttempt(
        paper.attemptId,
        toSubmitAnswers(paper, answersRef.current, textsRef.current, languagesRef.current),
      )
      setResult(outcome)
      setView('results')
      toast.success('Test submitted')
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not submit the test. Your saved answers are still safe.'))
      setSubmitting(false)
    }
  }, [paper, submitting])

  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  // Starts (or resumes) an attempt; a test that is no longer open falls back to past results.
  const startAttempt = useCallback(() => {
    if (!quizId) return
    setView('loading')
    startOrResumeAttempt(quizId)
      .then((view) => {
        if (!mounted.current) return
        setPaper(view)
        setSecondsRemaining(view.secondsRemaining)
        // A resumed sitting shows what was already saved, so nothing looks lost after a reload.
        const restoredAnswers: Answers = {}
        const restoredTexts: Texts = {}
        const restoredTests: Tested = {}
        const restoredLanguages: Languages = {}
        for (const saved of view.savedAnswers) {
          if (saved.selectedOptionIds.length > 0) restoredAnswers[saved.questionId] = new Set(saved.selectedOptionIds)
          if (saved.answerText) restoredTexts[saved.questionId] = saved.answerText
          if (saved.codeLanguage) restoredLanguages[saved.questionId] = saved.codeLanguage
          if (saved.testsTotal != null) {
            restoredTests[saved.questionId] = { passed: saved.testsPassed ?? 0, total: saved.testsTotal }
          }
        }
        for (const question of view.questions) {
          if (question.type === 'CODING' && restoredTexts[question.id] === undefined) {
            restoredTexts[question.id] = question.starterCode ?? ''
          }
        }
        setAnswers(restoredAnswers)
        setTexts(restoredTexts)
        setLanguages(restoredLanguages)
        setTested(restoredTests)
        setView('live')
      })
      .catch(() => {
        getMyAttempts(quizId)
          .then((attempts) => {
            if (!mounted.current) return
            setPastAttempts(attempts)
            setView('results')
          })
          .catch((error: unknown) => {
            if (!mounted.current) return
            setErrorMessage(apiErrorMessage(error, 'This test is not available to you.'))
            setView('error')
          })
      })
  }, [quizId])

  // A secure test asks for a click first: browsers only allow fullscreen from one, and the student should
  // read the rules before the clock starts. Anything else goes straight in.
  useEffect(() => {
    if (!quizId) return
    setView('loading')
    availableQuizzes()
      .then((list) => {
        if (!mounted.current) return
        const quiz = list.find((q) => String(q.id) === quizId)
        if ((quiz?.secureMode || quiz?.requireCamera) && (quiz.canStart || quiz.inProgressAttemptId)) {
          setIntro(quiz)
          setView('intro')
        } else {
          startAttempt()
        }
      })
      .catch(() => startAttempt())
  }, [quizId, startAttempt])

  // Secure mode: the browser reports, the server counts. A warning is shown; the report that ends the
  // attempt takes the student to their (failed) result.
  const secure = view === 'live' && paper?.secureMode === true
  const handleViolation = useCallback(
    (type: ViolationType, detail?: string) => {
      if (!paper) return
      reportViolation(paper.attemptId, type, detail)
        .then((outcome) => {
          if (outcome.terminated) {
            getAttemptResult(paper.attemptId)
              .then((ended) => {
                setResult(ended)
                setWarning(null)
                setView('results')
              })
              .catch(() => undefined)
          } else if (outcome.message) {
            setWarning(outcome.message)
          }
        })
        .catch(() => undefined)
    },
    [paper],
  )
  useTestGuard({
    enabled: secure,
    onViolation: handleViolation,
    onFullscreenChange: (fullscreen) => setNeedFullscreen(!fullscreen && document.fullscreenEnabled),
  })

  // Camera tests: the camera is opened on the rules screen (that needs a click) and kept for the sitting.
  // Each problem the monitor notices goes to the server the same way a window event does, and the server
  // answers with the warning to show.
  const [cameraStream, setCameraStream] = useState<MediaStream | null>(null)
  const [cameraReady, setCameraReady] = useState(false)
  const videoRef = useRef<HTMLVideoElement>(null)
  const watchingCamera = view === 'live' && paper?.requireCamera === true
  const cameraStatus = useCameraMonitor({
    enabled: watchingCamera,
    stream: cameraStream,
    video: videoRef,
    onEvent: handleViolation,
  })
  const cameraBlocked =
    watchingCamera && (!cameraStream || cameraStatus === 'off' || cameraStatus === 'denied')

  async function reopenCamera() {
    try {
      setCameraStream(await openCamera())
    } catch (error) {
      toast.error(CAMERA_PROBLEM_TEXT[error instanceof CameraError ? error.problem : 'other'])
    }
  }

  // The camera belongs to the sitting: let it go when the page is left, replaced or finished.
  useEffect(() => () => stopStream(cameraStream), [cameraStream])
  useEffect(() => {
    if (view === 'results') stopStream(cameraStream)
  }, [view, cameraStream])

  // Leave fullscreen once the test is over, however it ended.
  useEffect(() => {
    if (view === 'results') void leaveFullscreen()
  }, [view])

  // The countdown, and autosave every 20s while it runs.
  useEffect(() => {
    if (view !== 'live' || !paper) return
    const tick = setInterval(() => setSecondsRemaining((s) => Math.max(0, s - 1)), 1000)
    const save = setInterval(() => {
      void saveAttemptAnswers(
        paper.attemptId,
        toSubmitAnswers(paper, answersRef.current, textsRef.current, languagesRef.current),
      ).catch(
        () => undefined,
      )
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

  if (view === 'intro' && intro) {
    const resuming = intro.inProgressAttemptId != null
    return (
      <div className="flex max-w-2xl flex-col gap-4">
        <BackLink />
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ShieldAlert className="size-5" />
              {intro.title}
            </CardTitle>
            <CardDescription>
              {intro.secureMode ? 'This is a secure test.' : 'This test uses your camera.'} {intro.durationMinutes}{' '}
              minutes &middot; {intro.totalMarks} marks
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 text-sm">
            <ul className="flex list-disc flex-col gap-1 pl-5">
              <li>The clock {resuming ? 'is already running' : 'starts as soon as you begin'}.</li>
              {intro.secureMode && (
                <>
                  <li>The test opens in fullscreen.</li>
                  <li>
                    Do not switch to another tab or window.{' '}
                    {intro.maxViolations <= 1
                      ? 'Doing so ends your test at once.'
                      : `The first time you get a warning; the ${ordinal(intro.maxViolations)} time ends your test and it is marked failed.`}
                  </li>
                  <li>Copying, right-click and shortcuts such as print and developer tools are blocked.</li>
                </>
              )}
              {intro.requireCamera && (
                <li>
                  Your camera must stay on with your face visible. You are warned if it is not; nothing is failed
                  automatically for this, but it is recorded for your trainer.
                </li>
              )}
              <li>Everything above is recorded, with the time, for your trainer to review.</li>
            </ul>
            {intro.instructions && <p className="text-muted-foreground">{intro.instructions}</p>}
            {intro.requireCamera && (
              <CameraCheck stream={cameraStream} onStream={setCameraStream} onReady={setCameraReady} />
            )}
            <Button
              className="self-start"
              disabled={intro.requireCamera && !cameraReady}
              onClick={() => {
                // Fullscreen must be asked for from this click, so it comes first.
                void (intro.secureMode ? enterFullscreen() : Promise.resolve(true)).then(() => startAttempt())
              }}
            >
              {resuming ? 'Resume test' : 'Start test'}
            </Button>
            {intro.requireCamera && !cameraReady && (
              <p className="text-xs text-muted-foreground">
                You can start once your camera is on and your face is visible.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    )
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
    <div className={cn('flex max-w-2xl flex-col gap-4', paper.secureMode && 'select-none')}>
      <BackLink />

      {warning && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="alertdialog" aria-modal>
          <Card className="w-full max-w-md border-destructive">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-destructive">
                <ShieldAlert className="size-5" />
                {warning}
              </CardTitle>
              <CardDescription>Your time is still running. Go back to your answers.</CardDescription>
            </CardHeader>
            <CardContent>
              <Button onClick={() => setWarning(null)}>I understand, continue the test</Button>
            </CardContent>
          </Card>
        </div>
      )}

      {paper.requireCamera && cameraStream && (
        <CameraPreview stream={cameraStream} video={videoRef} status={cameraStatus} />
      )}

      {cameraBlocked && !warning && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-background p-4" role="alertdialog" aria-modal>
          <Card className="w-full max-w-md border-destructive">
            <CardHeader>
              <CardTitle className="text-destructive">Your camera is off</CardTitle>
              <CardDescription>
                This test needs your camera on. Turn it back on to continue. Your time is still running.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button onClick={() => void reopenCamera()}>Turn camera on</Button>
            </CardContent>
          </Card>
        </div>
      )}

      {needFullscreen && !warning && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-background p-4" role="alertdialog" aria-modal>
          <Card className="w-full max-w-md">
            <CardHeader>
              <CardTitle>Return to fullscreen</CardTitle>
              <CardDescription>This test must be taken in fullscreen. Your time is still running.</CardDescription>
            </CardHeader>
            <CardContent>
              <Button
                onClick={() => {
                  void enterFullscreen().then((ok) => ok && setNeedFullscreen(false))
                }}
              >
                Go fullscreen
              </Button>
            </CardContent>
          </Card>
        </div>
      )}

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
              {question.type === 'SHORT_ANSWER' && ' · type your answer'}
              {question.type === 'CODING' && ' · write code'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {question.type === 'SHORT_ANSWER' ? (
              <Input
                aria-label={`Answer to question ${qIndex + 1}`}
                value={texts[question.id] ?? ''}
                maxLength={1000}
                autoComplete="off"
                onChange={(event) => setTexts((prev) => ({ ...prev, [question.id]: event.target.value }))}
              />
            ) : question.type === 'CODING' ? (
              <CodingQuestion
                attemptId={paper.attemptId}
                question={question}
                code={texts[question.id] ?? question.starterCode ?? ''}
                onChange={(code) => setTexts((prev) => ({ ...prev, [question.id]: code }))}
                chosenLanguage={languages[question.id]}
                onLanguageChange={(language) => setLanguages((prev) => ({ ...prev, [question.id]: language }))}
                savedPassed={tested[question.id]?.passed ?? null}
                savedTotal={tested[question.id]?.total ?? null}
                onTested={(questionId, passed, total) =>
                  setTested((prev) => ({ ...prev, [questionId]: { passed, total } }))
                }
              />
            ) : question.type === 'MULTI_CHOICE' ? (
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
          {countAnswered(paper, answers, texts)} of {paper.questions.length} answered
        </p>
        <Button onClick={() => void submitNow()} disabled={submitting}>
          {submitting ? 'Submitting (running your tests)...' : 'Submit test'}
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
        {attempt.status === 'TERMINATED' && (
          <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            This attempt was ended: {attempt.terminatedReason ?? 'the secure-test rules were broken'}. It is marked as
            failed.
          </p>
        )}
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
            {a.type === 'SHORT_ANSWER' && (
              <div className="mt-1 flex flex-col gap-1">
                <span className={a.marksAwarded > 0 ? 'text-emerald-600' : 'text-destructive'}>
                  Your answer: {a.answerText || '(none)'}
                </span>
                {a.marksAwarded === 0 && a.acceptedAnswers.length > 0 && (
                  <span className="text-muted-foreground">Accepted: {a.acceptedAnswers.join(' / ')}</span>
                )}
              </div>
            )}
            {a.type === 'CODING' && (
              <div className="mt-1 flex flex-col gap-1">
                <span className={a.testsPassed === a.testsTotal ? 'text-emerald-600' : 'text-destructive'}>
                  {a.testsTotal == null
                    ? 'Your tests were not run.'
                    : `${a.testsPassed ?? 0} of ${a.testsTotal} tests passed`}
                </span>
                {a.answerText && (
                  <pre className="max-h-48 overflow-auto rounded-md border bg-muted/40 px-3 py-2 font-mono text-xs whitespace-pre-wrap">
                    {a.answerText}
                  </pre>
                )}
              </div>
            )}
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
