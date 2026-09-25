import { type KeyboardEvent, type UIEvent, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Loader2, Play, RotateCcw } from 'lucide-react'

import { apiErrorMessage } from '@/api/client'
import { codeLanguageLabel, type CodeLanguageCode, getCodeLanguages, runCode, type RunCodeResponse } from '@/api/code'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

const INDENT = '    '

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
  /** Keeps each lesson's draft separate, so a half-written exercise survives leaving the page. Render with key={lessonId} so moving between lessons starts fresh. */
  lessonId: number
}

/**
 * A code editor and Run button for one lesson. It runs nothing itself: the code goes to
 * codeexec-service, which decides whether it may run and hands it to the sandbox.
 *
 * A plain textarea on purpose - no syntax highlighting, but no editor library to load either.
 * Tab indents, Esc then Tab moves focus on (so keyboard users are not trapped), Ctrl/Cmd+Enter runs.
 */
export function PracticeEditor({ language, starterCode, lessonId }: PracticeEditorProps) {
  const draftKey = `itilms.practice.${lessonId}`
  const starter = starterCode ?? ''

  const [source, setSource] = useState(() => loadDraft(draftKey) ?? starter)
  const [stdin, setStdin] = useState('')
  const [result, setResult] = useState<RunCodeResponse | null>(null)
  const [runError, setRunError] = useState<string | null>(null)
  const tabMovesFocus = useRef(false)
  const gutterRef = useRef<HTMLDivElement>(null)

  const languagesQuery = useQuery({
    queryKey: ['code', 'languages'],
    queryFn: getCodeLanguages,
    staleTime: 5 * 60_000,
  })

  const mutation = useMutation({
    mutationFn: () => runCode({ language, sourceCode: source, stdin: stdin || undefined }),
    onMutate: () => {
      setResult(null)
      setRunError(null)
    },
    onSuccess: setResult,
    onError: (error) => setRunError(apiErrorMessage(error, 'Could not run the code. Try again in a moment.')),
  })

  const label = codeLanguageLabel(language)
  const enabled = languagesQuery.data?.some((l) => l.code === language) ?? false
  const usesStdin = language !== 'SQL'
  const lineCount = source.split('\n').length
  const canRun = enabled && source.trim().length > 0 && !mutation.isPending

  function handleChange(value: string) {
    setSource(value)
    saveDraft(draftKey, value)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault()
      if (canRun) mutation.mutate()
      return
    }
    if (event.key === 'Escape') {
      tabMovesFocus.current = true
      return
    }
    if (event.key === 'Tab' && !event.shiftKey && !tabMovesFocus.current) {
      event.preventDefault()
      const field = event.currentTarget
      field.setRangeText(INDENT, field.selectionStart, field.selectionEnd, 'end')
      handleChange(field.value)
      return
    }
    tabMovesFocus.current = false
  }

  function handleScroll(event: UIEvent<HTMLTextAreaElement>) {
    if (gutterRef.current) gutterRef.current.scrollTop = event.currentTarget.scrollTop
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

      <div className="flex overflow-hidden rounded-md border bg-background font-mono text-sm focus-within:ring-[3px] focus-within:ring-ring/50">
        <div
          ref={gutterRef}
          aria-hidden
          className="max-h-96 min-h-40 select-none overflow-hidden border-r bg-muted/50 px-2 py-2 text-right leading-6 text-muted-foreground"
        >
          {Array.from({ length: lineCount }, (_, i) => (
            <div key={i}>{i + 1}</div>
          ))}
        </div>
        <textarea
          aria-label={`${label} code`}
          value={source}
          onChange={(event) => handleChange(event.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={() => {
            tabMovesFocus.current = false
          }}
          onScroll={handleScroll}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
          wrap="off"
          className="max-h-96 min-h-40 w-full resize-y overflow-auto bg-transparent px-3 py-2 leading-6 whitespace-pre outline-none"
          style={{ height: `${Math.min(Math.max(lineCount, 8), 16) * 1.5 + 1}rem` }}
        />
      </div>
      <p className="text-xs text-muted-foreground">
        Tab indents &middot; Esc then Tab moves to the next field &middot; Ctrl+Enter runs
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
