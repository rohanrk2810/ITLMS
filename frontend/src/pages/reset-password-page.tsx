import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { resetPassword } from '@/api/auth'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [newPassword, setNewPassword] = useState('')
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: () => resetPassword(token, newPassword),
    onSuccess: () => {
      setTimeout(() => void navigate('/login', { replace: true }), 1500)
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  if (!token) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Reset link invalid</CardTitle>
          <CardDescription>This link is missing its token. Request a new one.</CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/forgot-password" className="text-sm font-medium hover:underline">
            Request a new link
          </Link>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Set a new password</CardTitle>
        <CardDescription>You&apos;ll be signed out of every other device.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
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
          </div>
          {mutation.isError && (
            <p className="text-sm text-destructive">{apiErrorMessage(mutation.error, 'Could not reset the password.')}</p>
          )}
          {mutation.isSuccess && (
            <p className="text-sm text-emerald-600">{mutation.data.message} Redirecting to sign in...</p>
          )}
          <Button type="submit" disabled={mutation.isPending || mutation.isSuccess}>
            {mutation.isPending ? 'Saving...' : 'Set new password'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
