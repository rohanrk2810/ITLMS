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
import { useCaptcha } from '@/lib/use-captcha'

/** Doc S8.1, S11: public self-registration always creates a STUDENT account. Staff accounts come from POST /api/users. */
export function RegisterPage() {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '', password: '' })
  const [details, setDetails] = useState({
    dateOfBirth: '',
    gender: '',
    highestEducation: '',
    college: '',
    graduationYear: '',
    addressLine: '',
    city: '',
    state: '',
    pincode: '',
    guardianName: '',
    guardianPhone: '',
    emergencyContact: '',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  // Self-registration is off by default (itilms.security.self-registration-enabled).
  // Set once the backend actually refuses, rather than guessed in advance.
  const [closed, setClosed] = useState(false)
  const navigate = useNavigate()
  const captcha = useCaptcha()

  const mutation = useMutation({
    mutationFn: () =>
      register(
        {
          ...form,
          details: {
            ...details,
            college: details.college.trim() || undefined,
            graduationYear: details.graduationYear.trim() ? Number(details.graduationYear) : undefined,
            emergencyContact: details.emergencyContact.trim() || undefined,
          },
        },
        captcha.token,
      ),
    onSuccess: () => {
      toast.success('Account created. Add your photo for the ID card.')
      void navigate('/app/my-profile', { replace: true })
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
      captcha.reset() // a token works once
    },
  })

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setFieldErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function updateDetail(key: keyof typeof details, value: string) {
    setDetails((prev) => ({ ...prev, [key]: value }))
    setFieldErrors((prev) => ({ ...prev, [`details.${key}`]: '' }))
  }

  function detail(key: keyof typeof details, label: string, props: { type?: string; required?: boolean } = {}) {
    return (
      <div className="flex flex-col gap-2">
        <Label htmlFor={`d-${key}`}>{label}</Label>
        <Input
          id={`d-${key}`}
          type={props.type}
          required={props.required ?? true}
          value={details[key]}
          onChange={(event) => updateDetail(key, event.target.value)}
        />
        {fieldErrors[`details.${key}`] && <p className="text-xs text-destructive">{fieldErrors[`details.${key}`]}</p>}
      </div>
    )
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
          <div className="grid gap-4 sm:grid-cols-2">
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
          <div className="grid gap-4 sm:grid-cols-2">
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
          </div>
          <p className="mt-2 text-sm font-medium">Personal details</p>
          <div className="grid gap-4 sm:grid-cols-3">
            {detail('dateOfBirth', 'Date of birth', { type: 'date' })}
            <div className="flex flex-col gap-2">
              <Label htmlFor="d-gender">Gender</Label>
              <select
                id="d-gender"
                required
                value={details.gender}
                onChange={(event) => updateDetail('gender', event.target.value)}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                <option value="">Select</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
              {fieldErrors['details.gender'] && <p className="text-xs text-destructive">{fieldErrors['details.gender']}</p>}
            </div>
            {detail('highestEducation', 'Highest education')}
            {detail('college', 'College', { required: false })}
            {detail('graduationYear', 'Graduation year', { type: 'number', required: false })}
          </div>
          <p className="mt-2 text-sm font-medium">Address</p>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="sm:col-span-3">{detail('addressLine', 'Address')}</div>
            {detail('city', 'City')}
            {detail('state', 'State')}
            {detail('pincode', 'PIN code')}
          </div>
          <p className="mt-2 text-sm font-medium">Guardian</p>
          <div className="grid gap-4 sm:grid-cols-3">
            {detail('guardianName', 'Guardian name')}
            {detail('guardianPhone', 'Guardian phone')}
            {detail('emergencyContact', 'Emergency contact (optional)', { required: false })}
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
          {captcha.widget}
          <Button type="submit" className="mt-2" disabled={mutation.isPending || (captcha.enabled && !captcha.token)}>
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
