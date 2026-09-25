import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Plus } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type CreateTrainerInput, type TrainerResponse, createTrainer } from '@/api/trainers'
import { type CreateUserInput, createUser, resetUserPassword, searchUsers, updateUserStatus } from '@/api/users'
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
}

export function UsersPage() {
  const [role, setRole] = useState('')
  const [query, setQuery] = useState('')
  const queryClient = useQueryClient()
  const isAdmin = hasRole(useAuthStore((state) => state.user?.role), ['ADMIN'])

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
                <TableCell>{user.roleDisplayName}</TableCell>
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
