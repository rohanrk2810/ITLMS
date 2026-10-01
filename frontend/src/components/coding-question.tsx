import { lazy, Suspense, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, Loader2, Play, XCircle } from 'lucide-react'

import { type AttemptQuestion, type CodingRunResponse, runCodingTests } from '@/api/assessments'
import { apiErrorMessage } from '@/api/client'
import { type CodeLanguageCode, codeLanguageLabel } from '@/api/code'
import { CodeResultPanel } from '@/components/code-result-panel'
import { LanguageSelect } from '@/components/language-select'
import { Button } from '@/components/ui/button'
import { CLIPBOARD_OK_ATTRIBUTE } from '@/lib/use-test-guard'
import { cn } from '@/lib/utils'

// Monaco is several MB, so it loads only when a test with a coding question is opened.
const CodeEditor = lazy(() => import('@/components/code-editor'))

interface CodingQuestionProps {
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
}

/**
 * A coding question inside a test: the editor, the sample cases, and a button that runs every case,
 * hidden ones included. Hidden cases come back as pass or fail only.
 */
export function CodingQuestion({
  attemptId,
  question,
  code,
  onChange,
  chosenLanguage,
  onLanguageChange,
  savedPassed,
  savedTotal,
  onTested,
}: CodingQuestionProps) {
  const [result, setResult] = useState<CodingRunResponse | null>(null)
  /** The code the tests ran against, so the complexity estimate matches the results shown. */
  const [ranCode, setRanCode] = useState('')
  const [runError, setRunError] = useState<string | null>(null)
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

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
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
          <span className="text-xs text-muted-foreground">{codeLanguageLabel(language)}</span>
        )}
        <span className="text-xs text-muted-foreground">
          {question.allowLanguageChoice && 'You may answer in any language. '}
          {question.hiddenTestCount > 0 && ` · ${question.hiddenTestCount} hidden test${question.hiddenTestCount === 1 ? '' : 's'}`}
        </span>
        <Button type="button" size="sm" onClick={() => mutation.mutate()} disabled={mutation.isPending || !code.trim()}>
          {mutation.isPending ? <Loader2 className="animate-spin" /> : <Play />}
          {mutation.isPending ? 'Running tests...' : 'Run tests'}
        </Button>
      </div>

      {question.sampleTests.length > 0 && (
        <div className="flex flex-col gap-2">
          <span className="text-xs font-medium">Examples</span>
          {question.sampleTests.map((sample, i) => (
            <div key={i} className="grid gap-2 text-xs sm:grid-cols-2">
              <Labelled label="Input" text={sample.input} />
              <Labelled label="Expected output" text={sample.expectedOutput} />
            </div>
          ))}
        </div>
      )}

      {/* A secure test blocks copying everywhere except here: writing code needs it. Pasting is recorded. */}
      <div {...{ [CLIPBOARD_OK_ATTRIBUTE]: '' }}>
        <Suspense fallback={<div className="h-72 rounded-md border bg-muted/30" aria-busy />}>
          <CodeEditor
            language={language}
            value={code}
            onChange={onChange}
            onRun={() => !mutation.isPending && code.trim() && mutation.mutate()}
            height={288}
            ariaLabel={`${codeLanguageLabel(language)} answer`}
          />
        </Suspense>
      </div>

      {runError && (
        <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {runError}
        </p>
      )}

      {result ? (
        <>
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
        </>
      ) : (
        savedTotal != null && (
          <p className="text-xs text-muted-foreground">
            Last run: {savedPassed ?? 0} of {savedTotal} tests passed. Run the tests again after changing your code.
          </p>
        )
      )}
    </div>
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
      <div className="mb-0.5 text-muted-foreground">{label}</div>
      <pre className="max-h-32 overflow-auto rounded-md border bg-muted/40 px-2 py-1 font-mono whitespace-pre-wrap">
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
