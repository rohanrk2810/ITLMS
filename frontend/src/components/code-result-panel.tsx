import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Info, Lightbulb, TriangleAlert, XCircle } from 'lucide-react'

import { type CodeAnalysis, type CodeLanguageCode, type ComplexityModel, analyzeCode } from '@/api/code'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface CaseMeasure {
  number: number
  hidden: boolean
  passed: boolean
  timeSeconds: number | null
  memoryKb: number | null
}

interface CodeResultPanelProps {
  language: CodeLanguageCode
  /** The exact code that was run. The analysis reads this, so editing afterwards does not change what is shown. */
  ranCode: string
  /** First row, e.g. "8/8 Passed" or "Ran successfully". */
  headline: string
  headlineLabel: string
  ok: boolean
  timeSeconds: number | null
  memoryKb: number | null
  /** Per-test measurements, when the run was a set of test cases. */
  cases?: CaseMeasure[]
  /** A program that did not compile has no complexity to estimate. */
  analyse?: boolean
}

type Open = 'explain' | 'optimize' | 'timings' | null

/**
 * The result of a run, laid out like a coding-practice site: what happened, how long it took, how much memory it used,
 * and an estimate of how it scales. The complexity is read from the source and always labelled an estimate.
 */
export function CodeResultPanel({
  language,
  ranCode,
  headline,
  headlineLabel,
  ok,
  timeSeconds,
  memoryKb,
  cases,
  analyse = true,
}: CodeResultPanelProps) {
  const [open, setOpen] = useState<Open>(null)
  const analysis = useQuery({
    queryKey: ['code-analysis', language, ranCode],
    queryFn: () => analyzeCode(language, ranCode),
    enabled: analyse && ranCode.trim().length > 0,
    staleTime: Infinity,
    retry: 0,
  })
  const data = analysis.data
  const measured = (cases ?? []).filter((c) => c.timeSeconds != null || c.memoryKb != null)
  const toggle = (which: Exclude<Open, null>) => setOpen((current) => (current === which ? null : which))

  return (
    <div className="flex flex-col gap-3 rounded-md border bg-card p-3 text-sm" role="region" aria-label="Run result and analysis">
      <dl className="grid grid-cols-[9rem_1fr] gap-y-1">
        <dt className="text-muted-foreground">{headlineLabel}</dt>
        <dd className={cn('flex items-center gap-1 font-medium', ok ? 'text-emerald-600' : 'text-destructive')}>
          {ok ? <CheckCircle2 className="size-4" /> : <XCircle className="size-4" />}
          {headline}
        </dd>
        <dt className="text-muted-foreground">Execution Time</dt>
        <dd>{timeSeconds != null ? `${Math.round(timeSeconds * 1000)} ms` : '-'}</dd>
        <dt className="text-muted-foreground">Memory Usage</dt>
        <dd>{memoryKb != null ? `${(memoryKb / 1024).toFixed(1)} MB` : '-'}</dd>
      </dl>

      {analyse && analysis.isLoading && <p className="text-xs text-muted-foreground">Estimating complexity...</p>}
      {analyse && analysis.isError && (
        <p className="text-xs text-muted-foreground">The complexity estimate is not available right now.</p>
      )}

      {data && data.supported && data.timeComplexity && (
        <>
          <div className="border-t pt-3">
            <dl className="grid grid-cols-[9rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">Time Complexity</dt>
              <dd className="font-mono font-medium">{data.timeComplexity}</dd>
              <dt className="text-muted-foreground">Space Complexity</dt>
              <dd className="font-mono font-medium">{data.spaceComplexity}</dd>
              <dt className="text-muted-foreground">Code analysis</dt>
              <dd className="flex flex-wrap items-center gap-2">
                <Verdict data={data} />
              </dd>
            </dl>
            <p className="mt-2 text-xs text-muted-foreground">
              <Badge variant="outline" className="mr-1">
                Estimated
              </Badge>
              Read from your code, not measured. Confidence: {data.confidence.toLowerCase()}.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" variant={open === 'explain' ? 'default' : 'outline'} onClick={() => toggle('explain')}>
              View Explanation
            </Button>
            <Button type="button" size="sm" variant={open === 'optimize' ? 'default' : 'outline'} onClick={() => toggle('optimize')}>
              <Lightbulb className="size-3.5" />
              Optimize Solution
            </Button>
            {measured.length > 0 && (
              <Button type="button" size="sm" variant={open === 'timings' ? 'default' : 'outline'} onClick={() => toggle('timings')}>
                Test case timings
              </Button>
            )}
          </div>

          {open === 'explain' && <Explanation data={data} />}
          {open === 'optimize' && <Optimization data={data} />}
          {open === 'timings' && <CaseBars cases={measured} />}
        </>
      )}

      {data && !data.supported && <p className="text-xs text-muted-foreground">{data.verdictMessage}</p>}
      {data && data.supported && !data.timeComplexity && <p className="text-xs text-muted-foreground">{data.verdictMessage}</p>}
    </div>
  )
}

