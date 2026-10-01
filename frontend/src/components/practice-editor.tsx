import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Loader2, Play, RotateCcw } from 'lucide-react'

import { apiErrorMessage } from '@/api/client'
import { codeLanguageLabel, type CodeLanguageCode, getCodeLanguages, runCode, type RunCodeResponse } from '@/api/code'
import { CodeResultPanel } from '@/components/code-result-panel'
import { LanguageSelect } from '@/components/language-select'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

// Monaco is several MB, so it loads only when a lesson with an editor is opened.
const CodeEditor = lazy(() => import('@/components/code-editor'))

/** Grows with the code, within a range that keeps the page usable. */
function editorHeight(lineCount: number): number {
  return Math.min(Math.max(lineCount, 8), 20) * 22 + 16
}

/** Read and written defensively: private browsing or blocked storage just means the draft is not kept. */
function loadDraft(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function saveDraft(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Not worth interrupting someone who is typing.
  }
}

function clearDraft(key: string) {
  try {
    localStorage.removeItem(key)
  } catch {
    // See saveDraft.
  }
}

interface PracticeEditorProps {
  language: CodeLanguageCode
  starterCode: string | null
  /** The author lets the student practise in another language. */
  allowLanguageChoice?: boolean
  /** Keeps each lesson's draft separate, so a half-written exercise survives leaving the page. Render with key={lessonId} so moving between lessons starts fresh. */
  lessonId: number
}

/**
 * A code editor and Run button for one lesson. It runs nothing itself: the code goes to
 * codeexec-service, which decides whether it may run and hands it to the sandbox.
 *
 * The editor is Monaco (the engine of VS Code) with snippets and keyword suggestions; see code-editor.tsx.
 * Ctrl/Cmd+Enter runs.
 */
