import { lazy, Suspense, useState } from 'react'
import { Loader2, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { CODE_LANGUAGES, type CodeLanguageCode } from '@/api/code'
import {
  type AskQuestionInput,
  type CodeTestCase,
  type LiveQuestionType,
  LIVE_QUESTION_TYPE_LABEL,
} from '@/api/live-classes'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { parseTable, QuestionMaterial, tableToText } from './question-material'

const CodeEditor = lazy(() => import('@/components/code-editor'))

const TYPES: LiveQuestionType[] = ['MCQ', 'MULTIPLE_SELECT', 'TRUE_FALSE', 'SHORT_ANSWER', 'CODING', 'OTHER']

interface TableDraft {
  name: string
  text: string
}

/**
 * Writes one question: the text, the answer key, and optionally the code or database tables the students look at.
 * Used live in the room (to ask now) and on the lecture material page (to keep for later).
 */
export function QuestionForm({ initial, submitLabel, onSubmit, extraActions }: {
  initial?: AskQuestionInput
  submitLabel: string
  onSubmit: (input: AskQuestionInput) => Promise<void>
  extraActions?: (build: () => AskQuestionInput | null) => React.ReactNode
}) {
  const [type, setType] = useState<LiveQuestionType>(initial?.type ?? 'MCQ')
  const [prompt, setPrompt] = useState(initial?.prompt ?? '')
  const [options, setOptions] = useState(initial?.options ?? ['', ''])
  const [correct, setCorrect] = useState<number[]>(initial?.correctOptions ?? [])
  const [accepted, setAccepted] = useState(initial?.acceptedAnswers?.join(', ') ?? '')
  const [language, setLanguage] = useState(initial?.language ?? 'python')
  const [starterCode, setStarterCode] = useState(initial?.starterCode ?? '')
  const [setupSql, setSetupSql] = useState(initial?.setupSql ?? '')
  const [allowChoice, setAllowChoice] = useState(initial?.allowLanguageChoice ?? false)
  const [testCases, setTestCases] = useState<CodeTestCase[]>(initial?.testCases ?? [])
  const [explanation, setExplanation] = useState(initial?.explanation ?? '')
  const [marks, setMarks] = useState(initial?.marks ?? 1)
  const [snippet, setSnippet] = useState(initial?.snippet ?? '')
  const [showSnippet, setShowSnippet] = useState(!!initial?.snippet)
  const [tables, setTables] = useState<TableDraft[]>(
    (initial?.dataset ?? []).map((t) => ({ name: t.name, text: tableToText(t) })),
  )
  const [busy, setBusy] = useState(false)

  function toggleCorrect(i: number) {
    if (type === 'MULTIPLE_SELECT') {
      setCorrect((c) => (c.includes(i) ? c.filter((x) => x !== i) : [...c, i]))
    } else {
      setCorrect([i])
    }
  }

  /** The question as typed, or null (with a message) when something needed is missing. */
  function build(): AskQuestionInput | null {
    if (!prompt.trim()) {
      toast.error('Write the question first.')
      return null
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
      if (language === 'sql' && setupSql.trim()) input.setupSql = setupSql
      if (language !== 'sql') input.allowLanguageChoice = allowChoice
      const cases = testCases.filter((c) => c.expectedOutput.trim() !== '')
      if (cases.length !== testCases.length) {
        toast.error('Every test case needs an expected output.')
        return null
      }
      if (cases.length > 0) input.testCases = cases
    }
    if (explanation.trim()) input.explanation = explanation.trim()
    if (showSnippet && snippet.trim()) {
      input.snippet = snippet
      input.snippetLanguage = language
    }
    const parsed = tables.map((t) => parseTable(t.name, t.text))
    if (parsed.some((t) => t === null)) {
      toast.error('Each table needs a name and at least a header line.')
      return null
    }
    if (parsed.length > 0) input.dataset = parsed as NonNullable<(typeof parsed)[number]>[]
    return input
  }

  async function submit() {
    const input = build()
    if (!input) return
    setBusy(true)
    try {
      await onSubmit(input)
      setPrompt(''); setOptions(['', '']); setCorrect([]); setAccepted(''); setStarterCode('')
      setExplanation(''); setMarks(1); setSnippet(''); setShowSnippet(false); setTables([])
      setSetupSql(''); setTestCases([]); setAllowChoice(false)
    } finally {
      setBusy(false)
    }
  }

  const preview = (() => {
    const parsed = tables.map((t) => parseTable(t.name, t.text)).filter((t) => t !== null)
    return <QuestionMaterial snippet={showSnippet ? snippet : null} dataset={parsed} />
  })()

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-1">
        {TYPES.map((t) => (
          <Button key={t} size="sm" variant={type === t ? 'default' : 'outline'} onClick={() => { setType(t); setCorrect([]) }}>
            {LIVE_QUESTION_TYPE_LABEL[t]}
          </Button>
        ))}
      </div>
      <textarea
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        placeholder="Type the question"
        rows={2}
        maxLength={4000}
        className="rounded-md border bg-transparent px-3 py-2 text-sm"
      />

      <div className="flex flex-wrap gap-1">
        {!showSnippet && (
          <Button size="sm" variant="ghost" onClick={() => setShowSnippet(true)}>
            <Plus className="size-4" /> Show code
          </Button>
        )}
        {tables.length < 5 && (
          <Button size="sm" variant="ghost" onClick={() => setTables((t) => [...t, { name: '', text: '' }])}>
            <Plus className="size-4" /> Show database table
          </Button>
        )}
      </div>

      {showSnippet && (
        <div className="flex flex-col gap-1">
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">Code the students will see</span>
            <Button size="icon" variant="ghost" onClick={() => { setShowSnippet(false); setSnippet('') }}>
              <Trash2 className="size-4" />
            </Button>
          </div>
          <textarea
            value={snippet}
            onChange={(e) => setSnippet(e.target.value)}
            spellCheck={false}
            wrap="off"
            rows={6}
            placeholder={'for i in range(3):\n    print(i * 2)'}
            className="rounded-md border bg-transparent px-3 py-2 font-mono text-xs whitespace-pre"
          />
        </div>
      )}

      {tables.map((table, i) => (
        <div key={i} className="flex flex-col gap-1 rounded-md border p-2">
          <div className="flex items-center gap-2">
            <Input
              value={table.name}
              onChange={(e) => setTables((all) => all.map((t, idx) => (idx === i ? { ...t, name: e.target.value } : t)))}
              placeholder="Table name, e.g. employees"
              className="h-8"
            />
            <Button size="icon" variant="ghost" onClick={() => setTables((all) => all.filter((_, idx) => idx !== i))}>
              <Trash2 className="size-4" />
            </Button>
          </div>
          <textarea
            value={table.text}
            onChange={(e) => setTables((all) => all.map((t, idx) => (idx === i ? { ...t, text: e.target.value } : t)))}
            spellCheck={false}
            wrap="off"
            rows={5}
            placeholder={'id,name,salary\n1,Asha,50000\n2,Ravi,42000'}
            className="rounded-md border bg-transparent px-2 py-1 font-mono text-xs whitespace-pre"
          />
          <p className="text-xs text-muted-foreground">First line is the column names, then one row per line. Paste from Excel works.</p>
        </div>
      ))}

      {(showSnippet || tables.length > 0) && (
        <div className="flex flex-col gap-1">
          <span className="text-xs text-muted-foreground">How students will see it</span>
          {preview}
        </div>
      )}

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
        <Input value={accepted} onChange={(e) => setAccepted(e.target.value)} placeholder="Right answer(s), comma separated (optional - leave blank to mark yourself)" />
      )}

      {type === 'CODING' && (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm">
              Compiler
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                className="h-9 rounded-md border bg-transparent px-2 text-sm"
              >
                {CODE_LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code.toLowerCase()}>{l.label}</option>
                ))}
              </select>
            </label>
            {language !== 'sql' && (
              <label className="flex items-center gap-2 text-sm">
                <Checkbox checked={allowChoice} onCheckedChange={(v) => setAllowChoice(v === true)} />
                Let students choose the language
              </label>
            )}
          </div>
          <span className="text-xs text-muted-foreground">Starter code - what the editor opens with</span>
          <Suspense fallback={<div className="flex h-24 items-center justify-center"><Loader2 className="size-4 animate-spin" /></div>}>
            <CodeEditor language={language.toUpperCase() as CodeLanguageCode} value={starterCode} onChange={setStarterCode} height={160} ariaLabel="Starter code" />
          </Suspense>
          {language === 'sql' && (
            <div className="flex flex-col gap-1">
              <span className="text-xs text-muted-foreground">Database setup - CREATE TABLE and INSERT statements that run before the student's query</span>
              <textarea
                value={setupSql}
                onChange={(e) => setSetupSql(e.target.value)}
                spellCheck={false}
                wrap="off"
                rows={5}
                className="rounded-md border bg-transparent px-3 py-2 font-mono text-xs whitespace-pre"
                placeholder={'CREATE TABLE employees (id INT, name VARCHAR(50), salary INT);\nINSERT INTO employees VALUES (1, \'Asha\', 50000);'}
              />
            </div>
          )}
          <div className="flex flex-col gap-2 rounded-md border p-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium">Test cases ({testCases.length}/10)</span>
              <Button
                size="sm"
                variant="ghost"
                disabled={testCases.length >= 10}
                onClick={() => setTestCases((c) => [...c, { input: '', expectedOutput: '', hidden: false, weight: 1 }])}
              >
                <Plus className="size-4" /> Add test case
              </Button>
            </div>
            {testCases.length === 0 && (
              <p className="text-xs text-muted-foreground">
                Optional. With test cases a test marks the code automatically; without them the trainer reads it.
              </p>
            )}
            {testCases.map((tc, i) => (
              <div key={i} className="grid gap-1 rounded-md bg-muted/30 p-2 sm:grid-cols-2">
                <textarea
                  value={tc.input}
                  onChange={(e) => setTestCases((all) => all.map((c, idx) => (idx === i ? { ...c, input: e.target.value } : c)))}
                  placeholder="Input (what is typed in)"
                  rows={2}
                  spellCheck={false}
                  className="rounded-md border bg-transparent px-2 py-1 font-mono text-xs"
                />
                <textarea
                  value={tc.expectedOutput}
                  onChange={(e) => setTestCases((all) => all.map((c, idx) => (idx === i ? { ...c, expectedOutput: e.target.value } : c)))}
                  placeholder="Expected output"
                  rows={2}
                  spellCheck={false}
                  className="rounded-md border bg-transparent px-2 py-1 font-mono text-xs"
                />
                <div className="flex items-center gap-3 sm:col-span-2">
                  <label className="flex items-center gap-1 text-xs">
                    <Checkbox checked={tc.hidden} onCheckedChange={(v) => setTestCases((all) => all.map((c, idx) => (idx === i ? { ...c, hidden: v === true } : c)))} />
                    Hidden
                  </label>
                  <label className="flex items-center gap-1 text-xs">
                    Weight
                    <Input
                      type="number"
                      min={1}
                      max={100}
                      value={tc.weight}
                      onChange={(e) => setTestCases((all) => all.map((c, idx) => (idx === i ? { ...c, weight: Math.min(100, Math.max(1, Number(e.target.value) || 1)) } : c)))}
                      className="h-7 w-16"
                    />
                  </label>
                  <Button size="icon" variant="ghost" className="ml-auto" onClick={() => setTestCases((all) => all.filter((_, idx) => idx !== i))}>
                    <Trash2 className="size-4" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {type !== 'CODING' && type !== 'OTHER' && (
        <Input value={explanation} onChange={(e) => setExplanation(e.target.value)} placeholder="Explanation shown after answering (optional)" />
      )}

      <div className="flex flex-wrap items-center gap-2">
        <label className="text-sm text-muted-foreground">Marks</label>
        <Input type="number" min={1} max={100} value={marks} onChange={(e) => setMarks(Number(e.target.value) || 1)} className="w-20" />
        <div className="ml-auto flex gap-2">
          {extraActions?.(build)}
          <Button size="sm" disabled={busy} onClick={() => void submit()}>
            {busy && <Loader2 className="size-4 animate-spin" />} {submitLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
