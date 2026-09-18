import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { forgotPassword } from '@/api/auth'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const mutation = useMutation({ mutationFn: () => forgotPassword(email) })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  if (mutation.isSuccess) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Check your email</CardTitle>
          <CardDescription>{mutation.data.message}</CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/login" className="text-sm font-medium hover:underline">
            Back to sign in
          </Link>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Forgot your password?</CardTitle>
        <CardDescription>We&apos;ll email you a link to reset it, if the address is registered.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>
          {mutation.isError && (
            <p className="text-sm text-destructive">{apiErrorMessage(mutation.error, 'Could not send the reset link.')}</p>
          )}
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Sending...' : 'Send reset link'}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-muted-foreground">
          <Link to="/login" className="font-medium text-foreground hover:underline">
            Back to sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
