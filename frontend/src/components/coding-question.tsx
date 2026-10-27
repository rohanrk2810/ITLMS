import { type CSSProperties, lazy, type PointerEvent as ReactPointerEvent, type ReactNode, Suspense, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, ChevronsLeftRightEllipsis, CircleCheck, FileText, Loader2, Play, SquareTerminal, XCircle } from 'lucide-react'

import { type AttemptQuestion, type CodingRunResponse, runCodingTests } from '@/api/assessments'
import { apiErrorMessage } from '@/api/client'
import { type CodeLanguageCode, codeLanguageLabel } from '@/api/code'
import { CodeResultPanel } from '@/components/code-result-panel'
import { LanguageSelect } from '@/components/language-select'
import { FULLSCREEN_PANEL_CLASS, FullscreenToggle, useFullscreenPanel } from '@/components/fullscreen-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { CLIPBOARD_OK_ATTRIBUTE } from '@/lib/use-test-guard'
import { cn } from '@/lib/utils'

// Monaco is several MB, so it loads only when a test with a coding question is opened.
const CodeEditor = lazy(() => import('@/components/code-editor'))

interface CodingQuestionProps {
  /** The question's number in the test, shown in the heading. */
  number: number
  attemptId: number
  question: AttemptQuestion
  code: string
  onChange: (code: string) => void
  /** The language the student picked, when the question allows a choice. */
  chosenLanguage?: CodeLanguageCode
  onLanguageChange: (language: CodeLanguageCode) => void
  /** Last known result, from a saved answer, shown until the student runs the tests again. */
  savedPassed: number | null
  savedTotal: number | null
  /** Told after each run, so the page can show progress for every coding question. */
  onTested: (questionId: number, passed: number, total: number) => void
  /** Shown in full screen, where the test's own header (with the clock) is covered. */
  clock?: ReactNode
}

/**
 * A coding question inside a test, laid out as a workspace: the problem on the left, the editor on the right with
 * the sample cases and the run results underneath. Both dividers can be dragged. The Run button runs every case,
 * hidden ones included; hidden cases come back as pass or fail only.
 */
