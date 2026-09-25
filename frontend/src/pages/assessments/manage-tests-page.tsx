import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { createQuiz, listQuizzes, type QuizInput } from '@/api/assessments'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  DRAFT: 'secondary',
  PUBLISHED: 'default',
  CLOSED: 'outline',
}

export function ManageTestsPage() {
  const quizzesQuery = useQuery({ queryKey: ['quizzes', 'manage'], queryFn: () => listQuizzes() })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Tests</h1>
          <p className="text-muted-foreground">Every MCQ test across your courses.</p>
        </div>
        <NewQuizDialog />
      </div>

      {quizzesQuery.isLoading && <Skeleton className="h-64" />}

      {quizzesQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Course</TableHead>
              <TableHead>Questions</TableHead>
              <TableHead>Duration</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {quizzesQuery.data.content.map((quiz) => (
              <TableRow key={quiz.id}>
                <TableCell>
                  <Link to={`/app/assessments/tests/${quiz.id}`} className="font-medium hover:underline">
                    {quiz.title}
                  </Link>
                  {quiz.secureMode && (
                    <Badge variant="outline" className="ml-2 gap-1">
                      <ShieldCheck className="size-3" />
                      Secure
                    </Badge>
                  )}
                </TableCell>
                <TableCell>#{quiz.courseId}</TableCell>
                <TableCell>{quiz.questionCount}</TableCell>
                <TableCell>{quiz.durationMinutes} min</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[quiz.status] ?? 'outline'}>{quiz.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {quizzesQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No tests yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

function NewQuizDialog() {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<Partial<QuizInput>>({ durationMinutes: 30, passPercentage: 40, attemptsAllowed: 1 })
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => createQuiz(form as QuizInput),
    onSuccess: () => {
      toast.success('Test created as a draft')
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['quizzes'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the test.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New test
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New test</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="courseId">Course id</Label>
            <Input
              id="courseId"
              type="number"
              required
              onChange={(event) => setForm({ ...form, courseId: Number(event.target.value) })}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="title">Title</Label>
            <Input
              id="title"
              required
              onChange={(event) => setForm({ ...form, title: event.target.value })}
            />
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="durationMinutes">Duration (min)</Label>
              <Input
                id="durationMinutes"
                type="number"
                value={form.durationMinutes}
                onChange={(event) => setForm({ ...form, durationMinutes: Number(event.target.value) })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="passPercentage">Pass %</Label>
              <Input
                id="passPercentage"
                type="number"
                value={form.passPercentage}
                onChange={(event) => setForm({ ...form, passPercentage: Number(event.target.value) })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="attemptsAllowed">Attempts</Label>
              <Input
                id="attemptsAllowed"
                type="number"
                value={form.attemptsAllowed}
                onChange={(event) => setForm({ ...form, attemptsAllowed: Number(event.target.value) })}
              />
            </div>
          </div>
          <div className="flex flex-col gap-3 rounded-md border p-3">
            <label className="flex items-center gap-2 text-sm font-medium">
              <Checkbox
                checked={form.secureMode ?? false}
                onCheckedChange={(value) => setForm({ ...form, secureMode: value === true })}
              />
              Secure test mode
            </label>
            <p className="text-xs text-muted-foreground">
              The test runs in fullscreen. Leaving the test window (another tab or window) is warned about, and too
              many times ends the attempt as failed. Copying is blocked. Everything is recorded for you to review.
            </p>
            {form.secureMode && (
              <div className="flex flex-col gap-2">
                <Label htmlFor="maxViolations">Ends the attempt on violation number</Label>
                <Input
                  id="maxViolations"
                  type="number"
                  min={1}
                  max={10}
                  className="w-24"
                  value={form.maxViolations ?? 2}
                  onChange={(event) => setForm({ ...form, maxViolations: Number(event.target.value) })}
                />
                <p className="text-xs text-muted-foreground">
                  2 means the first time is a warning and the second ends the test. 1 ends it at once.
                </p>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Creating...' : 'Create draft'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
