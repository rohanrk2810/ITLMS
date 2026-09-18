import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Megaphone, Plus, Send, XCircle } from 'lucide-react'
import { toast } from 'sonner'

import {
  type AnnouncementInput,
  type AnnouncementResponse,
  createAnnouncement,
  listAnnouncements,
  withdrawAnnouncement,
} from '@/api/announcements'
import { searchBatches } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import { searchCourses } from '@/api/courses'
import type { Role } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const AUDIENCES = ['ALL', 'ROLE', 'BATCH', 'COURSE'] as const
const ROLES: Role[] = ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT', 'PLACEMENT', 'FINANCE']

export function AnnouncementsPage() {
  const canCreate = hasRole(useAuthStore((state) => state.user?.role), ['ADMIN', 'COORDINATOR', 'TRAINER'])
  const queryClient = useQueryClient()

  const query = useQuery({ queryKey: ['announcements', 'list'], queryFn: () => listAnnouncements() })

  const withdrawMutation = useMutation({
    mutationFn: (id: number) => withdrawAnnouncement(id),
    onSuccess: () => {
      toast.success('Withdrawn')
      void queryClient.invalidateQueries({ queryKey: ['announcements'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not withdraw the announcement.')),
  })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Announcements</h1>
          <p className="text-muted-foreground">Messages from the institute (Doc S16).</p>
        </div>
        {canCreate && <NewAnnouncementDialog />}
      </div>

      {query.isLoading && <Skeleton className="h-48" />}
      {query.isSuccess && query.data.content.length === 0 && (
        <p className="text-sm text-muted-foreground">Nothing to show.</p>
      )}

      <div className="flex flex-col gap-3">
        {query.data?.content.map((announcement) => (
          <AnnouncementCard
            key={announcement.id}
            announcement={announcement}
            canManage={canCreate}
            onWithdraw={() => withdrawMutation.mutate(announcement.id)}
            withdrawing={withdrawMutation.isPending}
          />
        ))}
      </div>
    </div>
  )
}

function AnnouncementCard({
  announcement,
  canManage,
  onWithdraw,
  withdrawing,
}: {
  announcement: AnnouncementResponse
  canManage: boolean
  onWithdraw: () => void
  withdrawing: boolean
}) {
  return (
    <Card className={announcement.withdrawn ? 'opacity-60' : ''}>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <Megaphone className="size-4 text-muted-foreground" />
            {announcement.title}
          </CardTitle>
          <div className="flex items-center gap-2">
            <Badge variant="outline">{announcement.audience}</Badge>
            {announcement.withdrawn && <Badge variant="destructive">Withdrawn</Badge>}
          </div>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm">
        <p className="whitespace-pre-wrap">{announcement.message}</p>
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>
            {formatDate(announcement.createdAt)}
            {announcement.expiresAt && ` · expires ${formatDate(announcement.expiresAt)}`}
            {announcement.recipientCount != null && ` · ${announcement.recipientCount} recipients`}
          </span>
          {canManage && !announcement.withdrawn && (
            <Button size="sm" variant="ghost" disabled={withdrawing} onClick={onWithdraw}>
              <XCircle className="size-3.5" />
              Withdraw
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

const EMPTY: AnnouncementInput = { title: '', message: '', audience: 'ALL', sendEmail: false }

function NewAnnouncementDialog() {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<AnnouncementInput>(EMPTY)
  const queryClient = useQueryClient()

  const batchesQuery = useQuery({
    queryKey: ['batches', 'search', 'announce'],
    queryFn: () => searchBatches({}),
    enabled: open && form.audience === 'BATCH',
  })
  const coursesQuery = useQuery({
    queryKey: ['courses', 'search', 'announce'],
    queryFn: () => searchCourses({}),
    enabled: open && form.audience === 'COURSE',
  })

  const mutation = useMutation({
    mutationFn: () => createAnnouncement(form),
    onSuccess: () => {
      toast.success('Announcement sent')
      setOpen(false)
      setForm(EMPTY)
      void queryClient.invalidateQueries({ queryKey: ['announcements'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not send the announcement.')),
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
          New announcement
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New announcement</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="title">Title</Label>
            <Input id="title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="message">Message</Label>
            <textarea
              id="message"
              rows={4}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={form.message}
              onChange={(e) => setForm({ ...form, message: e.target.value })}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="audience">Audience</Label>
            <select
              id="audience"
              value={form.audience}
              onChange={(e) =>
                setForm({ ...form, audience: e.target.value as AnnouncementInput['audience'], targetRole: undefined, targetId: undefined })
              }
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              {AUDIENCES.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </select>
          </div>
          {form.audience === 'ROLE' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="targetRole">Role</Label>
              <select
                id="targetRole"
                value={form.targetRole ?? ''}
                onChange={(e) => setForm({ ...form, targetRole: e.target.value })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
                required
              >
                <option value="" disabled>
                  Select a role
                </option>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </div>
          )}
          {form.audience === 'BATCH' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="targetBatch">Batch</Label>
              <select
                id="targetBatch"
                value={form.targetId ?? ''}
                onChange={(e) => setForm({ ...form, targetId: Number(e.target.value) })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
                required
              >
                <option value="" disabled>
                  Select a batch
                </option>
                {batchesQuery.data?.content.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} ({b.batchCode})
                  </option>
                ))}
              </select>
            </div>
          )}
          {form.audience === 'COURSE' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="targetCourse">Course</Label>
              <select
                id="targetCourse"
                value={form.targetId ?? ''}
                onChange={(e) => setForm({ ...form, targetId: Number(e.target.value) })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
                required
              >
                <option value="" disabled>
                  Select a course
                </option>
                {coursesQuery.data?.content.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </div>
          )}
          <div className="flex flex-col gap-2">
            <Label htmlFor="expiresAt">Expires (optional)</Label>
            <Input
              id="expiresAt"
              type="date"
              value={form.expiresAt ?? ''}
              onChange={(e) => setForm({ ...form, expiresAt: e.target.value })}
            />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={form.sendEmail ?? false}
              onCheckedChange={(v) => setForm({ ...form, sendEmail: v === true })}
            />
            <Send className="size-3.5" />
            Also email everyone it reaches
          </label>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Sending...' : 'Send'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
