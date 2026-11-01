import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Menu, Plus, ShieldPlus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type CreateTrainerInput, type TrainerResponse, createTrainer } from '@/api/trainers'
import { type CreateUserInput, createUser, deleteUser, resetUserPassword, searchUsers, updateUserDuties, updateUserMenu, updateUserStatus } from '@/api/users'
import { NAV_ITEMS } from '@/components/nav-items'
import { Checkbox } from '@/components/ui/checkbox'
import { useBranding } from '@/lib/use-branding'
import type { UserResponse } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const ROLES = ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT', 'PLACEMENT', 'FINANCE']
const STATUSES = ['ACTIVE', 'INACTIVE', 'BLOCKED']

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'default',
  INACTIVE: 'secondary',
  BLOCKED: 'destructive',
  DELETED: 'outline',
}

export function UsersPage() {
  const [role, setRole] = useState('')
  const [query, setQuery] = useState('')
  const queryClient = useQueryClient()
  const isAdmin = hasRole(useAuthStore((state) => state.roles), ['ADMIN'])

  const usersQuery = useQuery({
    queryKey: ['users', 'search', role, query],
    queryFn: () => searchUsers({ role: role || undefined, query: query || undefined }),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      updateUserStatus(id, status, 'Changed via admin console'),
    onSuccess: () => {
      toast.success('Status updated')
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change the status.')),
  })

  const resetMutation = useMutation({
    mutationFn: (id: number) => resetUserPassword(id),
    onSuccess: (message) => toast.success(message.message),
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not reset the password.')),
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Users</h1>
          <p className="text-muted-foreground">Every login account, and its role.</p>
        </div>
        {isAdmin && <NewUserDialog />}
      </div>

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search name, email, phone..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="max-w-xs"
        />
        <select
          value={role}
          onChange={(event) => setRole(event.target.value)}
          className="h-9 rounded-md border bg-transparent px-3 text-sm"
        >
          <option value="">All roles</option>
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
      </div>

      {usersQuery.isLoading && <Skeleton className="h-64" />}

      {usersQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              {isAdmin && <TableHead />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {usersQuery.data.content.map((user) => (
              <TableRow key={user.id}>
                <TableCell className="font-medium">{user.fullName}</TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>
                  {user.roleDisplayName}
                  {(user.extraRoles ?? []).map((duty) => (
                    <Badge key={duty} variant="secondary" className="ml-1.5">
                      + {DUTIES.find((d) => d.role === duty)?.label ?? duty}
                    </Badge>
                  ))}
                </TableCell>
                <TableCell>
                  {isAdmin ? (
                    <select
                      value={user.status}
                      onChange={(event) => statusMutation.mutate({ id: user.id, status: event.target.value })}
                      disabled={statusMutation.isPending}
                      className="h-8 rounded-md border bg-transparent px-2 text-sm"
                    >
                      {STATUSES.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <Badge variant={STATUS_VARIANT[user.status] ?? 'outline'}>{user.status}</Badge>
                  )}
                </TableCell>
                {isAdmin && (
                  <TableCell>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => resetMutation.mutate(user.id)}
                      disabled={resetMutation.isPending}
                    >
                      <KeyRound className="size-3.5" />
                      Reset password
                    </Button>
                    {user.role === 'STUDENT' && <StudentMenuDialog user={user} />}
                    {user.role !== 'STUDENT' && user.role !== 'ADMIN' && <DutiesDialog user={user} />}
                    <DeleteUserDialog user={user} />
                  </TableCell>
                )}
              </TableRow>
            ))}
            {usersQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={isAdmin ? 5 : 4} className="text-center text-muted-foreground">
                  No users match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

/** Soft delete behind a confirmation and a reason: the account is wiped but its history in other services stays. */
function DeleteUserDialog({ user }: { user: UserResponse }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => deleteUser(user.id, reason.trim()),
    onSuccess: (message) => {
      toast.success(message.message)
      setOpen(false)
      setReason('')
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the user.')),
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost" className="text-destructive">
          <Trash2 className="size-3.5" />
          Delete
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete {user.fullName}?</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-3 text-sm">
          <p>
            They can no longer sign in, and their name, e-mail and phone are wiped, so that e-mail and phone can be used by
            a new account. Fees, attendance and certificates stay in the records under &quot;Deleted user&quot;.
            <strong> This cannot be undone.</strong>
          </p>
          <div className="flex flex-col gap-2">
            <Label htmlFor={`delete-reason-${user.id}`}>Reason (kept in the audit log)</Label>
            <Input
              id={`delete-reason-${user.id}`}
              value={reason}
              maxLength={255}
              placeholder="e.g. Test account / left the institute"
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button variant="destructive" disabled={!reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? 'Deleting...' : 'Delete user'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function NewUserDialog() {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<CreateUserInput>({ firstName: '', lastName: '', email: '', role: 'TRAINER' })
  const queryClient = useQueryClient()

  const mutation = useMutation<UserResponse | TrainerResponse, unknown, void>({
    mutationFn: () => {
      if (form.role === 'TRAINER') {
        // Trainers are a profile in admission-service, not just an identity
        // account - /api/trainers creates the login and the profile together.
        // See the Batches page's "New batch" trainer picker, which can only
        // offer trainers created this way.
        return createTrainer({
          firstName: form.firstName,
          lastName: form.lastName,
          email: form.email,
          phone: form.phone ?? '',
        } satisfies CreateTrainerInput)
      }
      return createUser(form)
    },
    onSuccess: () => {
      toast.success('Account created. A temporary password has been emailed.')
      setOpen(false)
      setForm({ firstName: '', lastName: '', email: '', role: 'TRAINER' })
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      void queryClient.invalidateQueries({ queryKey: ['trainers'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the account.')),
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
          New user
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New user account</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="firstName">First name</Label>
              <Input
                id="firstName"
                value={form.firstName}
                onChange={(event) => setForm({ ...form, firstName: event.target.value })}
                required
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="lastName">Last name</Label>
              <Input
                id="lastName"
                value={form.lastName}
                onChange={(event) => setForm({ ...form, lastName: event.target.value })}
                required
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="phone">Phone{form.role === 'TRAINER' && ' (required for trainers)'}</Label>
            <Input
              id="phone"
              value={form.phone ?? ''}
              onChange={(event) => setForm({ ...form, phone: event.target.value })}
              required={form.role === 'TRAINER'}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="role">Role</Label>
            <select
              id="role"
              value={form.role}
              onChange={(event) => setForm({ ...form, role: event.target.value })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              {ROLES.filter((r) => r !== 'STUDENT').map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
            <p className="text-xs text-muted-foreground">
              Students come from admissions, not here - see the Admissions or Students page.
            </p>
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Creating...' : 'Create account'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** Gives one student a sidebar of their own, or sends them back to the institute's menu. */
function StudentMenuDialog({ user }: { user: UserResponse }) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const instituteHidden = useBranding().branding?.studentHiddenMenu ?? []
  const items = NAV_ITEMS.filter((item) => item.roles.includes('STUDENT') && item.to !== '/app')
  const [personal, setPersonal] = useState(user.hiddenMenu !== null)
  const [shown, setShown] = useState<Set<string>>(
    () => new Set(items.filter((i) => !(user.hiddenMenu ?? instituteHidden).includes(i.to)).map((i) => i.to)),
  )

  const mutation = useMutation({
    mutationFn: () => updateUserMenu(user.id, personal ? items.filter((i) => !shown.has(i.to)).map((i) => i.to) : null),
    onSuccess: () => {
      toast.success(personal ? `${user.fullName} now has a menu of their own.` : `${user.fullName} follows the institute's menu.`)
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      setOpen(false)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the menu.')),
  })

  function toggle(path: string, on: boolean) {
    setPersonal(true)
    setShown((current) => {
      const next = new Set(current)
      if (on) next.add(path)
      else next.delete(path)
      return next
    })
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <Menu className="size-3.5" />
          Menu
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Menu for {user.fullName}</DialogTitle>
        </DialogHeader>
        <label className="flex items-start gap-2 text-sm">
          <Checkbox checked={!personal} onCheckedChange={(v) => setPersonal(v !== true)} />
          <span>
            Follow the institute&apos;s student menu
            <span className="block text-xs text-muted-foreground">Set on the Branding page. Untick to give this student a menu of their own.</span>
          </span>
        </label>
        <div className={`grid gap-3 sm:grid-cols-2 ${personal ? '' : 'opacity-50'}`}>
          {items.map((item) => (
            <label key={item.to} className="flex items-center gap-2 text-sm">
              <Checkbox checked={shown.has(item.to)} onCheckedChange={(v) => toggle(item.to, v === true)} />
              <item.icon className="size-4 text-muted-foreground" />
              {item.label}
            </label>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** The duties an administrator can add to a main role. Admin and Student cannot be added; Trainer needs a trainer profile. */
const DUTIES: { role: string; label: string; what: string }[] = [
  { role: 'COORDINATOR', label: 'Coordinator', what: 'Admissions, students, batches, courses, course requests, users, announcements, reports and downloads.' },
  { role: 'FINANCE', label: 'Finance', what: 'Fees, payments, receipts and the finance desk.' },
  { role: 'PLACEMENT', label: 'Placement', what: 'Companies, job openings, applications and resumes.' },
]

function DutiesDialog({ user }: { user: UserResponse }) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const [chosen, setChosen] = useState<Set<string>>(() => new Set(user.extraRoles ?? []))
  const choices = DUTIES.filter((d) => d.role !== user.role)

  const mutation = useMutation({
    mutationFn: () => updateUserDuties(user.id, choices.filter((d) => chosen.has(d.role)).map((d) => d.role)),
    onSuccess: (saved) => {
      const extras = saved.extraRoles ?? []
      toast.success(
        extras.length > 0
          ? `${user.fullName} now also has: ${extras.map((r) => DUTIES.find((d) => d.role === r)?.label ?? r).join(', ')}.`
          : `${user.fullName} has no extra duties now.`,
      )
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      setOpen(false)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the duties.')),
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) setChosen(new Set(user.extraRoles ?? []))
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <ShieldPlus className="size-3.5" />
          Duties
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Duties of {user.fullName}</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          Main role: <b>{user.roleDisplayName}</b>. It stays as it is. What you tick below is added to it, and unticking
          takes it away again.
        </p>
        <div className="flex flex-col gap-3">
          {choices.map((duty) => (
            <label key={duty.role} className="flex items-start gap-2 text-sm">
              <Checkbox
                checked={chosen.has(duty.role)}
                onCheckedChange={(on) =>
                  setChosen((current) => {
                    const next = new Set(current)
                    if (on === true) next.add(duty.role)
                    else next.delete(duty.role)
                    return next
                  })
                }
              />
              <span>
                {duty.label}
                <span className="block text-xs text-muted-foreground">{duty.what}</span>
              </span>
            </label>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">Applies to {user.firstName}&apos;s very next click, a change either way. It is recorded in the audit log.</p>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
