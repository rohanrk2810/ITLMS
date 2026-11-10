import { type FormEvent, useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { login } from '@/api/auth'
import { SIGNED_OUT_NOTICE_KEY, apiErrorMessage } from '@/api/client'
import { SocialLogin } from '@/components/social-login'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useCaptcha } from '@/lib/use-captcha'

/** The /login page. */
export function LoginPage() {
  return <LoginForm />
}

/**
 * The sign-in form. On the /login page it stands alone; in the popup (components/auth-dialog.tsx) the caller says what
 * to do once signed in, how to switch to registering, and what to do when a link leaves for another page.
 */
export function LoginForm({
  onSignedIn,
  onCreateAccount,
  onNavigate,
}: {
  onSignedIn?: () => void
  onCreateAccount?: () => void
  onNavigate?: () => void
}) {
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const captcha = useCaptcha()

  useEffect(() => {
    try {
      const notice = sessionStorage.getItem(SIGNED_OUT_NOTICE_KEY)
      if (notice) {
        sessionStorage.removeItem(SIGNED_OUT_NOTICE_KEY)
        toast.warning(notice)
      }
    } catch {
      /* no storage, no notice */
    }
  }, [])

  function goToApp() {
    if (onSignedIn) {
      onSignedIn()
      return
    }
    // A temporary password sends every route to /app/change-password anyway
    // (see ProtectedRoute), so there is nothing special to do with that case here.
    const state = location.state as { from?: { pathname?: string } } | null
    void navigate(state?.from?.pathname ?? '/app', { replace: true })
  }

  const mutation = useMutation({
    mutationFn: () => login(identifier, password, captcha.token),
    onSuccess: goToApp,
    onError: (error) => {
      toast.error(apiErrorMessage(error, 'Could not sign in. Check your details and try again.'))
      captcha.reset() // a token works once
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sign in</CardTitle>
        <CardDescription>Use the email or phone number your account was registered with.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="identifier">Email or phone</Label>
            <Input
              id="identifier"
              autoComplete="username"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Password</Label>
              <Link to="/forgot-password" onClick={onNavigate} className="text-xs text-muted-foreground hover:underline">
                Forgot password?
              </Link>
            </div>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>
          {captcha.widget}
          <Button type="submit" className="mt-2" disabled={mutation.isPending || (captcha.enabled && !captcha.token)}>
            {mutation.isPending ? 'Signing in...' : 'Sign in'}
          </Button>
        </form>
        <SocialLogin
          captchaToken={captcha.token}
          captchaRequired={captcha.enabled}
          onCaptchaUsed={captcha.reset}
          onSignedIn={goToApp}
        />
        <p className="mt-4 text-center text-sm text-muted-foreground">
          New student?{' '}
          {onCreateAccount ? (
            <button type="button" onClick={onCreateAccount} className="font-medium text-foreground hover:underline">
              Create an account
            </button>
          ) : (
            <Link to="/register" className="font-medium text-foreground hover:underline">
              Create an account
            </Link>
          )}
        </p>
      </CardContent>
    </Card>
  )
}
