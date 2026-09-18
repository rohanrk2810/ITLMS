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
  publishQuiz,
  type QuestionInput,
  type QuestionOptionInput,
} from '@/api/assessments'
import { apiErrorMessage } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const QUESTION_TYPES = ['SINGLE_CHOICE', 'MULTI_CHOICE', 'TRUE_FALSE']

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
                <ul className="flex flex-col gap-1 text-sm">
                  {question.options.map((option) => (
                    <li key={option.id} className={option.correct ? 'font-medium text-emerald-600' : 'text-muted-foreground'}>
                      {option.correct ? '✓ ' : '○ '}
                      {option.optionText}
                    </li>
                  ))}
                </ul>
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
                      {result.passed ? 'Passed' : 'Not passed'}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}

function AddQuestionDialog({ quizId, onAdded }: { quizId: number; onAdded: () => void }) {
  const [open, setOpen] = useState(false)
  const [questionText, setQuestionText] = useState('')
  const [type, setType] = useState('SINGLE_CHOICE')
  const [marks, setMarks] = useState(1)
  const [options, setOptions] = useState<QuestionOptionInput[]>([
    { optionText: '', correct: true },
    { optionText: '', correct: false },
  ])

  const mutation = useMutation({
    mutationFn: () => {
      const input: QuestionInput = { questionText, type, marks, options }
      return addQuestion(quizId, input)
    },
    onSuccess: () => {
      setOpen(false)
      setQuestionText('')
      setOptions([
        { optionText: '', correct: true },
        { optionText: '', correct: false },
      ])
      onAdded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the question.')),
  })

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
      <DialogContent className="max-h-[85vh] overflow-y-auto">
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
                onChange={(event) => setType(event.target.value)}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                {QUESTION_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="marks">Marks</Label>
              <Input id="marks" type="number" min={1} value={marks} onChange={(event) => setMarks(Number(event.target.value))} />
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <Label>Options - tick the correct one(s)</Label>
            {options.map((option, index) => (
              <div key={index} className="flex items-center gap-2">
                <Checkbox checked={option.correct} onCheckedChange={() => toggleCorrect(index)} />
                <Input
                  value={option.optionText}
                  placeholder={`Option ${index + 1}`}
                  onChange={(event) => updateOption(index, { optionText: event.target.value })}
                  required
                />
                {options.length > 2 && (
                  <Button type="button" size="sm" variant="ghost" onClick={() => removeOption(index)}>
                    <Trash2 className="size-3.5" />
                  </Button>
                )}
              </div>
            ))}
            {options.length < 8 && (
              <Button type="button" size="sm" variant="ghost" className="self-start" onClick={addOption}>
                <Plus className="size-3.5" />
                Add option
              </Button>
            )}
          </div>

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
