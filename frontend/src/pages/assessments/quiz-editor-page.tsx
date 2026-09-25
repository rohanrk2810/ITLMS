import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Plus, Trash2 } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import {
  addQuestion,
  closeQuiz,
  deleteQuestion,
  getQuiz,
  getQuizResults,
  listViolations,
  publishQuiz,
  QUESTION_TYPE_LABEL,
  type QuestionInput,
  type QuestionOptionInput,
  type QuestionType,
  type QuizQuestionWithKey,
  type TestCaseInput,
  VIOLATION_LABEL,
} from '@/api/assessments'
import { CODE_LANGUAGES, type CodeLanguageCode, codeLanguageLabel } from '@/api/code'
import { apiErrorMessage } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

export function QuizEditorPage() {
  const { quizId } = useParams<{ quizId: string }>()
  const queryClient = useQueryClient()

  const query = useQuery({ queryKey: ['quizzes', quizId], queryFn: () => getQuiz(quizId!), enabled: !!quizId })
  const resultsQuery = useQuery({
    queryKey: ['quizzes', quizId, 'results'],
    queryFn: () => getQuizResults(quizId!),
    enabled: !!quizId && query.data?.status !== 'DRAFT',
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: ['quizzes', quizId] })
  }

  const publishMutation = useMutation({
    mutationFn: () => publishQuiz(quizId!),
    onSuccess: () => {
      toast.success('Test published')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not publish. Add at least one question first.')),
  })

  const closeMutation = useMutation({
    mutationFn: () => closeQuiz(quizId!),
    onSuccess: () => {
      toast.success('Test closed')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not close the test.')),
  })

  const deleteQuestionMutation = useMutation({
    mutationFn: (questionId: number) => deleteQuestion(questionId),
    onSuccess: () => void invalidate(),
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the question.')),
  })

  if (query.isLoading) return <Skeleton className="h-96 max-w-2xl" />
  const quiz = query.data
  if (!quiz) return null

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <Link to="/app/assessments" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Tests
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{quiz.title}</h1>
          <p className="text-muted-foreground">
            Course #{quiz.courseId} &middot; {quiz.durationMinutes} min &middot; {quiz.passPercentage}% to pass
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge>{quiz.status}</Badge>
          {quiz.status === 'DRAFT' && (
            <Button size="sm" onClick={() => publishMutation.mutate()} disabled={publishMutation.isPending}>
              Publish
            </Button>
          )}
          {quiz.status === 'PUBLISHED' && (
            <Button size="sm" variant="outline" onClick={() => closeMutation.mutate()} disabled={closeMutation.isPending}>
              Close
            </Button>
          )}
        </div>
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">
            Questions ({quiz.questionCount}, {quiz.totalMarks} marks)
          </h2>
          {quiz.status === 'DRAFT' && <AddQuestionDialog quizId={quiz.id} onAdded={invalidate} />}
        </div>
        <div className="flex flex-col gap-3">
          {quiz.questions?.map((question, index) => (
            <Card key={question.id}>
              <CardContent className="flex flex-col gap-2 pt-6">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-medium">
                    {index + 1}. {question.questionText}{' '}
                    <span className="text-xs font-normal text-muted-foreground">
                      ({question.marks} {question.marks === 1 ? 'mark' : 'marks'})
                    </span>
                  </p>
                  {quiz.status === 'DRAFT' && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => deleteQuestionMutation.mutate(question.id)}
                      disabled={deleteQuestionMutation.isPending}
                    >
                      <Trash2 className="size-3.5" />
                    </Button>
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  {QUESTION_TYPE_LABEL[question.type as QuestionType] ?? question.type}
                </p>
                <QuestionKey question={question} />
              </CardContent>
            </Card>
          ))}
          {quiz.questions?.length === 0 && <p className="text-sm text-muted-foreground">No questions yet.</p>}
        </div>
      </div>

      {resultsQuery.data && resultsQuery.data.length > 0 && (
        <div>
          <h2 className="mb-2 text-sm font-medium text-muted-foreground">Results</h2>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Student</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Result</TableHead>
                {quiz.secureMode && <TableHead>Violations</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {resultsQuery.data.map((result) => (
                <TableRow key={result.attemptId}>
                  <TableCell>{result.studentName}</TableCell>
                  <TableCell>
                    {result.score}/{result.totalMarks} ({result.percentage}%)
                  </TableCell>
                  <TableCell>
                    <Badge variant={result.passed ? 'default' : 'destructive'}>
                      {result.status === 'TERMINATED' ? 'Terminated' : result.passed ? 'Passed' : 'Not passed'}
                    </Badge>
                  </TableCell>
                  {quiz.secureMode && (
                    <TableCell>
                      <ViolationsButton attemptId={result.attemptId} count={result.violationCount} />
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}

/** How many times a student left the test window, and a way to see every event the browser reported. */
function ViolationsButton({ attemptId, count }: { attemptId: number; count: number }) {
  const [open, setOpen] = useState(false)
  const query = useQuery({
    queryKey: ['quiz-attempts', attemptId, 'violations'],
    queryFn: () => listViolations(attemptId),
    enabled: open,
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={count > 0 ? 'outline' : 'ghost'}>
          {count} counted &middot; review
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>What the browser reported</DialogTitle>
          <DialogDescription>
            Everything the student&apos;s browser reported, oldest first. Only the events marked counted add to the limit.
          </DialogDescription>
        </DialogHeader>
        {query.isLoading && <Skeleton className="h-24" />}
        {query.isSuccess && query.data.length === 0 && (
          <p className="text-sm text-muted-foreground">Nothing was reported during this attempt.</p>
        )}
        <ul className="flex flex-col gap-2 text-sm">
          {query.data?.map((event) => (
            <li key={event.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2">
              <span>
                {VIOLATION_LABEL[event.type] ?? event.type}
                {event.detail && <span className="text-muted-foreground"> &middot; {event.detail}</span>}
              </span>
              <span className="flex items-center gap-2 text-xs text-muted-foreground">
                {new Date(event.occurredAt).toLocaleTimeString()}
                <Badge variant={event.counted ? 'destructive' : 'secondary'}>{event.counted ? 'Counted' : 'Recorded'}</Badge>
              </span>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}

/** What a trainer sees under a question: the key, whatever shape it takes. */
function QuestionKey({ question }: { question: QuizQuestionWithKey }) {
  if (question.type === 'SHORT_ANSWER') {
    return (
      <p className="text-sm text-emerald-600">
        Accepted: {question.acceptedAnswers.length > 0 ? question.acceptedAnswers.join(' / ') : '(none)'}
      </p>
    )
  }
  if (question.type === 'CODING') {
    return (
      <div className="flex flex-col gap-1 text-sm">
        <p className="text-muted-foreground">
          {question.codeLanguage ? codeLanguageLabel(question.codeLanguage) : 'No language'} &middot;{' '}
          {question.testCases.length} test case{question.testCases.length === 1 ? '' : 's'}
        </p>
        {question.starterCode && (
          <pre className="max-h-32 overflow-auto rounded-md border bg-muted/40 px-2 py-1 font-mono text-xs">{question.starterCode}</pre>
        )}
        <ul className="flex flex-col gap-1 text-xs">
          {question.testCases.map((c) => (
            <li key={c.id} className="rounded-md border px-2 py-1 font-mono">
              <span className="font-sans text-muted-foreground">
                #{c.sequenceNo} &middot; weight {c.weight}
                {c.hidden ? ' · hidden' : ''}:{' '}
              </span>
              {JSON.stringify(c.input)} &rarr; {JSON.stringify(c.expectedOutput)}
            </li>
          ))}
        </ul>
      </div>
    )
  }
  return (
    <ul className="flex flex-col gap-1 text-sm">
      {question.options.map((option) => (
        <li key={option.id} className={option.correct ? 'font-medium text-emerald-600' : 'text-muted-foreground'}>
          {option.correct ? '✓ ' : '○ '}
          {option.optionText}
        </li>
      ))}
    </ul>
  )
}

const TYPES: QuestionType[] = ['SINGLE_CHOICE', 'MULTI_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'CODING']
const BLANK_OPTIONS: QuestionOptionInput[] = [
  { optionText: '', correct: true },
  { optionText: '', correct: false },
]
const TRUE_FALSE_OPTIONS: QuestionOptionInput[] = [
  { optionText: 'True', correct: true },
  { optionText: 'False', correct: false },
]
const BLANK_CASE: TestCaseInput = { input: '', expectedOutput: '', hidden: false, weight: 1 }

function AddQuestionDialog({ quizId, onAdded }: { quizId: number; onAdded: () => void }) {
  const [open, setOpen] = useState(false)
  const [questionText, setQuestionText] = useState('')
  const [type, setType] = useState<QuestionType>('SINGLE_CHOICE')
  const [marks, setMarks] = useState(1)
  const [options, setOptions] = useState<QuestionOptionInput[]>(BLANK_OPTIONS)
  const [accepted, setAccepted] = useState('')
  const [codeLanguage, setCodeLanguage] = useState<CodeLanguageCode>('JAVA')
  const [starterCode, setStarterCode] = useState('')
  const [testCases, setTestCases] = useState<TestCaseInput[]>([BLANK_CASE])

  const isChoice = type === 'SINGLE_CHOICE' || type === 'MULTI_CHOICE' || type === 'TRUE_FALSE'

  function reset() {
    setQuestionText('')
    setOptions(BLANK_OPTIONS)
    setAccepted('')
    setStarterCode('')
    setTestCases([BLANK_CASE])
  }

  function buildInput(): QuestionInput {
    const base = { questionText, type, marks }
    if (isChoice) return { ...base, options }
    if (type === 'SHORT_ANSWER') {
      return { ...base, acceptedAnswers: accepted.split('\n').map((a) => a.trim()).filter(Boolean) }
    }
    return { ...base, codeLanguage, starterCode: starterCode || undefined, testCases }
  }

  const mutation = useMutation({
    mutationFn: () => addQuestion(quizId, buildInput()),
    onSuccess: () => {
      setOpen(false)
      reset()
      onAdded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the question.')),
  })

  function changeType(next: QuestionType) {
    setType(next)
    if (next === 'TRUE_FALSE') setOptions(TRUE_FALSE_OPTIONS)
    else if (type === 'TRUE_FALSE' || options.length === 0) setOptions(BLANK_OPTIONS)
    else if (next === 'SINGLE_CHOICE') {
      // Only one option can be right; keep the first ticked one.
      const first = options.findIndex((o) => o.correct)
      setOptions((prev) => prev.map((o, i) => ({ ...o, correct: i === Math.max(first, 0) })))
    }
  }

  function updateOption(index: number, patch: Partial<QuestionOptionInput>) {
    setOptions((prev) => prev.map((o, i) => (i === index ? { ...o, ...patch } : o)))
  }

  function toggleCorrect(index: number) {
    if (type === 'MULTI_CHOICE') {
      updateOption(index, { correct: !options[index].correct })
    } else {
      setOptions((prev) => prev.map((o, i) => ({ ...o, correct: i === index })))
    }
  }

  function addOption() {
    if (options.length >= 8) return
    setOptions((prev) => [...prev, { optionText: '', correct: false }])
  }

  function removeOption(index: number) {
    if (options.length <= 2) return
    setOptions((prev) => prev.filter((_, i) => i !== index))
  }

  function updateCase(index: number, patch: Partial<TestCaseInput>) {
    setTestCases((prev) => prev.map((c, i) => (i === index ? { ...c, ...patch } : c)))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Plus className="size-3.5" />
          Add question
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Add a question</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="questionText">Question</Label>
            <textarea
              id="questionText"
              rows={2}
              required
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={questionText}
              onChange={(event) => setQuestionText(event.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="type">Type</Label>
              <select
                id="type"
                value={type}
                onChange={(event) => changeType(event.target.value as QuestionType)}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                {TYPES.map((t) => (
                  <option key={t} value={t}>
                    {QUESTION_TYPE_LABEL[t]}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="marks">Marks</Label>
              <Input id="marks" type="number" min={1} value={marks} onChange={(event) => setMarks(Number(event.target.value))} />
            </div>
          </div>

          {isChoice && (
            <div className="flex flex-col gap-2">
              <Label>Options - tick the correct one{type === 'MULTI_CHOICE' ? 's' : ''}</Label>
              {options.map((option, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Checkbox checked={option.correct} onCheckedChange={() => toggleCorrect(index)} />
                  <Input
                    value={option.optionText}
                    placeholder={`Option ${index + 1}`}
                    onChange={(event) => updateOption(index, { optionText: event.target.value })}
                    readOnly={type === 'TRUE_FALSE'}
                    required
                  />
                  {type !== 'TRUE_FALSE' && options.length > 2 && (
                    <Button type="button" size="sm" variant="ghost" onClick={() => removeOption(index)}>
                      <Trash2 className="size-3.5" />
                    </Button>
                  )}
                </div>
              ))}
              {type !== 'TRUE_FALSE' && options.length < 8 && (
                <Button type="button" size="sm" variant="ghost" className="self-start" onClick={addOption}>
                  <Plus className="size-3.5" />
                  Add option
                </Button>
              )}
            </div>
          )}

          {type === 'SHORT_ANSWER' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="accepted">Accepted answers - one per line</Label>
              <textarea
                id="accepted"
                rows={3}
                required
                className="rounded-md border bg-transparent px-3 py-2 text-sm"
                value={accepted}
                onChange={(event) => setAccepted(event.target.value)}
              />
              <p className="text-xs text-muted-foreground">
                A typed answer is right if it equals one of these, ignoring capital letters and extra spaces.
              </p>
            </div>
          )}

          {type === 'CODING' && (
            <>
              <div className="flex flex-col gap-2">
                <Label htmlFor="codeLanguage">Language</Label>
                <select
                  id="codeLanguage"
                  value={codeLanguage}
                  onChange={(event) => setCodeLanguage(event.target.value as CodeLanguageCode)}
                  className="h-9 rounded-md border bg-transparent px-3 text-sm"
                >
                  {CODE_LANGUAGES.map((l) => (
                    <option key={l.code} value={l.code}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="starterCode">Starter code (optional)</Label>
                <textarea
                  id="starterCode"
                  rows={4}
                  spellCheck={false}
                  className="rounded-md border bg-transparent px-3 py-2 font-mono text-xs"
                  value={starterCode}
                  onChange={(event) => setStarterCode(event.target.value)}
                />
                {codeLanguage === 'JAVA' && (
                  <p className="text-xs text-muted-foreground">Java runs the first class in the file, so put the class with main first.</p>
                )}
              </div>
              <div className="flex flex-col gap-2">
                <Label>Test cases - what the program reads, and what it must print</Label>
                {testCases.map((c, index) => (
                  <div key={index} className="flex flex-col gap-2 rounded-md border p-3">
                    <div className="grid gap-2 sm:grid-cols-2">
                      <textarea
                        aria-label={`Test ${index + 1} input`}
                        rows={2}
                        placeholder="Input (leave empty if the program reads nothing)"
                        spellCheck={false}
                        className="rounded-md border bg-transparent px-2 py-1 font-mono text-xs"
                        value={c.input}
                        onChange={(event) => updateCase(index, { input: event.target.value })}
                      />
                      <textarea
                        aria-label={`Test ${index + 1} expected output`}
                        rows={2}
                        required
                        placeholder="Expected output"
                        spellCheck={false}
                        className="rounded-md border bg-transparent px-2 py-1 font-mono text-xs"
                        value={c.expectedOutput}
                        onChange={(event) => updateCase(index, { expectedOutput: event.target.value })}
                      />
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                      <label className="flex items-center gap-2">
                        <Checkbox checked={c.hidden} onCheckedChange={(v) => updateCase(index, { hidden: v === true })} />
                        Hidden (students see only pass or fail)
                      </label>
                      <label className="flex items-center gap-2">
                        Weight
                        <Input
                          type="number"
                          min={1}
                          max={100}
                          className="h-8 w-16"
                          value={c.weight}
                          onChange={(event) => updateCase(index, { weight: Math.max(1, Number(event.target.value)) })}
                        />
                      </label>
                      {testCases.length > 1 && (
                        <Button type="button" size="sm" variant="ghost" onClick={() => setTestCases((prev) => prev.filter((_, i) => i !== index))}>
                          <Trash2 className="size-3.5" />
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
                {testCases.length < 10 && (
                  <Button type="button" size="sm" variant="ghost" className="self-start" onClick={() => setTestCases((prev) => [...prev, BLANK_CASE])}>
                    <Plus className="size-3.5" />
                    Add test case
                  </Button>
                )}
                <p className="text-xs text-muted-foreground">
                  Marks follow the weight of the cases a student&apos;s program passes. Output is compared ignoring trailing spaces and blank lines.
                </p>
              </div>
            </>
          )}

          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Adding...' : 'Add question'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}