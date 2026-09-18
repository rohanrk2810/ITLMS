import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { register } from '@/api/auth'
import { apiErrorMessage } from '@/api/client'
import type { ApiErrorResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** Doc S8.1, S11: public self-registration always creates a STUDENT account. Staff accounts come from POST /api/users. */
export function RegisterPage() {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  // Self-registration is off by default (itilms.security.self-registration-enabled).
  // Set once the backend actually refuses, rather than guessed in advance.
  const [closed, setClosed] = useState(false)
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: () => register(form),
    onSuccess: () => {
      toast.success('Account created')
      void navigate('/app', { replace: true })
    },
    onError: (error) => {
      if (axios.isAxiosError<ApiErrorResponse>(error)) {
        if (error.response?.status === 403) {
          setClosed(true)
          return
        }
        if (error.response?.data.fieldErrors) {
          setFieldErrors(error.response.data.fieldErrors)
        }
      }
      toast.error(apiErrorMessage(error, 'Could not create the account.'))
    },
  })

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setFieldErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  if (closed) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Online registration is not open</CardTitle>
          <CardDescription>
            Please contact the institute to enrol. Already have an account from admission?
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild className="w-full">
            <Link to="/login">Sign in instead</Link>
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create your student account</CardTitle>
        <CardDescription>Already admitted through the institute? Sign in instead.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="firstName">First name</Label>
              <Input
                id="firstName"
                value={form.firstName}
                onChange={(event) => update('firstName', event.target.value)}
                required
              />
              {fieldErrors.firstName && <p className="text-xs text-destructive">{fieldErrors.firstName}</p>}
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="lastName">Last name</Label>
              <Input
                id="lastName"
                value={form.lastName}
                onChange={(event) => update('lastName', event.target.value)}
                required
              />
              {fieldErrors.lastName && <p className="text-xs text-destructive">{fieldErrors.lastName}</p>}
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={(event) => update('email', event.target.value)}
              required
            />
            {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="phone">Phone</Label>
            <Input
              id="phone"
              autoComplete="tel"
              value={form.phone}
              onChange={(event) => update('phone', event.target.value)}
              required
            />
            {fieldErrors.phone && <p className="text-xs text-destructive">{fieldErrors.phone}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(event) => update('password', event.target.value)}
              required
            />
            {fieldErrors.password && <p className="text-xs text-destructive">{fieldErrors.password}</p>}
            <p className="text-xs text-muted-foreground">At least 8 characters, with upper case, lower case and a digit.</p>
          </div>
          <Button type="submit" className="mt-2" disabled={mutation.isPending}>
            {mutation.isPending ? 'Creating account...' : 'Create account'}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-foreground hover:underline">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