function Verdict({ data }: { data: CodeAnalysis }) {
  if (data.verdict === 'EFFICIENT') {
    return (
      <span className="flex items-center gap-1 text-emerald-600">
        <CheckCircle2 className="size-4" />
        {data.verdictMessage}
      </span>
    )
  }
  if (data.verdict === 'COULD_BE_BETTER') {
    return (
      <span className="flex items-center gap-1 text-amber-600">
        <TriangleAlert className="size-4" />
        {data.verdictMessage}
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1 text-muted-foreground">
      <Info className="size-4" />
      {data.verdictMessage}
    </span>
  )
}

function Explanation({ data }: { data: CodeAnalysis }) {
  return (
    <div className="flex flex-col gap-3 rounded-md border bg-muted/30 p-3">
      <div>
        <p className="font-medium">
          Time Complexity: <span className="font-mono">{data.timeComplexity}</span>
        </p>
        <p className="text-muted-foreground">
          <span className="font-medium text-foreground">Reason: </span>
          {data.timeReason}
        </p>
      </div>
      <div>
        <p className="font-medium">
          Space Complexity: <span className="font-mono">{data.spaceComplexity}</span>
        </p>
        <p className="text-muted-foreground">
          <span className="font-medium text-foreground">Reason: </span>
          {data.spaceReason}
        </p>
      </div>
      {data.timeModel && <GrowthChart yours={data.timeModel} yoursLabel={data.timeComplexity ?? ''} />}
      {data.notes.length > 0 && (
        <ul className="list-disc pl-5 text-xs text-muted-foreground">
          {data.notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Optimization({ data }: { data: CodeAnalysis }) {
  if (data.suggestions.length === 0) {
    return (
      <p className="rounded-md border bg-muted/30 p-3 text-muted-foreground">
        No clear way to make this faster was found. Your code is not changed either way.
      </p>
    )
  }
  const first = data.suggestions[0]
  return (
    <div className="flex flex-col gap-3">
      {data.suggestions.map((s) => (
        <div key={s.title} className="flex flex-col gap-2 rounded-md border bg-muted/30 p-3">
          <p className="font-medium">{s.title}</p>
          <div className="grid gap-2 sm:grid-cols-2">
            <div className="rounded-md border bg-card p-2">
              <p className="text-xs text-muted-foreground">Your solution</p>
              <p className="font-mono">
                Time: {s.currentTime}
                <br />
                Space: {s.currentSpace}
              </p>
            </div>
            <div className="rounded-md border border-emerald-500/50 bg-card p-2">
              <p className="text-xs text-emerald-700 dark:text-emerald-400">Possible optimized solution</p>
              <p className="font-mono">
                Time: {s.betterTime}
                <br />
                Space: {s.betterSpace}
              </p>
            </div>
          </div>
          <p className="text-muted-foreground">
            <span className="font-medium text-foreground">Optimization: </span>
            {s.explanation}
          </p>
        </div>
      ))}
      {data.timeModel && (
        <GrowthChart
          yours={data.timeModel}
          yoursLabel={data.timeComplexity ?? ''}
          better={first.betterTimeModel}
          betterLabel={first.betterTime}
        />
      )}
      <p className="text-xs text-muted-foreground">These are suggestions only. Your code is never changed for you.</p>
    </div>
  )
}

// ------------------------------------------------------------------ charts

const CURVES: Array<{ label: string; model: ComplexityModel }> = [
  { label: 'O(1)', model: { p2: 0, log: 0, exp: false } },
  { label: 'O(log n)', model: { p2: 0, log: 1, exp: false } },
  { label: 'O(n)', model: { p2: 2, log: 0, exp: false } },
  { label: 'O(n log n)', model: { p2: 2, log: 1, exp: false } },
  { label: 'O(n²)', model: { p2: 4, log: 0, exp: false } },
  { label: 'O(2ⁿ)', model: { p2: 0, log: 0, exp: true } },
]

const W = 340
const H = 190
const PAD = { left: 34, right: 10, top: 10, bottom: 26 }
const N_MAX = 12
const Y_MAX = 150

/** Steps for an input of size n. A constant is drawn at 1 so it can be seen. */
function steps(model: ComplexityModel, n: number): number {
  if (model.exp) return 2 ** n
  const poly = n ** (model.p2 / 2)
  const log = Math.log2(n + 1) ** model.log
  return Math.max(1, poly * log)
}

const px = (n: number) => PAD.left + ((n - 1) / (N_MAX - 1)) * (W - PAD.left - PAD.right)
const py = (y: number) => PAD.top + (1 - Math.min(y, Y_MAX) / Y_MAX) * (H - PAD.top - PAD.bottom)

/** The curve as an SVG path, stopped where it leaves the top of the chart rather than flattened against it. */
function pathFor(model: ComplexityModel): string {
  const points: string[] = []
  let previous: { n: number; y: number } | null = null
  for (let n = 1; n <= N_MAX + 1e-9; n += 0.25) {
    const y = steps(model, n)
    if (y > Y_MAX) {
      if (previous) {
        const t = (Y_MAX - previous.y) / (y - previous.y)
        points.push(`L${px(previous.n + t * (n - previous.n)).toFixed(1)},${py(Y_MAX).toFixed(1)}`)
      }
      break
    }
    points.push(`${points.length === 0 ? 'M' : 'L'}${px(n).toFixed(1)},${py(y).toFixed(1)}`)
    previous = { n, y }
  }
  return points.join(' ')
}

function sameModel(a: ComplexityModel, b: ComplexityModel) {
  return a.p2 === b.p2 && a.log === b.log && a.exp === b.exp
}

/**
 * How the number of steps grows as the input grows, for the usual growth rates, with this code's own rate drawn
 * heavy and (when there is one) the suggested rate dashed. An illustration of shape, not a measurement.
 */
function GrowthChart({
  yours,
  yoursLabel,
  better,
  betterLabel,
}: {
  yours: ComplexityModel
  yoursLabel: string
  better?: ComplexityModel
  betterLabel?: string
}) {
  return (
    <figure className="flex flex-col gap-1">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`How the number of steps grows with the input. Your code: ${yoursLabel}${better ? `. Suggested: ${betterLabel}` : ''}.`}
        className="w-full max-w-md rounded-md border bg-card"
      >
        <line x1={PAD.left} y1={H - PAD.bottom} x2={W - PAD.right} y2={H - PAD.bottom} stroke="currentColor" opacity={0.4} />
        <line x1={PAD.left} y1={PAD.top} x2={PAD.left} y2={H - PAD.bottom} stroke="currentColor" opacity={0.4} />
        <text x={W / 2} y={H - 6} textAnchor="middle" fontSize="9" fill="currentColor" opacity={0.7}>
          input size (n)
        </text>
        <text x={10} y={H / 2} textAnchor="middle" fontSize="9" fill="currentColor" opacity={0.7} transform={`rotate(-90 10 ${H / 2})`}>
          steps
        </text>
        {CURVES.map((curve) => (
          <path
            key={curve.label}
            d={pathFor(curve.model)}
            fill="none"
            stroke="currentColor"
            strokeWidth={1}
            opacity={0.25}
          />
        ))}
        {better && !sameModel(better, yours) && (
          <path d={pathFor(better)} fill="none" stroke="#10b981" strokeWidth={2.5} strokeDasharray="6 4" />
        )}
        <path d={pathFor(yours)} fill="none" stroke="var(--primary)" strokeWidth={3} />
      </svg>
      <figcaption className="flex flex-wrap gap-x-3 text-xs text-muted-foreground">
        <span className="flex items-center gap-1">
          <span className="inline-block h-0.5 w-4" style={{ background: 'var(--primary)' }} />
          Your code {yoursLabel}
        </span>
        {better && !sameModel(better, yours) && (
          <span className="flex items-center gap-1">
            <span className="inline-block h-0.5 w-4 border-t-2 border-dashed border-emerald-500" />
            Suggested {betterLabel}
          </span>
        )}
        <span>Faint lines: {CURVES.map((c) => c.label).join(', ')}</span>
      </figcaption>
    </figure>
  )
}

/** One bar per test case for time and for memory, coloured by whether the test passed. */
function CaseBars({ cases }: { cases: CaseMeasure[] }) {
  const maxTime = Math.max(...cases.map((c) => c.timeSeconds ?? 0), 0.001)
  const maxMem = Math.max(...cases.map((c) => c.memoryKb ?? 0), 1)
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <BarGroup
        title="Time per test (ms)"
        rows={cases.map((c) => ({
          key: c.number,
          label: c.hidden ? `Hidden ${c.number}` : `Test ${c.number}`,
          passed: c.passed,
          fraction: (c.timeSeconds ?? 0) / maxTime,
          value: c.timeSeconds != null ? `${Math.round(c.timeSeconds * 1000)} ms` : '-',
        }))}
      />
      <BarGroup
        title="Memory per test (MB)"
        rows={cases.map((c) => ({
          key: c.number,
          label: c.hidden ? `Hidden ${c.number}` : `Test ${c.number}`,
          passed: c.passed,
          fraction: (c.memoryKb ?? 0) / maxMem,
          value: c.memoryKb != null ? `${(c.memoryKb / 1024).toFixed(1)} MB` : '-',
        }))}
      />
    </div>
  )
}

function BarGroup({
  title,
  rows,
}: {
  title: string
  rows: Array<{ key: number; label: string; passed: boolean; fraction: number; value: string }>
}) {
  return (
    <figure className="flex flex-col gap-1" aria-label={title}>
      <figcaption className="text-xs font-medium">{title}</figcaption>
      {rows.map((row) => (
        <div key={row.key} className="grid grid-cols-[5rem_1fr_4rem] items-center gap-2 text-xs">
          <span className="truncate text-muted-foreground">{row.label}</span>
          <span className="h-3 overflow-hidden rounded bg-muted">
            <span
              className={cn('block h-full rounded', row.passed ? 'bg-emerald-500' : 'bg-destructive')}
              style={{ width: `${Math.max(2, Math.round(row.fraction * 100))}%` }}
            />
          </span>
          <span className="text-right tabular-nums">{row.value}</span>
        </div>
      ))}
    </figure>
  )
}
