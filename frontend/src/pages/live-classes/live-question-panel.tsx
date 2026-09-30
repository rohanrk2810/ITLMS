import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { useDataChannel } from '@livekit/components-react'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { toast } from 'sonner'

import { type AnswerQuestionInput, type LiveQuestion, answerQuestion, openQuestion } from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'

const CodeEditor = lazy(() => import('@/components/code-editor'))

/**
 * A student's view of the question the trainer is asking right now. Polls as a fallback and reacts at once to the
 * data-channel messages the server pushes ('live-question' when one opens, 'live-question-closed' when it shuts).
 */
export function LiveQuestionPanel({ classSessionId }: { classSessionId: number }) {
  const [question, setQuestion] = useState<LiveQuestion | null>(null)
  const [dismissed, setDismissed] = useState<number | null>(null)

  const refresh = useCallback(() => {
    openQuestion(classSessionId).then(setQuestion).catch(() => undefined)
  }, [classSessionId])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 5000)
    return () => window.clearInterval(timer)
  }, [refresh])

  useDataChannel('live-question', () => refresh())
  useDataChannel('live-question-closed', () => refresh())

  if (!question || question.status !== 'OPEN' || question.myAnswer || dismissed === question.id) {
    return null
  }

  return (
    <div className="absolute inset-x-0 bottom-0 z-40 flex justify-center p-3">
      <div className="w-full max-w-xl rounded-lg border bg-background p-4 shadow-lg">
        <AnswerForm
          question={question}
          onAnswered={(q) => setQuestion(q)}
          onSkip={() => setDismissed(question.id)}
        />
      </div>
    </div>
  )
}

function AnswerForm({ question, onAnswered, onSkip }: {
  question: LiveQuestion
  onAnswered: (q: LiveQuestion) => void
  onSkip: () => void
}) {
  const [selected, setSelected] = useState<number[]>([])
  const [text, setText] = useState('')
  const [code, setCode] = useState(question.starterCode ?? '')
  const [busy, setBusy] = useState(false)

  function toggle(i: number) {
    if (question.type === 'MULTIPLE_SELECT') {
      setSelected((s) => (s.includes(i) ? s.filter((x) => x !== i) : [...s, i]))
    } else {
      setSelected([i])
    }
  }

  async function submit() {
    const input: AnswerQuestionInput = {}
    if (question.type === 'MCQ' || question.type === 'MULTIPLE_SELECT' || question.type === 'TRUE_FALSE') {
      if (selected.length === 0) {
        toast.error('Pick an answer.')
        return
      }
      input.selected = selected
    } else if (question.type === 'CODING') {
      if (!code.trim()) {
        toast.error('Write your code first.')
        return
      }
      input.code = code
      input.language = question.language ?? undefined
    } else {
      if (!text.trim()) {
        toast.error('Write your answer.')
        return
      }
      input.text = text
    }

    setBusy(true)
    try {
      const result = await answerQuestion(question.id, input)
      onAnswered(result)
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not submit your answer.'))
    } finally {
      setBusy(false)
    }
  }

  if (question.myAnswer) {
    const a = question.myAnswer
    return (
      <div className="flex items-center gap-2 text-sm">
        {a.correct === true && <CheckCircle2 className="size-5 text-green-600" />}
        {a.correct === false && <XCircle className="size-5 text-destructive" />}
        <span>
          {a.correct == null ? 'Answer submitted. The trainer will review it.'
            : a.correct ? `Correct! +${a.awardedMarks ?? 0} marks` : 'Not quite right.'}
        </span>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium">{question.prompt}</p>
        <button type="button" className="text-xs text-muted-foreground hover:underline" onClick={onSkip}>Hide</button>
      </div>

      {(question.type === 'MCQ' || question.type === 'TRUE_FALSE') && (
        <RadioGroup value={String(selected[0] ?? '')} onValueChange={(v) => setSelected([Number(v)])}>
          {(question.options ?? []).map((opt, i) => (
            <label key={i} className="flex items-center gap-2 text-sm">
              <RadioGroupItem value={String(i)} /> {opt}
            </label>
          ))}
        </RadioGroup>
      )}

      {question.type === 'MULTIPLE_SELECT' && (
        <div className="flex flex-col gap-1">
          {(question.options ?? []).map((opt, i) => (
            <label key={i} className="flex items-center gap-2 text-sm">
              <Checkbox checked={selected.includes(i)} onCheckedChange={() => toggle(i)} /> {opt}
            </label>
          ))}
        </div>
      )}

      {question.type === 'SHORT_ANSWER' && (
        <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="Your answer" maxLength={4000} />
      )}

      {question.type === 'OTHER' && (
        <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="Your answer" maxLength={4000} />
      )}

      {question.type === 'CODING' && (
        <Suspense fallback={<div className="flex h-24 items-center justify-center"><Loader2 className="size-4 animate-spin" /></div>}>
          <CodeEditor language="PYTHON" value={code} onChange={setCode} height={200} ariaLabel="Your code" />
        </Suspense>
      )}

      <Button size="sm" disabled={busy} onClick={() => void submit()} className="self-end">
        {busy && <Loader2 className="size-4 animate-spin" />} Submit
      </Button>
    </div>
  )
}