export function PracticeEditor({ language: lessonLanguage, starterCode, lessonId, allowLanguageChoice = false }: PracticeEditorProps) {
  const [language, setLanguage] = useState<CodeLanguageCode>(lessonLanguage)
  // Each language keeps its own draft; the lesson's own language keeps the plain key, as before choice existed.
  const draftKey = language === lessonLanguage ? `itilms.practice.${lessonId}` : `itilms.practice.${lessonId}.${language}`
  // The starter code was written for the lesson's language only.
  const starter = language === lessonLanguage ? (starterCode ?? '') : ''

  const [source, setSource] = useState(() => loadDraft(draftKey) ?? starter)
  const [stdin, setStdin] = useState('')
  const [result, setResult] = useState<RunCodeResponse | null>(null)
  /** The code that produced `result`; the complexity estimate reads this, not whatever is typed since. */
  const [ranCode, setRanCode] = useState('')
  const [runError, setRunError] = useState<string | null>(null)

  const languagesQuery = useQuery({
    queryKey: ['code', 'languages'],
    queryFn: getCodeLanguages,
    staleTime: 5 * 60_000,
  })

  const mutation = useMutation({
    mutationFn: async () => {
      const code = source
      const response = await runCode({ language, sourceCode: code, stdin: stdin || undefined })
      return { response, code }
    },
    onMutate: () => {
      setResult(null)
      setRunError(null)
    },
    onSuccess: ({ response, code }) => {
      setResult(response)
      setRanCode(code)
    },
    onError: (error) => setRunError(apiErrorMessage(error, 'Could not run the code. Try again in a moment.')),
  })

  const label = codeLanguageLabel(language)
  const enabled = languagesQuery.data?.some((l) => l.code === language) ?? false
  const usesStdin = language !== 'SQL'
  const lineCount = source.split('\n').length
  const canRun = enabled && source.trim().length > 0 && !mutation.isPending
  // Monaco registers its Ctrl+Enter command once, when it mounts; the ref keeps that command current.
  const canRunRef = useRef(canRun)
  useEffect(() => {
    canRunRef.current = canRun
  }, [canRun])

  function handleChange(value: string) {
    setSource(value)
    saveDraft(draftKey, value)
  }

  function handleLanguage(next: CodeLanguageCode) {
    const nextKey = next === lessonLanguage ? `itilms.practice.${lessonId}` : `itilms.practice.${lessonId}.${next}`
    setLanguage(next)
    setSource(loadDraft(nextKey) ?? (next === lessonLanguage ? (starterCode ?? '') : ''))
    setResult(null)
    setRunError(null)
  }

  function handleReset() {
    clearDraft(draftKey)
    setSource(starter)
    setResult(null)
    setRunError(null)
  }

  return (
    <section className="flex flex-col gap-3 rounded-lg border bg-card p-4" aria-label={`${label} practice editor`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold">Try it yourself</h2>
          <p className="text-xs text-muted-foreground">
            {label}
            {language === 'SQL' && ' (runs on SQLite, starting from an empty database)'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {allowLanguageChoice && <LanguageSelect value={language} disabled={mutation.isPending} onChange={handleLanguage} />}
          <Button type="button" size="sm" variant="ghost" onClick={handleReset} disabled={mutation.isPending}>
            <RotateCcw />
            Reset
          </Button>
          <Button type="button" size="sm" onClick={() => mutation.mutate()} disabled={!canRun}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : <Play />}
            {mutation.isPending ? 'Running...' : 'Run'}
          </Button>
        </div>
      </div>

      {languagesQuery.isSuccess && !enabled && (
        <p className="rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">
          {languagesQuery.data.length === 0
            ? 'Running code is not set up on this server yet. You can still write your solution here.'
            : `${label} is not enabled on this server. You can still write your solution here.`}
        </p>
      )}

      <Suspense fallback={<div className="h-80 rounded-md border bg-muted/30" aria-busy />}>
        <CodeEditor
          language={language}
          value={source}
          onChange={handleChange}
          onRun={() => canRunRef.current && mutation.mutate()}
          height={editorHeight(lineCount)}
          ariaLabel={`${label} code`}
        />
      </Suspense>
      <p className="text-xs text-muted-foreground">
        Suggestions appear as you type (Ctrl+Space for more) &middot; Tab indents &middot; Ctrl+M lets Tab move to the
        next field &middot; Ctrl+Enter runs
      </p>

      {usesStdin && (
        <details className="text-sm">
          <summary className="cursor-pointer text-xs text-muted-foreground">Program input (optional)</summary>
          <Label htmlFor={`stdin-${lessonId}`} className="sr-only">
            Program input
          </Label>
          <textarea
            id={`stdin-${lessonId}`}
            value={stdin}
            onChange={(event) => setStdin(event.target.value)}
            rows={3}
            spellCheck={false}
            placeholder="Whatever your program reads, e.g. with input() or Scanner"
            className="mt-2 w-full rounded-md border bg-transparent px-3 py-2 font-mono text-sm"
          />
        </details>
      )}

      {runError && (
        <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {runError}
        </p>
      )}
      {result && <RunOutput result={result} />}
      {result && (
        <CodeResultPanel
          language={language}
          ranCode={ranCode}
          headlineLabel="Status"
          headline={OUTCOME_LABEL[result.outcome]}
          ok={result.outcome === 'SUCCESS'}
          timeSeconds={result.timeSeconds}
          memoryKb={result.memoryKb}
          analyse={result.outcome !== 'COMPILE_ERROR' && language !== 'SQL'}
        />
      )}
    </section>
  )
}

const OUTCOME_LABEL: Record<RunCodeResponse['outcome'], string> = {
  SUCCESS: 'Ran successfully',
  COMPILE_ERROR: 'Did not compile',
  RUNTIME_ERROR: 'Crashed while running',
  TIME_LIMIT_EXCEEDED: 'Took too long and was stopped',
}

function RunOutput({ result }: { result: RunCodeResponse }) {
  const ok = result.outcome === 'SUCCESS'
  const noOutput = !result.stdout && !result.stderr && !result.compileOutput

  return (
    <div className="flex flex-col gap-2" role="status">
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-xs">
        <span className={cn('font-medium', ok ? 'text-emerald-600' : 'text-destructive')}>
          {OUTCOME_LABEL[result.outcome]}
        </span>
        <span className="text-muted-foreground">
          {result.timeSeconds != null && `${result.timeSeconds}s`}
          {result.timeSeconds != null && result.memoryKb != null && ' · '}
          {result.memoryKb != null && `${Math.round(result.memoryKb / 1024)} MB`}
        </span>
      </div>
      {result.compileOutput && <OutputBlock title="Compiler messages" text={result.compileOutput} error />}
      {result.stdout && <OutputBlock title="Output" text={result.stdout} />}
      {result.stderr && <OutputBlock title="Error output" text={result.stderr} error />}
      {result.message && !result.stderr && !result.compileOutput && (
        <p className="text-xs text-muted-foreground">{result.message}</p>
      )}
      {ok && noOutput && <p className="text-xs text-muted-foreground">The program finished without printing anything.</p>}
      {result.outputTruncated && (
        <p className="text-xs text-muted-foreground">The output was very long, so only the start is shown.</p>
      )}
    </div>
  )
}

function OutputBlock({ title, text, error }: { title: string; text: string; error?: boolean }) {
  return (
    <div>
      <div className="mb-1 text-xs text-muted-foreground">{title}</div>
      <pre
        className={cn(
          'max-h-72 overflow-auto rounded-md border bg-muted/40 px-3 py-2 font-mono text-sm whitespace-pre-wrap',
          error && 'border-destructive/40 text-destructive',
        )}
      >
        {text}
      </pre>
    </div>
  )
}
