import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CheckCircle2Icon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import type { CourseSummaryResponse } from '@/api/courses'
import { sendEnquiry } from '@/api/public-site'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { S as s } from './home-content'

/** The public enquiry form. It lands in the admission team's leads list. */
export function EnquiryForm({ courses }: { courses: CourseSummaryResponse[] }) {
  const [form, setForm] = useState({ name: '', phone: '', email: '', courseId: '', message: '', website: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [sent, setSent] = useState(false)

  const mutation = useMutation({
    mutationFn: () =>
      sendEnquiry({
        name: form.name.trim(),
        phone: form.phone.trim(),
        email: form.email.trim() || undefined,
        interestedCourseId: form.courseId ? Number(form.courseId) : undefined,
        message: form.message.trim() || undefined,
      }),
    onSuccess: () => setSent(true),
    onError: (error) => {
      const fieldErrors = (error as { response?: { data?: { fieldErrors?: Record<string, string> } } }).response?.data?.fieldErrors
      if (fieldErrors) setErrors(fieldErrors)
      toast.error(apiErrorMessage(error, s.formFailed))
    },
  })

  function set(key: keyof typeof form, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (form.website) return // a person never sees this field; a bot fills it
    mutation.mutate()
  }

  if (sent) {
    return (
      <div role="status" className="mx-auto flex max-w-md flex-col items-center gap-2 rounded-xl border bg-card p-8 text-center">
        <CheckCircle2Icon className="size-10 text-primary" aria-hidden="true" />
        <h3 className="text-lg font-semibold">{s.thanks}</h3>
        <p className="text-sm text-muted-foreground">{s.thanksText}</p>
      </div>
    )
  }

  return (
    <form onSubmit={submit} className="mx-auto flex max-w-xl flex-col gap-4 rounded-xl border bg-card p-6 shadow-sm">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-name">{s.formName}</Label>
          <Input id="enq-name" autoComplete="name" required value={form.name} onChange={(e) => set('name', e.target.value)} />
          {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-phone">{s.formPhone}</Label>
          <Input id="enq-phone" type="tel" autoComplete="tel" required value={form.phone} onChange={(e) => set('phone', e.target.value)} />
          {errors.phone && <p className="text-xs text-destructive">{errors.phone}</p>}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-email">{s.formEmail}</Label>
          <Input id="enq-email" type="email" autoComplete="email" value={form.email} onChange={(e) => set('email', e.target.value)} />
          {errors.email && <p className="text-xs text-destructive">{errors.email}</p>}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-course">{s.formCourse}</Label>
          <select
            id="enq-course"
            value={form.courseId}
            onChange={(e) => set('courseId', e.target.value)}
            className="border-input h-9 rounded-md border bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          >
            <option value="">{s.formNotSure}</option>
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="enq-message">{s.formMessage}</Label>
        <textarea
          id="enq-message"
          rows={3}
          maxLength={1000}
          value={form.message}
          onChange={(e) => set('message', e.target.value)}
          className="border-input rounded-md border bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
        />
        {errors.message && <p className="text-xs text-destructive">{errors.message}</p>}
      </div>
      {/* Honeypot: hidden from people and screen readers. */}
      <div className="absolute -left-[9999px]" aria-hidden="true">
        <label>
          Website
          <input tabIndex={-1} autoComplete="off" value={form.website} onChange={(e) => set('website', e.target.value)} />
        </label>
      </div>
      <Button type="submit" size="lg" disabled={mutation.isPending}>
        {mutation.isPending ? s.formSending : s.formSend}
      </Button>
      <p className="text-center text-xs text-muted-foreground">
        {s.formAgree}{' '}
        <Link to="/privacy" className="underline">
          {s.privacy}
        </Link>
        .
      </p>
    </form>
  )
}
