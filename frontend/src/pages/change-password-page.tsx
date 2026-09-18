import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { changePassword } from '@/api/auth'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuthStore } from '@/stores/auth-store'

/**
 * A temporary password (set by an admin, or after a reset) must be changed
 * before anything else is usable - see the note this page's route is named
 * after: `ProtectedRoute` redirects every other page here until it is. The
 * backend does not enforce this itself, so the guard has to live here.
 */
export function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const user = useAuthStore((state) => state.user)
  const setUser = useAuthStore((state) => state.setUser)
  const navigate = useNavigate()
  const location = useLocation()

  const forced = user?.mustChangePassword ?? false

  const mutation = useMutation({
    mutationFn: () => changePassword(currentPassword, newPassword),
    onSuccess: () => {
      if (user) {
        setUser({ ...user, mustChangePassword: false })
      }
      toast.success('Password changed')
      const state = location.state as { from?: { pathname?: string } } | null
      void navigate(state?.from?.pathname && !forced ? state.from.pathname : '/app', { replace: true })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change the password.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (newPassword !== confirmPassword) {
      toast.error('The new password and its confirmation do not match.')
      return
    }
    mutation.mutate()
  }

  return (
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle>{forced ? 'Set a new password' : 'Change your password'}</CardTitle>
        <CardDescription>
          {forced
            ? 'Your account was given a temporary password. Choose one only you know before continuing.'
            : 'You will be signed out of every other device.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="currentPassword">Current password</Label>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="newPassword">New password</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
            />
            <p className="text-xs text-muted-foreground">At least 8 characters, with upper case, lower case and a digit.</p>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="confirmPassword">Confirm new password</Label>
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
            />
          </div>
          <Button type="submit" className="mt-2" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving...' : 'Change password'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
