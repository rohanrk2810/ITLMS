import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera, Clock, Keyboard, Mic, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { parseTestJson, type TestJson } from '@/lib/test-json'
import { useAsk } from '@/components/ask-dialog'

import { apiErrorMessage } from '@/api/client'
import { CAMERA_CHECKS, type CameraCheckName, addQuestion, createQuiz, deleteQuiz, listQuizzes, type QuizInput } from '@/api/assessments'
import { searchCourses } from '@/api/courses'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuthStore } from '@/stores/auth-store'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  DRAFT: 'secondary',
  PUBLISHED: 'default',
  CLOSED: 'outline',
}

export function ManageTestsPage() {
  const quizzesQuery = useQuery({ queryKey: ['quizzes', 'manage'], queryFn: () => listQuizzes() })
  const coursesQuery = useQuery({ queryKey: ['courses', 'search', 'all'], queryFn: () => searchCourses({}) })
  const courseTitles = new Map((coursesQuery.data?.content ?? []).map((course) => [course.id, course.title]))
  const isAdmin = useAuthStore((state) => state.user?.role) === 'ADMIN'
  const queryClient = useQueryClient()
  const ask = useAsk()

  const deleteMutation = useMutation({
    mutationFn: (quizId: number) => deleteQuiz(quizId),
    onSuccess: () => {
      toast.success('Test deleted')
      void queryClient.invalidateQueries({ queryKey: ['quizzes'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the test.')),
  })

  async function handleDelete(quizId: number, title: string) {
    const ok = await ask.confirm({
      title: `Delete "${title}"?`,
      message: "Its questions and every student's attempt and answers are removed too. This cannot be undone.",
      destructive: true,
    })
    if (ok) deleteMutation.mutate(quizId)
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      {ask.dialog}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Tests</h1>
          <p className="text-muted-foreground">
            Set a test, add questions (choice, short answer or code), publish it, and see how your students did.
          </p>
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
              {isAdmin && <TableHead className="w-12" />}
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
                  {quiz.requireCamera && (
                    <Badge variant="outline" className="ml-2 gap-1">
                      <Camera className="size-3" />
                      Camera
                    </Badge>
                  )}
                  {(quiz.availableFrom || quiz.availableUntil) && (
                    <Badge variant="outline" className="ml-2 gap-1">
                      <Clock className="size-3" />
                      Fixed time
                    </Badge>
                  )}
                  {quiz.recordTyping && (
                    <Badge variant="outline" className="ml-2 gap-1">
                      <Keyboard className="size-3" />
                      Typing replay
                    </Badge>
                  )}
                  {quiz.requireMicrophone && (
                    <Badge variant="outline" className="ml-2 gap-1">
                      <Mic className="size-3" />
                      Microphone
                    </Badge>
                  )}
                </TableCell>
                <TableCell>{courseTitles.get(quiz.courseId) ?? `#${quiz.courseId}`}</TableCell>
                <TableCell>{quiz.questionCount}</TableCell>
                <TableCell>{quiz.durationMinutes} min</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[quiz.status] ?? 'outline'}>{quiz.status}</Badge>
                </TableCell>
                {isAdmin && (
                  <TableCell>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="text-destructive"
                      aria-label={`Delete ${quiz.title}`}
                      disabled={deleteMutation.isPending}
                      onClick={() => handleDelete(quiz.id, quiz.title)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            ))}
            {quizzesQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={isAdmin ? 6 : 5} className="text-center text-muted-foreground">
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
  // When the test can be taken: any time once it is published, or only between two moments (then it expires).
  const [timing, setTiming] = useState<'anytime' | 'scheduled'>('anytime')
  const [opensAt, setOpensAt] = useState('')
  const [closesAt, setClosesAt] = useState('')
  const [pasted, setPasted] = useState('')
  const [fromFile, setFromFile] = useState<{ name: string; data: TestJson } | null>(null)
  const queryClient = useQueryClient()
  const coursesQuery = useQuery({ queryKey: ['courses', 'search', 'new-test'], queryFn: () => searchCourses({}), enabled: open })

  const mutation = useMutation({
    mutationFn: async (payload: Partial<QuizInput>) => {
      const quiz = await createQuiz(payload as QuizInput)
      let added = 0
      for (const question of fromFile?.data.questions ?? []) {
        await addQuestion(quiz.id, question)
        added += 1
      }
      return added
    },
    onSuccess: (added) => {
      toast.success(added > 0 ? `Test created as a draft with ${added} questions` : 'Test created as a draft')
      setOpen(false)
      setFromFile(null)
      setPasted('')
      void queryClient.invalidateQueries({ queryKey: ['quizzes'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the test.')),
  })

  async function handleFile(file: File | undefined) {
    if (file) loadJson(await file.text(), file.name)
  }

  function loadJson(text: string, name: string) {
    try {
      // ChatGPT often wraps its reply in a ```json fence; keep only the JSON.
      const start = text.search(/[\[{]/)
      const end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'))
      const data = parseTestJson(start >= 0 && end > start ? text.slice(start, end + 1) : text)
      setFromFile({ name, data })
      setForm((current) => ({ ...current, ...Object.fromEntries(Object.entries(data.settings).filter(([, v]) => v !== undefined)) }))
      if (data.skipped.length) toast.warning(`${data.skipped.length} left out: ${data.skipped.slice(0, 3).join('; ')}`)
    } catch (error) {
      setFromFile(null)
      toast.error(error instanceof Error ? error.message : 'Could not read the file.')
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (timing === 'anytime') {
      mutation.mutate({ ...form, availableFrom: undefined, availableUntil: undefined })
      return
    }
    const opens = new Date(opensAt)
    const closes = new Date(closesAt)
    if (!opensAt || !closesAt || Number.isNaN(opens.getTime()) || Number.isNaN(closes.getTime())) {
      toast.error('Choose when the test opens and when it closes.')
      return
    }
    if (closes <= opens) {
      toast.error('The closing time must be after the opening time.')
      return
    }
    mutation.mutate({ ...form, availableFrom: opens.toISOString(), availableUntil: closes.toISOString() })
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
          <div className="flex flex-col gap-2 rounded-md border border-dashed p-3">
            <Label htmlFor="testJson">Create from a JSON file (optional)</Label>
            <Input id="testJson" type="file" accept=".json,application/json" onChange={(e) => void handleFile(e.target.files?.[0])} />
            <Label htmlFor="testPaste">or paste ChatGPT's reply here</Label>
            <textarea
              id="testPaste"
              rows={4}
              value={pasted}
              onChange={(e) => setPasted(e.target.value)}
              placeholder='{"questions": [ ... ]}'
              className="rounded-md border bg-transparent p-2 font-mono text-xs"
            />
            <Button type="button" variant="outline" size="sm" className="self-start" disabled={!pasted.trim()} onClick={() => loadJson(pasted, 'pasted text')}>
              Use pasted text
            </Button>
            <p className="text-xs text-muted-foreground">
              {fromFile
                ? `${fromFile.name}: ${fromFile.data.questions.length} questions will be added.`
                : 'Same format as the question bank JSON. Title, duration, pass % and attempts may be set in the file too.'}
            </p>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="courseId">Course</Label>
            <select
              id="courseId"
              required
              value={form.courseId ?? ''}
              onChange={(event) => setForm({ ...form, courseId: Number(event.target.value) })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              <option value="" disabled>
                Select a course
              </option>
              {coursesQuery.data?.content.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.title} ({course.code})
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="title">Title</Label>
            <Input
              id="title"
              required
              value={form.title ?? ''}
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
            <span className="text-sm font-medium">When can students take it?</span>
            <RadioGroup value={timing} onValueChange={(value) => setTiming(value as 'anytime' | 'scheduled')}>
              <label className="flex items-start gap-2 text-sm">
                <RadioGroupItem value="anytime" className="mt-0.5" />
                <span>
                  Any time
                  <span className="block text-xs text-muted-foreground">
                    Open from the moment it is published until you close it.
                  </span>
                </span>
              </label>
              <label className="flex items-start gap-2 text-sm">
                <RadioGroupItem value="scheduled" className="mt-0.5" />
                <span>
                  At a fixed time
                  <span className="block text-xs text-muted-foreground">
                    Students must start it between the two times. After the closing time it has expired and cannot be
                    started. A sitting that is already running ends at the closing time too.
                  </span>
                </span>
              </label>
            </RadioGroup>
            {timing === 'scheduled' && (
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="opensAt">Opens</Label>
                  <Input id="opensAt" type="datetime-local" value={opensAt} onChange={(event) => setOpensAt(event.target.value)} />
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="closesAt">Closes (expires)</Label>
                  <Input id="closesAt" type="datetime-local" value={closesAt} onChange={(event) => setClosesAt(event.target.value)} />
                </div>
              </div>
            )}
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
              many times ends the attempt as failed (the student must then take it again). Copying is blocked. Everything is recorded for you to review.
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
                  value={form.maxViolations ?? 1}
                  onChange={(event) => setForm({ ...form, maxViolations: Number(event.target.value) })}
                />
                <p className="text-xs text-muted-foreground">
                  1 fails the test at once. 2 means the first time is a warning and the second ends the test.
                </p>
              </div>
            )}
          </div>
          <div className="flex flex-col gap-2 rounded-md border p-3">
            <label className="flex items-center gap-2 text-sm font-medium">
              <Checkbox
                checked={form.requireCamera ?? false}
                onCheckedChange={(value) => setForm({ ...form, requireCamera: value === true })}
              />
              Require camera
            </label>
            <p className="text-xs text-muted-foreground">
              Students must allow their camera before starting, and show their face. During the test they are
              warned when no face (or more than one) is visible or the camera is turned off, and each of these is
              recorded with its time for you to review. Nothing is failed automatically, and no pictures are stored.
            </p>
            {form.requireCamera && (
              <div className="flex flex-col gap-2 border-t pt-2">
                <span className="text-sm font-medium">What to detect</span>
                {CAMERA_CHECKS.map((check) => {
                  const selected: CameraCheckName[] = form.cameraChecks ?? CAMERA_CHECKS.map((c) => c.name)
                  return (
                    <label key={check.name} className="flex items-start gap-2 text-sm">
                      <Checkbox
                        className="mt-0.5"
                        checked={selected.includes(check.name)}
                        onCheckedChange={(value) =>
                          setForm({
                            ...form,
                            cameraChecks:
                              value === true ? [...selected, check.name] : selected.filter((n) => n !== check.name),
                          })
                        }
                      />
                      <span>
                        {check.label}
                        <span className="block text-xs text-muted-foreground">{check.help}</span>
                      </span>
                    </label>
                  )
                })}
                <p className="text-xs text-muted-foreground">
                  The camera must stay on in every case; with all of these off it is only that.
                </p>
              </div>
            )}
          </div>
          <div className="flex flex-col gap-2 rounded-md border p-3">
            <label className="flex items-center gap-2 text-sm font-medium">
              <Checkbox
                checked={form.requireMicrophone ?? false}
                onCheckedChange={(value) => setForm({ ...form, requireMicrophone: value === true })}
              />
              Require microphone
            </label>
            <p className="text-xs text-muted-foreground">
              Students must allow their microphone before starting. During the test they are warned when it is turned
              off or when sustained sound is picked up, and each is recorded with its time for you to review. Only the
              loudness is measured against the room&apos;s own background level: nothing is recorded or stored, it cannot
              tell speech from other noise, and nothing is failed automatically.
            </p>
          </div>
          <div className="flex flex-col gap-2 rounded-md border p-3">
            <label className="flex items-center gap-2 text-sm font-medium">
              <Checkbox
                checked={form.recordTyping ?? false}
                onCheckedChange={(value) => setForm({ ...form, recordTyping: value === true })}
              />
              Record typing (typing replay)
            </label>
            <p className="text-xs text-muted-foreground">
              For coding questions, the student&apos;s browser records every edit with its time. You can replay how each
              answer was written, and a solution that appeared all at once (pasted) stands out. Students are told it is
              recorded. It is what their browser reports, so use it as a hint, not as proof.
            </p>
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
