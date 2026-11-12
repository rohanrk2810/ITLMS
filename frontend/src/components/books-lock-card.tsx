import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Lock } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { getBooksLock, setBooksLock } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const yesterday = () => new Date(Date.now() - 86_400_000).toISOString().slice(0, 10)

/** Closing the books: nothing dated on or before the chosen day can be recorded, reversed or voided any more. */
export function BooksLockCard() {
  const queryClient = useQueryClient()
  const isAdmin = hasRole(useAuthStore((state) => state.roles), ['ADMIN'])
  const lock = useQuery({ queryKey: ['finance', 'books-lock'], queryFn: getBooksLock })
  const [date, setDate] = useState(yesterday())
  const [note, setNote] = useState('')

  const save = useMutation({
    mutationFn: (through: string | null) => setBooksLock(through, note.trim() || undefined),
    onSuccess: () => {
      toast.success('Saved')
      setNote('')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change the lock.')),
  })

  const through = lock.data?.lockedThrough
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Lock className="size-4" /> Closing the books
        </CardTitle>
        <CardDescription>
          {through
            ? `Closed up to ${formatDate(through)}. Nothing dated on or before it can be recorded, reversed or voided.`
            : 'Not closed. Close a month or year once it has been checked, so its figures cannot change.'}
        </CardDescription>
      </CardHeader>
      {isAdmin && (
        <CardContent className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="lock-date">Close through</Label>
            <Input id="lock-date" type="date" className="w-44" max={yesterday()} value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="lock-note">Note (needed to reopen)</Label>
            <Input id="lock-note" className="w-64" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <Button disabled={!date || save.isPending} onClick={() => save.mutate(date)}>Close the books</Button>
          {through && (
            <Button variant="outline" disabled={save.isPending || !note.trim()} onClick={() => save.mutate(null)}>
              Reopen everything
            </Button>
          )}
        </CardContent>
      )}
    </Card>
  )
}
