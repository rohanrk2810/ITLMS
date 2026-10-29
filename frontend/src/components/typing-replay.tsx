import { lazy, Suspense, useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ClipboardPaste, Pause, Play, RotateCcw } from 'lucide-react'

import { getTypingReplay } from '@/api/assessments'
import type { CodeLanguageCode } from '@/api/code'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { clock, NOTABLE_PASTE, parseEdits, Replay, sameCode, statsOf } from '@/lib/typing-replay'
import { cn } from '@/lib/utils'

// Monaco is several MB, so it loads only when a replay is opened.
const CodeEditor = lazy(() => import('@/components/code-editor'))

const SPEEDS = [1, 2, 4, 8, 16]

export interface ReplayQuestion {
  id: number
  text: string
  language: CodeLanguageCode | null
}

/** A trainer's way to watch how a student typed a coding answer: the edits played back with their pauses. */
export function TypingReplayButton({
  attemptId,
  studentName,
  questions,
}: {
  attemptId: number
  studentName: string
  questions: ReplayQuestion[]
}) {
  const [open, setOpen] = useState(false)
  const [questionId, setQuestionId] = useState(questions[0]?.id)
  const question = questions.find((q) => q.id === questionId) ?? questions[0]
  if (!question) return null

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <Play />
          Replay
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>How {studentName} typed the answer</DialogTitle>
          <DialogDescription>
            Every edit is played back as it happened. Long pauses are shortened. A block that appears all at once was pasted.
          </DialogDescription>
        </DialogHeader>
        {questions.length > 1 && (
          <select
            value={question.id}
            onChange={(event) => setQuestionId(Number(event.target.value))}
            className="h-9 rounded-md border bg-transparent px-2 text-sm"
            aria-label="Coding question"
          >
            {questions.map((q, i) => (
              <option key={q.id} value={q.id}>
                {i + 1}. {q.text.slice(0, 80)}
              </option>
            ))}
          </select>
        )}
        {questions.length === 1 && <p className="text-sm text-muted-foreground">{question.text}</p>}
        {open && <ReplayPlayer key={`${attemptId}-${question.id}`} attemptId={attemptId} question={question} />}
      </DialogContent>
    </Dialog>
  )
}

function ReplayPlayer({ attemptId, question }: { attemptId: number; question: ReplayQuestion }) {
  const query = useQuery({
    queryKey: ['quiz-attempts', attemptId, 'typing', question.id],
    queryFn: () => getTypingReplay(attemptId, question.id),
  })
  const data = query.data
  const replay = useMemo(() => (data ? new Replay(parseEdits(data.chunks), data.starterCode) : null), [data])
  const stats = useMemo(() => (replay ? statsOf(replay.edits) : null), [replay])
  const [index, setIndex] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState(4)

  useEffect(() => {
    if (!playing || !replay) return
    if (index >= replay.length) {
      setPlaying(false)
      return
    }
    const timer = window.setTimeout(() => setIndex((i) => i + 1), Math.max(8, replay.gapBefore(index) / speed))
    return () => window.clearTimeout(timer)
  }, [playing, index, replay, speed])

  if (query.isLoading) return <Skeleton className="h-80 w-full" />
  if (query.isError || !data || !replay || !stats) {
    return <p className="text-sm text-destructive">Could not load the replay.</p>
  }
  if (replay.length === 0) {
    return (
      <p className="rounded-md border px-3 py-2 text-sm">
        No typing was recorded for this answer. The student may not have typed in the editor, or their browser did not send
        it (it was offline, or the test was not set to record typing). The submitted answer itself is unaffected.
      </p>
    )
  }

  const text = replay.textAt(index)
  const total = stats.typed + stats.pasted
  const mostlyPasted = total > 0 && stats.pasted / total > 0.5 && stats.largestPaste >= NOTABLE_PASTE
  const notable = stats.pastes.filter((p) => p.characters >= NOTABLE_PASTE)
  const endsAsSubmitted = sameCode(replay.textAt(replay.length), data.finalCode)
  const first = replay.edits[0].t
  const nowAt = index === 0 ? 0 : replay.edits[Math.min(index, replay.length) - 1].t - first

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Badge variant="secondary">{stats.edits.toLocaleString()} edits</Badge>
        <Badge variant="secondary">{clock(stats.durationMs)} from first edit to last</Badge>
        <Badge variant="secondary">typed {stats.typed.toLocaleString()} characters</Badge>
        <Badge variant={stats.pasted > 0 ? 'outline' : 'secondary'}>
          pasted {stats.pasted.toLocaleString()} characters in {stats.pastes.length} paste{stats.pastes.length === 1 ? '' : 's'}
        </Badge>
        {mostlyPasted && <Badge variant="destructive">Most of this answer was pasted</Badge>}
        <Badge variant={endsAsSubmitted ? 'secondary' : 'outline'}>
          {endsAsSubmitted ? 'ends as submitted' : 'differs from the submitted answer'}
        </Badge>
      </div>

      <Suspense fallback={<Skeleton className="h-80 w-full" />}>
        <CodeEditor language={question.language ?? 'JAVA'} value={text} onChange={() => undefined} readOnly height={340} suggestions={false} />
      </Suspense>

      <div className="flex flex-col gap-2">
        <div className="relative">
          <input
            type="range"
            min={0}
            max={replay.length}
            value={index}
            onChange={(event) => {
              setPlaying(false)
              setIndex(Number(event.target.value))
            }}
            className="w-full"
            aria-label="Position in the replay"
          />
          {/* Where the pastes are, on the same line as the slider. */}
          {notable.map((p) => (
            <span
              key={p.index}
              className="pointer-events-none absolute top-0 h-2 w-1 rounded bg-destructive"
              style={{ left: `${(p.index / replay.length) * 100}%` }}
            />
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            size="sm"
            onClick={() => {
              if (index >= replay.length) setIndex(0)
              setPlaying((value) => !value)
            }}
          >
            {playing ? <Pause /> : <Play />}
            {playing ? 'Pause' : index >= replay.length ? 'Play again' : 'Play'}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setPlaying(false)
              setIndex(0)
            }}
          >
            <RotateCcw />
            Start
          </Button>
          <div className="flex items-center gap-1" role="group" aria-label="Speed">
            {SPEEDS.map((s) => (
              <Button key={s} size="sm" variant={s === speed ? 'secondary' : 'ghost'} onClick={() => setSpeed(s)}>
                {s}×
              </Button>
            ))}
          </div>
          <span className="ml-auto font-mono text-xs text-muted-foreground">
            {clock(nowAt)} / {clock(stats.durationMs)} &middot; edit {index} of {replay.length}
          </span>
        </div>
      </div>

      {notable.length > 0 && (
        <div className="flex flex-col gap-1">
          <span className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
            <ClipboardPaste className="size-3.5" />
            Pasted blocks (click to jump to just after one)
          </span>
          <div className="flex flex-wrap gap-2">
            {notable.map((p, i) => (
              <Button
                key={p.index}
                size="sm"
                variant="outline"
                className={cn('font-mono text-xs')}
                onClick={() => {
                  setPlaying(false)
                  setIndex(p.index + 1)
                }}
              >
                #{i + 1} &middot; {p.characters.toLocaleString()} characters at {clock(p.at)}
              </Button>
            ))}
          </div>
        </div>
      )}
      <p className="text-xs text-muted-foreground">
        This is what the student&apos;s browser reported, so it can be switched off or faked: treat it as a hint to look
        closer, not as proof. An answer with no typing at all is worth a look too.
      </p>
    </div>
  )
}
