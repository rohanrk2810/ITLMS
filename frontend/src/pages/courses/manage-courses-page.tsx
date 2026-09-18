import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type CourseInput, createCourse, searchCourses } from '@/api/courses'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  DRAFT: 'secondary',
  PUBLISHED: 'default',
  ARCHIVED: 'outline',
}

export function ManageCoursesPage() {
  const [query, setQuery] = useState('')
  const role = useAuthStore((state) => state.user?.role)
  const canCreate = hasRole(role, ['ADMIN', 'COORDINATOR'])

  const coursesQuery = useQuery({
    queryKey: ['courses', 'manage', query],
    queryFn: () => searchCourses({ query: query || undefined }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Courses</h1>
          <p className="text-muted-foreground">The catalog, and every trainer's curriculum to build.</p>
        </div>
        {canCreate && <NewCourseDialog />}
      </div>

      <Input
        placeholder="Search title or code..."
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        className="max-w-xs"
      />

      {coursesQuery.isLoading && <Skeleton className="h-64" />}

      {coursesQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Code</TableHead>
              <TableHead>Level</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {coursesQuery.data.content.map((course) => (
              <TableRow key={course.id}>
                <TableCell>
                  <Link to={`/app/courses/${course.id}`} className="font-medium hover:underline">
                    {course.title}
                  </Link>
                </TableCell>
                <TableCell>{course.code}</TableCell>
                <TableCell>{course.level}</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[course.status] ?? 'outline'}>{course.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {coursesQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground">
                  No courses match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

function NewCourseDialog() {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<CourseInput>({ title: '', code: '' })
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => createCourse(form),
    onSuccess: () => {
      toast.success('Course created as a draft')
      setOpen(false)
      setForm({ title: '', code: '' })
      void queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the course.')),
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
          New course
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New course</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="title">Title</Label>
            <Input
              id="title"
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="code">Code</Label>
            <Input
              id="code"
              placeholder="JFS"
              value={form.code}
              onChange={(event) => setForm({ ...form, code: event.target.value })}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="summary">Summary</Label>
            <Input
              id="summary"
              value={form.summary ?? ''}
              onChange={(event) => setForm({ ...form, summary: event.target.value })}
            />
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
