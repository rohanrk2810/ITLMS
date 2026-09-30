import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { toast } from 'sonner'

import { type LiveAnswer, type LiveQuestion, LIVE_QUESTION_TYPE_LABEL, classQuestions, questionAnswers } from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'

/**
 * Every question asked in a class, in order, for a host to review. Reused live (alongside the ask form) and on the
 * past-class review page once the class has ended - the answers a student gave are the same either way.
 */
export function QuestionHistoryList({ classSessionId, questions, onRefresh }: {
  classSessionId: number
  /** Pass a list already loaded (the review page has one); omit to have this component load and poll it itself. */
  questions?: LiveQuestion[]
  onRefresh?: () => void
}) {
  const [own, setOwn] = useState<LiveQuestion[]>([])
  const [expanded, setExpanded] = useState<LiveQuestion | null>(null)
  const [answers, setAnswers] = useState<LiveAnswer[]>([])

  useEffect(() => {
    if (questions) return
    classQuestions(classSessionId).then(setOwn).catch(() => undefined)
  }, [classSessionId, questions])

  const list = questions ?? own

  async function open(q: LiveQuestion) {
    setExpanded(q)
    try {
      setAnswers(await questionAnswers(q.id))
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not load answers.'))
    }
  }

  return (
    <div>
      <h3 className="text-sm font-medium">Questions asked ({list.length})</h3>
      <ul className="mt-1 flex flex-col gap-1">
        {list.map((q) => (
          <li key={q.id} className="flex items-center justify-between gap-2 rounded border p-2 text-xs">
            <button type="button" className="min-w-0 flex-1 truncate text-left hover:underline" onClick={() => void open(q)}>
              <span className="font-mono text-muted-foreground">{q.offsetLabel}</span> {q.prompt}
            </button>
            <span className="shrink-0 text-muted-foreground">
              {LIVE_QUESTION_TYPE_LABEL[q.type]} &middot; {q.status === 'OPEN' ? 'open' : `${q.answered ?? 0} answered`}
            </span>
          </li>
        ))}
        {list.length === 0 && <p className="text-xs text-muted-foreground">Nothing was asked in this class.</p>}
      </ul>

      {expanded && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => { setExpanded(null); onRefresh?.() }}>
          <div className="max-h-[80vh] w-full max-w-lg overflow-y-auto rounded-lg bg-background p-4 shadow-lg" onClick={(e) => e.stopPropagation()}>
            <div className="mb-2 flex items-center justify-between">
              <h4 className="font-medium">{expanded.prompt}</h4>
              <Button size="icon" variant="ghost" onClick={() => { setExpanded(null); onRefresh?.() }}><X className="size-4" /></Button>
            </div>
            <ul className="flex flex-col gap-2 text-sm">
              {answers.map((a) => (
                <li key={a.userId} className="rounded border p-2">
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{a.displayName ?? `User ${a.userId}`}</span>
                    <span className="flex items-center gap-2">
                      {a.correct != null && (
                        <span className={a.correct ? 'text-green-600' : 'text-destructive'}>
                          {a.correct ? `Correct (${a.awardedMarks ?? 0})` : 'Incorrect'}
                        </span>
                      )}
                      {a.viaRecording && <span className="text-xs text-muted-foreground">from recording</span>}
                    </span>
                  </div>
                  {a.code ? (
                    <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 text-xs">{a.code}</pre>
                  ) : a.text ? (
                    <p className="mt-1 text-muted-foreground">{a.text}</p>
                  ) : a.selected ? (
                    <p className="mt-1 text-muted-foreground">Picked: {a.selected.join(', ')}</p>
                  ) : null}
                </li>
              ))}
              {answers.length === 0 && <p className="text-xs text-muted-foreground">Nobody has answered yet.</p>}
            </ul>
          </div>
        </div>
      )}
    </div>
  )
}
