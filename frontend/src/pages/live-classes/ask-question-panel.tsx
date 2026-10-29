import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import {
  type AskQuestionInput,
  type LiveQuestion,
  LIVE_QUESTION_TYPE_LABEL,
  saveMaterial,
  askQuestion,
  classQuestions,
  closeQuestion,
} from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { useAsk } from '@/components/ask-dialog'
import { Button } from '@/components/ui/button'
import { MaterialList } from './material-list'
import { QuestionForm } from './question-form'
import { QuestionHistoryList } from './question-history-list'

/** The trainer's side of asking the class a question, and the history of what has been asked. */
export function AskQuestionPanel({ liveSessionId, classSessionId }: { liveSessionId: number; classSessionId: number }) {
  const [history, setHistory] = useState<LiveQuestion[]>([])

  const refresh = () => {
    classQuestions(classSessionId).then(setHistory).catch(() => undefined)
  }
  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 8000)
    return () => window.clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [classSessionId])

  async function close(q: LiveQuestion) {
    try {
      await closeQuestion(q.id)
      refresh()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not close the question.'))
    }
  }

  const open = history.find((q) => q.status === 'OPEN')

  return (
    <div className="flex flex-col gap-3">
      {open ? (
        <div className="rounded-md border bg-muted/40 p-3 text-sm">
          <div className="flex items-center justify-between gap-2">
            <span className="font-medium">Open: {open.prompt}</span>
            <Button size="sm" variant="outline" onClick={() => void close(open)}>Close</Button>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {LIVE_QUESTION_TYPE_LABEL[open.type]} &middot; {open.answered ?? 0} answered
            {open.correctCount != null && ` · ${open.correctCount} correct`}
          </p>
        </div>
      ) : (
        <AskForm liveSessionId={liveSessionId} onAsked={refresh} />
      )}

      <QuestionHistoryList classSessionId={classSessionId} questions={history} onRefresh={refresh} />
    </div>
  )
}

function AskForm({ liveSessionId, onAsked }: { liveSessionId: number; onAsked: () => void }) {
  const dialogs = useAsk()
  const [tab, setTab] = useState<'new' | 'saved'>('new')
  const [savedKey, setSavedKey] = useState(0)

  async function ask(input: AskQuestionInput) {
    try {
      await askQuestion(liveSessionId, input)
      toast.success('Question sent to the class')
      onAsked()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not ask the question.'))
      throw err
    }
  }

  async function keep(build: () => AskQuestionInput | null) {
    const input = build()
    if (!input) return
    const title = await dialogs.prompt({
      title: 'Save to your question bank',
      label: 'Name',
      initial: input.prompt.slice(0, 60),
      confirmLabel: 'Save',
    })
    if (!title?.trim()) return
    try {
      await saveMaterial(title.trim(), input)
      toast.success('Saved to your question bank')
      setSavedKey((k) => k + 1)
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not save it.'))
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border p-3">
      {dialogs.dialog}
      <div className="flex gap-1">
        <Button size="sm" variant={tab === 'new' ? 'default' : 'outline'} onClick={() => setTab('new')}>Ask a question</Button>
        <Button size="sm" variant={tab === 'saved' ? 'default' : 'outline'} onClick={() => setTab('saved')}>Question bank</Button>
      </div>
      {tab === 'new' ? (
        <QuestionForm
          submitLabel="Ask the class"
          onSubmit={ask}
          extraActions={(build) => (
            <Button size="sm" variant="outline" onClick={() => void keep(build)}>Save for later</Button>
          )}
        />
      ) : (
        <MaterialList liveSessionId={liveSessionId} refreshKey={savedKey} onSent={onAsked} />
      )}
    </div>
  )
}
