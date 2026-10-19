import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'

import { forgotPassword } from '@/api/auth'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useCaptcha } from '@/lib/use-captcha'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const navigate = useNavigate()
  const captcha = useCaptcha()
  const mutation = useMutation({
    mutationFn: () => forgotPassword(email, captcha.token),
    onSuccess: () => void navigate(`/reset-password?email=${encodeURIComponent(email)}`),
    onError: () => captcha.reset(), // a token works once
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Forgot your password?</CardTitle>
        <CardDescription>We&apos;ll email you a 6-digit code, if the address is registered.</CardDescription>
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
            <p className="text-sm text-destructive">{apiErrorMessage(mutation.error, 'Could not send the code.')}</p>
          )}
          {captcha.widget}
          <Button type="submit" disabled={mutation.isPending || (captcha.enabled && !captcha.token)}>
            {mutation.isPending ? 'Sending...' : 'Send code'}
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