export function CodingQuestion({
  number,
  attemptId,
  question,
  code,
  onChange,
  chosenLanguage,
  onLanguageChange,
  savedPassed,
  savedTotal,
  onTested,
  clock,
}: CodingQuestionProps) {
  const [full, setFull] = useState(false)
  useFullscreenPanel(full, () => setFull(false))
  const [result, setResult] = useState<CodingRunResponse | null>(null)
  /** The code the tests ran against, so the complexity estimate matches the results shown. */
  const [ranCode, setRanCode] = useState('')
  const [runError, setRunError] = useState<string | null>(null)
  const [bottomTab, setBottomTab] = useState<'cases' | 'result'>('cases')
  const [activeCase, setActiveCase] = useState(0)
  const across = useSplit(42, 25, 70, 'x')
  const down = useSplit(62, 25, 80, 'y')
  const language = question.allowLanguageChoice && chosenLanguage ? chosenLanguage : question.codeLanguage

  const mutation = useMutation({
    mutationFn: async () => {
      const submitted = code
      const picked = question.allowLanguageChoice ? chosenLanguage : undefined
      return { response: await runCodingTests(attemptId, question.id, submitted, picked), submitted }
    },
    onMutate: () => {
      setRunError(null)
    },
    onSuccess: ({ response, submitted }) => {
      setResult(response)
      setRanCode(submitted)
      onTested(question.id, response.passed, response.total)
    },
    onError: (error) => setRunError(apiErrorMessage(error, 'Could not run the tests. Your code is kept; try again.')),
  })

  if (!language) {
    return <p className="text-sm text-destructive">This question has no language set. Tell your trainer.</p>
  }

  const run = () => {
    if (mutation.isPending || !code.trim()) return
    setBottomTab('result')
    mutation.mutate()
  }
  const sample = question.sampleTests[Math.min(activeCase, Math.max(0, question.sampleTests.length - 1))]

  return (
    <div
      ref={across.box}
      style={{ '--left': `${across.ratio}%` } as CSSProperties}
      className={cn(
        'flex flex-col gap-1.5 bg-muted p-1.5 lg:flex-row lg:gap-0',
        full ? `${FULLSCREEN_PANEL_CLASS} lg:overflow-hidden` : 'rounded-xl lg:h-[calc(100vh-11rem)] lg:min-h-[560px]',
      )}
    >
      {/* Left: the problem */}
      <section className="flex h-[420px] min-h-0 flex-col overflow-hidden rounded-lg bg-card lg:h-auto lg:flex-none lg:basis-[var(--left)]">
        <PanelTitle icon={<FileText className="size-4 text-sky-600" />} title="Description" />
        <div className="flex-1 overflow-y-auto px-5 py-4">
          <h2 className="text-xl font-semibold whitespace-pre-wrap">
            {number}. {question.questionText}
          </h2>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Badge variant="secondary">
              {question.marks} {question.marks === 1 ? 'mark' : 'marks'}
            </Badge>
            <Badge variant="outline">{codeLanguageLabel(language)}</Badge>
            {question.hiddenTestCount > 0 && (
              <Badge variant="outline">
                {question.hiddenTestCount} hidden test{question.hiddenTestCount === 1 ? '' : 's'}
              </Badge>
            )}
            {question.allowLanguageChoice && <Badge variant="outline">Any language</Badge>}
          </div>

          {language === 'SQL' && question.setupSql && (
            <div className="mt-5 flex flex-col gap-1.5">
              <span className="text-sm font-semibold">Database</span>
              <p className="text-xs text-muted-foreground">Your query runs against these tables and rows.</p>
              <pre className="max-h-64 overflow-auto rounded-lg bg-muted px-3 py-2 font-mono text-xs whitespace-pre-wrap">
                {question.setupSql}
              </pre>
            </div>
          )}

          {question.sampleTests.map((example, i) => (
            <div key={i} className="mt-5 flex flex-col gap-2 text-sm">
              <span className="font-semibold">Example {i + 1}:</span>
              <div className="flex flex-col gap-1.5 border-l-2 pl-3">
                {language !== 'SQL' && (
                  <p>
                    <span className="font-medium">Input: </span>
                    <Chip text={example.input} />
                  </p>
                )}
                <p>
                  <span className="font-medium">Output: </span>
                  <Chip text={example.expectedOutput} />
                </p>
              </div>
            </div>
          ))}
        </div>
        <div className="flex items-center gap-3 border-t px-4 py-2 text-xs text-muted-foreground">
          {result ? (
            <span className={cn('flex items-center gap-1', result.passed === result.total ? 'text-emerald-600' : 'text-destructive')}>
              <CircleCheck className="size-3.5" />
              {result.passed}/{result.total} passed
            </span>
          ) : savedTotal != null ? (
            <span className="flex items-center gap-1">
              <CircleCheck className="size-3.5" />
              Last run: {savedPassed ?? 0}/{savedTotal} passed
            </span>
          ) : (
            <span>Not run yet</span>
          )}
        </div>
      </section>

      {/* Divider between the problem and the code */}
      <div
        role="separator"
        aria-orientation="vertical"
        onPointerDown={across.start}
        className="group hidden w-1.5 flex-none cursor-col-resize items-center justify-center lg:flex"
      >
        <span className="h-10 w-0.5 rounded bg-border group-hover:bg-primary" />
      </div>

      {/* Right: the code, then the test cases and results */}
      <div ref={down.box} className="flex h-[680px] min-h-0 flex-col gap-1.5 lg:h-auto lg:min-w-0 lg:flex-1 lg:gap-0">
        <section
          style={{ flex: `0 0 ${down.ratio}%` }}
          className="flex min-h-0 flex-col overflow-hidden rounded-lg bg-card max-lg:!flex-1"
        >
          <PanelTitle icon={<ChevronsLeftRightEllipsis className="size-4 text-emerald-600" />} title="Code" />
          <div className="flex flex-wrap items-center gap-2 border-b px-3 py-1.5">
            {question.allowLanguageChoice ? (
              <LanguageSelect
                value={language}
                disabled={mutation.isPending}
                onChange={(next) => {
                  // The earlier results were for another language, so they no longer describe this code.
                  setResult(null)
                  onLanguageChange(next)
                }}
              />
            ) : (
              <span className="text-sm text-muted-foreground">{codeLanguageLabel(language)}</span>
            )}
            <span className="ml-auto hidden text-xs text-muted-foreground sm:inline">Ctrl + Enter to run</span>
            {full && clock}
            <FullscreenToggle full={full} onToggle={() => setFull((value) => !value)} />
            <Button type="button" size="sm" onClick={run} disabled={mutation.isPending || !code.trim()}>
              {mutation.isPending ? <Loader2 className="animate-spin" /> : <Play />}
              {mutation.isPending ? 'Running...' : 'Run'}
            </Button>
          </div>
          {/* A secure test blocks copying everywhere except here: writing code needs it. Pasting is recorded. */}
          <div className="min-h-[220px] flex-1" {...{ [CLIPBOARD_OK_ATTRIBUTE]: '' }}>
            <Suspense fallback={<div className="size-full bg-muted/30" aria-busy />}>
              <CodeEditor
                fill
                language={language}
                value={code}
                onChange={onChange}
                onRun={run}
                suggestions={question.allowSuggestions}
                ariaLabel={`${codeLanguageLabel(language)} answer`}
              />
            </Suspense>
          </div>
        </section>

        <div
          role="separator"
          aria-orientation="horizontal"
          onPointerDown={down.start}
          className="group hidden h-1.5 flex-none cursor-row-resize items-center justify-center lg:flex"
        >
          <span className="h-0.5 w-10 rounded bg-border group-hover:bg-primary" />
        </div>

        <section className="flex min-h-[260px] flex-1 flex-col overflow-hidden rounded-lg bg-card lg:min-h-0">
          <div className="flex items-center gap-1 border-b px-2">
            <TabButton active={bottomTab === 'cases'} onClick={() => setBottomTab('cases')} icon={<CheckCircle2 className="size-4 text-emerald-600" />}>
              Testcase
            </TabButton>
            <TabButton active={bottomTab === 'result'} onClick={() => setBottomTab('result')} icon={<SquareTerminal className="size-4 text-emerald-600" />}>
              Test Result
            </TabButton>
          </div>
          <div className="flex-1 overflow-y-auto p-4">
            {bottomTab === 'cases' ? (
              !sample ? (
                <p className="text-sm text-muted-foreground">
                  This question has no sample cases to show. Run your code to check it against the tests.
                </p>
              ) : (
                <div className="flex flex-col gap-3">
                  <div className="flex flex-wrap gap-2">
                    {question.sampleTests.map((_, i) => (
                      <button
                        key={i}
                        type="button"
                        onClick={() => setActiveCase(i)}
                        className={cn(
                          'rounded-lg px-3 py-1 text-sm',
                          i === activeCase ? 'bg-muted font-medium' : 'text-muted-foreground hover:bg-muted/60',
                        )}
                      >
                        Case {i + 1}
                      </button>
                    ))}
                  </div>
                  {language !== 'SQL' && <Labelled label="Input" text={sample.input} />}
                  <Labelled label="Expected output" text={sample.expectedOutput} />
                </div>
              )
            ) : mutation.isPending ? (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" />
                Running your code against every test...
              </p>
            ) : runError ? (
              <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {runError}
              </p>
            ) : result ? (
              <div className="flex flex-col gap-3">
                <RunResult result={result} />
                <CodeResultPanel
                  language={language}
                  ranCode={ranCode}
                  headlineLabel="Test Cases"
                  headline={`${result.passed}/${result.total} Passed`}
                  ok={result.passed === result.total}
                  timeSeconds={slowest(result.cases.map((c) => c.timeSeconds))}
                  memoryKb={slowest(result.cases.map((c) => c.memoryKb))}
                  cases={result.cases.map((c) => ({
                    number: c.number,
                    hidden: c.hidden,
                    passed: c.passed,
                    timeSeconds: c.timeSeconds,
                    memoryKb: c.memoryKb,
                  }))}
                  comparison={result.comparison}
                  analyse={!result.compileError}
                />
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                {savedTotal != null
                  ? `Last run: ${savedPassed ?? 0} of ${savedTotal} tests passed. Run again after changing your code.`
                  : 'Run your code to see the results here.'}
              </p>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

/** Drag-to-resize state for a split: the first part's share, in percent, of the box the divider is dragged in. */
function useSplit(initial: number, min: number, max: number, axis: 'x' | 'y') {
  const [ratio, setRatio] = useState(initial)
  const box = useRef<HTMLDivElement>(null)
  const start = (event: ReactPointerEvent) => {
    event.preventDefault()
    const element = box.current
    if (!element) return
    const move = (e: PointerEvent) => {
      const rect = element.getBoundingClientRect()
      const share = axis === 'x' ? (e.clientX - rect.left) / rect.width : (e.clientY - rect.top) / rect.height
      setRatio(Math.min(max, Math.max(min, share * 100)))
    }
    const stop = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
      document.body.style.userSelect = ''
    }
    document.body.style.userSelect = 'none'
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
  }
  return { ratio, box, start }
}

function PanelTitle({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className="flex items-center gap-2 border-b px-4 py-2 text-sm font-medium">
      {icon}
      {title}
    </div>
  )
}

function TabButton({ active, onClick, icon, children }: { active: boolean; onClick: () => void; icon: ReactNode; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        '-mb-px flex items-center gap-2 border-b-2 px-3 py-2 text-sm',
        active ? 'border-foreground font-medium' : 'border-transparent text-muted-foreground hover:text-foreground',
      )}
    >
      {icon}
      {children}
    </button>
  )
}

/** A value shown the way problem statements show inputs and outputs: small, grey, monospace. */
function Chip({ text }: { text: string | null }) {
  return (
    <code className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[13px] whitespace-pre-wrap">
      {text === '' || text == null ? '(empty)' : text}
    </code>
  )
}

/** The largest of the measurements that exist: each test runs on its own, so the worst one is the one to watch. */
function slowest(values: Array<number | null>): number | null {
  const present = values.filter((v): v is number => v != null)
  return present.length === 0 ? null : Math.max(...present)
}

function Labelled({ label, text }: { label: string; text: string | null }) {
  return (
    <div>
      <div className="mb-1 text-xs text-muted-foreground">{label}</div>
      <pre className="max-h-40 overflow-auto rounded-lg bg-muted px-3 py-2.5 font-mono text-sm whitespace-pre-wrap">
        {text === '' || text == null ? '(empty)' : text}
      </pre>
    </div>
  )
}

function RunResult({ result }: { result: CodingRunResponse }) {
  const allPassed = result.passed === result.total
  return (
    <div className="flex flex-col gap-2" role="status">
      <p className={cn('text-sm font-medium', allPassed ? 'text-emerald-600' : 'text-destructive')}>
        {result.passed} of {result.total} tests passed
      </p>
      {result.compileError && (
        <pre className="max-h-48 overflow-auto rounded-md border border-destructive/40 bg-muted/40 px-3 py-2 font-mono text-xs whitespace-pre-wrap text-destructive">
          {result.compileError}
        </pre>
      )}
      {result.cases.map((c) => (
        <div key={c.number} className="rounded-md border px-3 py-2 text-xs">
          <div className="flex items-center gap-2 font-medium">
            {c.passed ? <CheckCircle2 className="size-3.5 text-emerald-600" /> : <XCircle className="size-3.5 text-destructive" />}
            {c.hidden ? `Hidden test ${c.number}` : `Test ${c.number}`}: {c.passed ? 'passed' : 'failed'}
          </div>
          {!c.hidden && !c.passed && (
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              <Labelled label="Input" text={c.input} />
              <Labelled label="Expected output" text={c.expectedOutput} />
              <Labelled label="Your output" text={c.actualOutput} />
              {c.error && <p className="text-destructive">{c.error}</p>}
            </div>
          )}
          {c.hidden && !c.passed && c.error && <p className="mt-1 text-muted-foreground">{c.error}</p>}
        </div>
      ))}
    </div>
  )
}
