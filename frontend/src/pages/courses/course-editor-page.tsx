import { type ChangeEvent, type FormEvent, useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Plus, Trash2, Upload } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { CODE_LANGUAGES, type CodeLanguageCode } from '@/api/code'
import {
  addLesson,
  addModule,
  archiveCourse,
  type CourseInput,
  deleteLesson,
  deleteModule,
  getCourseDetail,
  type LessonInput,
  type LessonResponse,
  publishCourse,
  updateCourse,
} from '@/api/courses'
import { uploadFile } from '@/api/files'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const LESSON_TYPES: LessonResponse['type'][] = ['VIDEO', 'PDF', 'NOTE', 'LINK', 'TEXT']

export function CourseEditorPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const queryClient = useQueryClient()
  const role = useAuthStore((state) => state.user?.role)
  const isStaff = hasRole(role, ['ADMIN', 'COORDINATOR'])

  const query = useQuery({
    queryKey: ['courses', 'detail', courseId],
    queryFn: () => getCourseDetail(courseId!),
    enabled: !!courseId,
  })

  const [form, setForm] = useState<CourseInput | null>(null)
  useEffect(() => {
    if (query.data) {
      const c = query.data.course
      setForm({
        title: c.title,
        code: c.code,
        summary: c.summary ?? '',
        description: c.description ?? '',
        level: c.level,
        durationHours: c.durationHours ?? undefined,
        fee: c.fee ?? undefined,
      })
    }
  }, [query.data])

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: ['courses', 'detail', courseId] })
  }

  const saveMutation = useMutation({
    mutationFn: () => updateCourse(courseId!, form!),
    onSuccess: () => {
      toast.success('Saved')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the course.')),
  })

  const publishMutation = useMutation({
    mutationFn: () => publishCourse(courseId!),
    onSuccess: () => {
      toast.success('Course published')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not publish. Check summary, description and lessons.')),
  })

  const archiveMutation = useMutation({
    mutationFn: () => archiveCourse(courseId!),
    onSuccess: () => {
      toast.success('Course archived')
      void invalidate()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not archive the course.')),
  })

  const deleteModuleMutation = useMutation({
    mutationFn: (moduleId: number) => deleteModule(moduleId),
    onSuccess: () => void invalidate(),
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the module.')),
  })

  const deleteLessonMutation = useMutation({
    mutationFn: (lessonId: number) => deleteLesson(lessonId),
    onSuccess: () => void invalidate(),
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the lesson.')),
  })

  if (query.isLoading || !form) return <Skeleton className="h-96 max-w-3xl" />
  const { course, modules } = query.data!

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Link to="/app/courses" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Courses
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{course.title}</h1>
          <p className="text-muted-foreground">
            {course.code} &middot; {modules.reduce((n, m) => n + m.lessons.length, 0)} lessons
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge>{course.status}</Badge>
          {isStaff && course.status === 'DRAFT' && (
            <Button size="sm" onClick={() => publishMutation.mutate()} disabled={publishMutation.isPending}>
              Publish
            </Button>
          )}
          {isStaff && course.status !== 'ARCHIVED' && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => archiveMutation.mutate()}
              disabled={archiveMutation.isPending}
            >
              Archive
            </Button>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Details</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-4"
            onSubmit={(event: FormEvent) => {
              event.preventDefault()
              saveMutation.mutate()
            }}
          >
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="title">Title</Label>
                <Input
                  id="title"
                  value={form.title}
                  disabled={!isStaff}
                  onChange={(event) => setForm({ ...form, title: event.target.value })}
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="code">Code</Label>
                <Input
                  id="code"
                  value={form.code}
                  disabled={!isStaff}
                  onChange={(event) => setForm({ ...form, code: event.target.value })}
                />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="summary">Summary</Label>
              <Input
                id="summary"
                value={form.summary ?? ''}
                disabled={!isStaff}
                onChange={(event) => setForm({ ...form, summary: event.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="description">Description</Label>
              <textarea
                id="description"
                rows={3}
                className="rounded-md border bg-transparent px-3 py-2 text-sm disabled:opacity-60"
                value={form.description ?? ''}
                disabled={!isStaff}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </div>
            {isStaff && (
              <Button type="submit" size="sm" className="self-start" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? 'Saving...' : 'Save details'}
              </Button>
            )}
          </form>
        </CardContent>
      </Card>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">Curriculum</h2>
          <AddModuleDialog courseId={course.id} onAdded={invalidate} />
        </div>
        <div className="flex flex-col gap-4">
          {modules.map((module) => (
            <Card key={module.id}>
              <CardHeader className="flex-row items-center justify-between space-y-0">
                <CardTitle className="text-base">{module.title}</CardTitle>
                {isStaff && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => deleteModuleMutation.mutate(module.id)}
                    disabled={deleteModuleMutation.isPending}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                )}
              </CardHeader>
              <CardContent className="flex flex-col gap-2">
                {module.lessons.map((lesson) => (
                  <div key={lesson.id} className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                    <span>
                      {lesson.title} <span className="text-xs text-muted-foreground">({lesson.type})</span>
                      {lesson.mandatory && (
                        <Badge variant="outline" className="ml-2 text-[10px]">
                          Mandatory
                        </Badge>
                      )}
                    </span>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => deleteLessonMutation.mutate(lesson.id)}
                      disabled={deleteLessonMutation.isPending}
                    >
                      <Trash2 className="size-3.5" />
                    </Button>
                  </div>
                ))}
                <AddLessonDialog moduleId={module.id} onAdded={invalidate} />
              </CardContent>
            </Card>
          ))}
          {modules.length === 0 && <p className="text-sm text-muted-foreground">No modules yet.</p>}
        </div>
      </div>
    </div>
  )
}

