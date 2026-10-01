import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Info, Lightbulb, TriangleAlert, XCircle } from 'lucide-react'

import type { ComparisonBucket, RunComparison } from '@/api/assessments'
import { type CodeAnalysis, type CodeLanguageCode, type ComplexityModel, analyzeCode } from '@/api/code'
import { BarChart, LineChart, type LineSeries, SEMANTIC } from '@/components/charts'
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
  /** Against other students, for a run that passed every test of a question many have solved. */
  comparison?: RunComparison | null
  /** A program that did not compile has no complexity to estimate. */
  analyse?: boolean
}

type Open = 'explain' | 'optimize' | 'timings' | 'compare' | null

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
  comparison,
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
        {comparison?.available && comparison.runtimeBeatsPercent != null && (
          <>
            <dt className="text-muted-foreground">Compared with others</dt>
            <dd>
              Faster than {comparison.runtimeBeatsPercent}%
              {comparison.memoryBeatsPercent != null && `, lighter than ${comparison.memoryBeatsPercent}%`} of{' '}
              {comparison.sampleSize - 1} other student{comparison.sampleSize - 1 === 1 ? '' : 's'}
            </dd>
          </>
        )}
      </dl>
      {comparison && !comparison.available && (
        <p className="text-xs text-muted-foreground">
          A comparison with other students appears once {comparison.minimumSample} have solved this question
          ({comparison.sampleSize} so far).
        </p>
      )}

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
            {comparison?.available && (
              <Button type="button" size="sm" variant={open === 'compare' ? 'default' : 'outline'} onClick={() => toggle('compare')}>
                Compare with others
              </Button>
            )}
          </div>

          {open === 'explain' && <Explanation data={data} />}
          {open === 'optimize' && <Optimization data={data} />}
          {open === 'timings' && <CaseBars cases={measured} />}
          {open === 'compare' && comparison && <Distribution comparison={comparison} />}
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

const N_MAX = 12
const Y_MAX = 150

/** Steps for an input of size n. A constant is drawn at 1 so it can be seen. */
function steps(model: ComplexityModel, n: number): number {
  if (model.exp) return 2 ** n
  const poly = n ** (model.p2 / 2)
  const log = Math.log2(n + 1) ** model.log
  return Math.max(1, poly * log)
}

function pointsFor(model: ComplexityModel): Array<{ x: number; y: number }> {
  const points: Array<{ x: number; y: number }> = []
  for (let n = 1; n <= N_MAX; n += 0.5) {
    points.push({ x: n, y: steps(model, n) })
  }
  return points
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
  const suggested = better && !sameModel(better, yours) ? better : undefined
  const series: LineSeries[] = [
    ...CURVES.map((c) => ({ label: c.label, points: pointsFor(c.model), color: '#94a3b8', faint: true })),
    ...(suggested
      ? [{ label: `Suggested ${betterLabel}`, points: pointsFor(suggested), color: SEMANTIC.good, dashed: true, width: 3 }]
      : []),
    { label: `Your code ${yoursLabel}`, points: pointsFor(yours), color: 'primary', width: 4 },
  ]
  return (
    <figure className="flex flex-col gap-1">
      <LineChart
        series={series}
        xTitle="input size (n)"
        yTitle="steps"
        yMax={Y_MAX}
        height={260}
        ariaLabel={`How the number of steps grows with the input. Your code: ${yoursLabel}${
          suggested ? `. Suggested: ${betterLabel}` : ''
        }.`}
      />
      <figcaption className="text-xs text-muted-foreground">
        Faint lines are the usual growth rates ({CURVES.map((c) => c.label).join(', ')}). The picture shows the shape of
        the growth, not measured time.
      </figcaption>
    </figure>
  )
}

/** Where this run sits among everyone who solved the question: ten columns, this student's column highlighted. */
function Distribution({ comparison }: { comparison: RunComparison }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Histogram title="Runtime of all submissions (ms)" buckets={comparison.runtimeBuckets} unit="ms" />
      {comparison.memoryBuckets.length > 0 && (
        <Histogram title="Memory of all submissions (MB)" buckets={comparison.memoryBuckets} unit="MB" scale={1024} />
      )}
      <p className="text-xs text-muted-foreground sm:col-span-2">
        Each column counts the students whose best passing solution fell in that range; the green column is yours. Only
        numbers are compared: no one&apos;s code or name is shown.
      </p>
    </div>
  )
}

function Histogram({ title, buckets, unit, scale = 1 }: { title: string; buckets: ComparisonBucket[]; unit: string; scale?: number }) {
  const fmt = (v: number) => (scale === 1 ? String(v) : (v / scale).toFixed(1))
  return (
    <figure className="flex flex-col gap-1">
      <figcaption className="text-xs font-medium">{title}</figcaption>
      <BarChart
        labels={buckets.map((b) => `${fmt(b.from)}-${fmt(b.to)}`)}
        series={[
          {
            label: 'Students',
            values: buckets.map((b) => b.count),
            color: buckets.map((b) => (b.yours ? SEMANTIC.good : SEMANTIC.neutral)),
          },
        ]}
        height={180}
        ariaLabel={`${title}. Your solution is in the range ${fmt(buckets.find((b) => b.yours)?.from ?? 0)} to ${fmt(
          buckets.find((b) => b.yours)?.to ?? 0,
        )} ${unit}.`}
      />
    </figure>
  )
}

/** One bar per test case for time and for memory, coloured by whether the test passed. */
function CaseBars({ cases }: { cases: CaseMeasure[] }) {
  const labels = cases.map((c) => (c.hidden ? `Hidden ${c.number}` : `Test ${c.number}`))
  const colors = cases.map((c) => (c.passed ? SEMANTIC.good : SEMANTIC.bad))
  const height = Math.max(120, 36 + cases.length * 26)
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <figure className="flex flex-col gap-1">
        <figcaption className="text-xs font-medium">Time per test (ms)</figcaption>
        <BarChart
          horizontal
          labels={labels}
          series={[{ label: 'Time', values: cases.map((c) => Math.round((c.timeSeconds ?? 0) * 1000)), color: colors }]}
          unit=" ms"
          height={height}
          ariaLabel="Time taken by each test case, in milliseconds. Green bars passed, red bars failed."
        />
      </figure>
      <figure className="flex flex-col gap-1">
        <figcaption className="text-xs font-medium">Memory per test (MB)</figcaption>
        <BarChart
          horizontal
          labels={labels}
          series={[{ label: 'Memory', values: cases.map((c) => Number(((c.memoryKb ?? 0) / 1024).toFixed(1))), color: colors }]}
          unit=" MB"
          height={height}
          ariaLabel="Memory used by each test case, in megabytes. Green bars passed, red bars failed."
        />
      </figure>
    </div>
  )
}
