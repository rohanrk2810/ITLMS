import { lazy, Suspense, useEffect, useState } from 'react'
import { Loader2, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import {
  type AskQuestionInput,
  type LiveQuestion,
  type LiveQuestionType,
  LIVE_QUESTION_TYPE_LABEL,
  askQuestion,
  classQuestions,
  closeQuestion,
} from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { QuestionHistoryList } from './question-history-list'

const CodeEditor = lazy(() => import('@/components/code-editor'))

const TYPES: LiveQuestionType[] = ['MCQ', 'MULTIPLE_SELECT', 'TRUE_FALSE', 'SHORT_ANSWER', 'CODING', 'OTHER']

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
  const [type, setType] = useState<LiveQuestionType>('MCQ')
  const [prompt, setPrompt] = useState('')
  const [options, setOptions] = useState(['', ''])
  const [correct, setCorrect] = useState<number[]>([])
  const [accepted, setAccepted] = useState('')
  const [language, setLanguage] = useState('python')
  const [starterCode, setStarterCode] = useState('')
  const [explanation, setExplanation] = useState('')
  const [marks, setMarks] = useState(1)
  const [busy, setBusy] = useState(false)

  function toggleCorrect(i: number) {
    if (type === 'MULTIPLE_SELECT') {
      setCorrect((c) => (c.includes(i) ? c.filter((x) => x !== i) : [...c, i]))
    } else {
      setCorrect([i])
    }
  }

  async function submit() {
    if (!prompt.trim()) {
      toast.error('Write the question first.')
      return
    }
    const input: AskQuestionInput = { type, prompt: prompt.trim(), marks }
    if (type === 'MCQ' || type === 'MULTIPLE_SELECT') {
      input.options = options.map((o) => o.trim())
      input.correctOptions = correct
    } else if (type === 'TRUE_FALSE') {
      input.correctOptions = correct
    } else if (type === 'SHORT_ANSWER') {
      input.acceptedAnswers = accepted.split(',').map((a) => a.trim()).filter(Boolean)
    } else if (type === 'CODING') {
      input.language = language
      input.starterCode = starterCode
    }
    if (explanation.trim()) input.explanation = explanation.trim()

    setBusy(true)
    try {
      await askQuestion(liveSessionId, input)
      toast.success('Question sent to the class')
      setPrompt(''); setOptions(['', '']); setCorrect([]); setAccepted(''); setStarterCode(''); setExplanation(''); setMarks(1)
      onAsked()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not ask the question.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border p-3">
      <h3 className="text-sm font-medium">Ask a question</h3>
      <div className="flex flex-wrap gap-1">
        {TYPES.map((t) => (
          <Button key={t} size="sm" variant={type === t ? 'default' : 'outline'} onClick={() => { setType(t); setCorrect([]) }}>
            {LIVE_QUESTION_TYPE_LABEL[t]}
          </Button>
        ))}
      </div>
      <Input value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder="Type the question" maxLength={4000} />

      {(type === 'MCQ' || type === 'MULTIPLE_SELECT') && (
        <div className="flex flex-col gap-1">
          {options.map((opt, i) => (
            <div key={i} className="flex items-center gap-2">
              {type === 'MCQ' ? (
                <RadioGroup value={String(correct[0] ?? '')} onValueChange={(v) => setCorrect([Number(v)])}>
                  <RadioGroupItem value={String(i)} />
                </RadioGroup>
              ) : (
                <Checkbox checked={correct.includes(i)} onCheckedChange={() => toggleCorrect(i)} />
              )}
              <Input
                value={opt}
                onChange={(e) => setOptions((o) => o.map((x, idx) => (idx === i ? e.target.value : x)))}
                placeholder={`Option ${i + 1}`}
              />
              {options.length > 2 && (
                <Button size="icon" variant="ghost" onClick={() => setOptions((o) => o.filter((_, idx) => idx !== i))}>
                  <Trash2 className="size-4" />
                </Button>
              )}
            </div>
          ))}
          {options.length < 8 && (
            <Button size="sm" variant="ghost" className="self-start" onClick={() => setOptions((o) => [...o, ''])}>
              <Plus className="size-4" /> Add option
            </Button>
          )}
        </div>
      )}

      {type === 'TRUE_FALSE' && (
        <RadioGroup value={String(correct[0] ?? '')} onValueChange={(v) => setCorrect([Number(v)])} className="flex gap-4">
          <label className="flex items-center gap-2 text-sm"><RadioGroupItem value="0" /> True</label>
          <label className="flex items-center gap-2 text-sm"><RadioGroupItem value="1" /> False</label>
        </RadioGroup>
      )}

      {type === 'SHORT_ANSWER' && (
        <Input value={accepted} onChange={(e) => setAccepted(e.target.value)} placeholder="Accepted answers, comma separated (optional - leave blank to mark yourself)" />
      )}

      {type === 'CODING' && (
        <div className="flex flex-col gap-2">
          <Input value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="Language (e.g. python, java)" className="w-40" />
          <Suspense fallback={<div className="flex h-24 items-center justify-center"><Loader2 className="size-4 animate-spin" /></div>}>
            <CodeEditor language="PYTHON" value={starterCode} onChange={setStarterCode} height={160} ariaLabel="Starter code" />
          </Suspense>
        </div>
      )}

      {type !== 'CODING' && type !== 'OTHER' && (
        <Input value={explanation} onChange={(e) => setExplanation(e.target.value)} placeholder="Explanation shown after answering (optional)" />
      )}

      <div className="flex items-center gap-2">
        <label className="text-sm text-muted-foreground">Marks</label>
        <Input type="number" min={1} max={100} value={marks} onChange={(e) => setMarks(Number(e.target.value) || 1)} className="w-20" />
        <Button size="sm" disabled={busy} onClick={() => void submit()} className="ml-auto">
          {busy && <Loader2 className="size-4 animate-spin" />} Ask the class
        </Button>
      </div>
    </div>
  )
}