function AddModuleDialog({ courseId, onAdded }: { courseId: number; onAdded: () => void }) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  const mutation = useMutation({
    mutationFn: () => addModule(courseId, { title, description: description || undefined }),
    onSuccess: () => {
      setOpen(false)
      setTitle('')
      setDescription('')
      onAdded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the module.')),
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Plus className="size-3.5" />
          Add module
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a module</DialogTitle>
        </DialogHeader>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event: FormEvent) => {
            event.preventDefault()
            mutation.mutate()
          }}
        >
          <div className="flex flex-col gap-2">
            <Label htmlFor="moduleTitle">Title</Label>
            <Input id="moduleTitle" value={title} onChange={(event) => setTitle(event.target.value)} required />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="moduleDescription">Description</Label>
            <Input id="moduleDescription" value={description} onChange={(event) => setDescription(event.target.value)} />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Adding...' : 'Add module'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function AddLessonDialog({ moduleId, onAdded }: { moduleId: number; onAdded: () => void }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<LessonInput>({ title: '', type: 'VIDEO' })
  const [uploading, setUploading] = useState(false)

  const mutation = useMutation({
    mutationFn: () => addLesson(moduleId, form),
    onSuccess: () => {
      setOpen(false)
      setForm({ title: '', type: 'VIDEO' })
      onAdded()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not add the lesson.')),
  })

  async function handleFilePick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const uploaded = await uploadFile(file, 'LESSON_RESOURCE')
      setForm((prev) => ({ ...prev, contentFileRef: String(uploaded.id) }))
      toast.success(`Uploaded ${uploaded.filename}`)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not upload the file.'))
    } finally {
      setUploading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost" className="self-start">
          <Plus className="size-3.5" />
          Add lesson
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a lesson</DialogTitle>
        </DialogHeader>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event: FormEvent) => {
            event.preventDefault()
            mutation.mutate()
          }}
        >
          <div className="flex flex-col gap-2">
            <Label htmlFor="lessonTitle">Title</Label>
            <Input
              id="lessonTitle"
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="lessonType">Type</Label>
            <select
              id="lessonType"
              value={form.type}
              onChange={(event) => setForm({ ...form, type: event.target.value as LessonResponse['type'] })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              {LESSON_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>

          {(form.type === 'VIDEO' || form.type === 'LINK') && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="contentUrl">{form.type === 'VIDEO' ? 'Video URL' : 'Link URL'}</Label>
              <Input
                id="contentUrl"
                value={form.contentUrl ?? ''}
                onChange={(event) => setForm({ ...form, contentUrl: event.target.value })}
              />
            </div>
          )}
          {form.type === 'PDF' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="pdfFile">PDF file</Label>
              <Input id="pdfFile" type="file" accept="application/pdf" onChange={(event) => void handleFilePick(event)} />
              {uploading && (
                <p className="flex items-center gap-1 text-xs text-muted-foreground">
                  <Upload className="size-3 animate-pulse" />
                  Uploading...
                </p>
              )}
              {form.contentFileRef && <p className="text-xs text-emerald-600">File uploaded.</p>}
            </div>
          )}
          {(form.type === 'NOTE' || form.type === 'TEXT') && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="textContent">Content</Label>
              <textarea
                id="textContent"
                rows={4}
                className="rounded-md border bg-transparent px-3 py-2 text-sm"
                value={form.textContent ?? ''}
                onChange={(event) => setForm({ ...form, textContent: event.target.value })}
              />
            </div>
          )}

          <div className="flex flex-col gap-2">
            <Label htmlFor="codeLanguage">Practice editor (optional)</Label>
            <select
              id="codeLanguage"
              value={form.codeLanguage ?? ''}
              onChange={(event) => {
                const language = (event.target.value || undefined) as CodeLanguageCode | undefined
                // Starter code without a language is refused by the server, so it goes when the language does.
                setForm({ ...form, codeLanguage: language, starterCode: language ? form.starterCode : undefined })
              }}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              <option value="">No editor</option>
              {CODE_LANGUAGES.map((language) => (
                <option key={language.code} value={language.code}>
                  {language.label}
                </option>
              ))}
            </select>
            <p className="text-xs text-muted-foreground">
              Students get a code editor under the lesson to try what they learned.
            </p>
          </div>
          {form.codeLanguage && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="starterCode">Starter code</Label>
              <textarea
                id="starterCode"
                rows={6}
                spellCheck={false}
                wrap="off"
                className="rounded-md border bg-transparent px-3 py-2 font-mono text-sm whitespace-pre"
                placeholder="What the editor opens with (optional)"
                value={form.starterCode ?? ''}
                onChange={(event) => setForm({ ...form, starterCode: event.target.value })}
              />
            </div>
          )}

          <div className="flex flex-col gap-2">
            <Label htmlFor="durationMinutes">Duration (minutes)</Label>
            <Input
              id="durationMinutes"
              type="number"
              onChange={(event) =>
                setForm({ ...form, durationMinutes: event.target.value ? Number(event.target.value) : undefined })
              }
            />
          </div>

          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending || uploading}>
              {mutation.isPending ? 'Adding...' : 'Add lesson'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
