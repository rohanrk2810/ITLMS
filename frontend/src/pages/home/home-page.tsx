import { type FormEvent, type ReactNode, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  ArrowRightIcon,
  CheckCircle2Icon,
  ClockIcon,
  MailIcon,
  MapPinIcon,
  MenuIcon,
  PhoneIcon,
  XIcon,
} from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { assetUrl } from '@/api/branding'
import type { CourseSummaryResponse } from '@/api/courses'
import { getPublicCourses, sendEnquiry } from '@/api/public-site'
import { BrandMark } from '@/components/brand-mark'
import { LegalLinks } from '@/components/legal-links'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { useBranding } from '@/lib/use-branding'
import { useNormalTextSize } from '@/lib/use-normal-text-size'
import { useReveal } from '@/lib/use-reveal'
import { useAuthStore } from '@/stores/auth-store'

import { ABOUT_POINTS, DEFAULT_TAGLINE, FEATURES, NAV, STEPS } from './home-content'

/** The public front page. A signed-in visitor goes straight to the app, as before. */
export function HomePage() {
  const status = useAuthStore((state) => state.status)
  useNormalTextSize()
  if (status === 'checking') return null
  if (status === 'authenticated') return <Navigate to="/app" replace />
  return <Website />
}

function Section({ id, title, intro, children, tone }: { id: string; title: string; intro?: string; children: ReactNode; tone?: 'muted' }) {
  const ref = useReveal<HTMLElement>()
  return (
    <section id={id} ref={ref} className={`reveal scroll-mt-16 px-4 py-16 sm:py-20 ${tone === 'muted' ? 'bg-muted/40' : ''}`}>
      <div className="mx-auto max-w-6xl">
        <div className="mx-auto mb-10 max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h2>
          {intro && <p className="mt-3 text-muted-foreground">{intro}</p>}
        </div>
        {children}
      </div>
    </section>
  )
}

function Website() {
  const { branding, name } = useBranding()
  const [menuOpen, setMenuOpen] = useState(false)
  const courses = useQuery({ queryKey: ['public-courses'], queryFn: getPublicCourses, staleTime: 5 * 60_000 })
  const list = courses.data?.content ?? []
  const tagline = branding?.tagline || DEFAULT_TAGLINE
  const hero = assetUrl(branding?.loginBackgroundUrl ?? null)
  const stack = Array.from(new Set(list.flatMap((c) => (c.technologyStack ?? '').split(/[,/|]/).map((t) => t.trim()).filter(Boolean)))).slice(0, 14)

  return (
    <div className="min-h-svh bg-background text-foreground">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-background focus:p-2">
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4">
          <a href="#top" className="flex items-center gap-2 font-semibold" aria-label={`${name} home`}>
            <BrandMark className="size-7" />
          </a>
          <nav aria-label="Main" className="ml-6 hidden items-center gap-5 text-sm md:flex">
            {NAV.map((item) => (
              <a key={item.href} href={item.href} className="text-muted-foreground transition-colors hover:text-foreground">
                {item.label}
              </a>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <ThemeToggle />
            <Button asChild size="sm" variant="outline" className="hidden sm:inline-flex">
              <Link to="/register">Register</Link>
            </Button>
            <Button asChild size="sm">
              <Link to="/login">Login</Link>
            </Button>
            <Button
              size="icon"
              variant="ghost"
              className="md:hidden"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? <XIcon /> : <MenuIcon />}
            </Button>
          </div>
        </div>
        {menuOpen && (
          <nav aria-label="Mobile" className="flex flex-col gap-1 border-t px-4 py-2 md:hidden">
            {NAV.map((item) => (
              <a key={item.href} href={item.href} onClick={() => setMenuOpen(false)} className="rounded px-2 py-2 text-sm hover:bg-muted">
                {item.label}
              </a>
            ))}
            <Link to="/register" className="rounded px-2 py-2 text-sm hover:bg-muted">
              Register
            </Link>
          </nav>
        )}
      </header>

      <main id="main">
        {/* Hero */}
        <section id="top" className="relative isolate overflow-hidden px-4 pb-20 pt-16 sm:pt-24">
          {hero && <img src={hero} alt="" className="absolute inset-0 -z-20 size-full object-cover opacity-20" />}
          <div className="blob absolute -left-24 top-0 -z-10 size-96 rounded-full bg-primary/25 blur-3xl" aria-hidden="true" />
          <div className="blob blob-slow absolute -right-24 top-32 -z-10 size-96 rounded-full bg-accent blur-3xl" aria-hidden="true" />
          <div className="mx-auto flex max-w-4xl flex-col items-center gap-6 text-center">
            <span className="rounded-full border bg-background/70 px-3 py-1 text-xs font-medium text-muted-foreground backdrop-blur">
              Admissions open
            </span>
            <h1 className="text-4xl font-bold tracking-tight sm:text-6xl">
              {name}
              <span className="mt-2 block bg-gradient-to-r from-primary to-foreground bg-clip-text pb-2 leading-[1.2] text-transparent">{tagline}</span>
            </h1>
            <p className="max-w-2xl text-lg text-muted-foreground">
              Live classes, hands-on projects, regular tests and a certificate you can prove, all in one place.
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <Button asChild size="lg">
                <a href="#enquiry">
                  Get free counselling <ArrowRightIcon />
                </a>
              </Button>
              <Button asChild size="lg" variant="outline">
                <a href="#courses">Explore courses</a>
              </Button>
            </div>
            <dl className="mt-6 grid w-full max-w-3xl grid-cols-2 gap-3 sm:grid-cols-4">
              {[
                [courses.data ? String(courses.data.totalElements) : '–', 'Courses'],
                ['Live', 'Classes'],
                ['QR', 'Verified certificates'],
                ['24x7', 'Notes and recordings'],
              ].map(([value, label]) => (
                <div key={label} className="rounded-xl border bg-background/70 p-4 backdrop-blur">
                  <dt className="sr-only">{label}</dt>
                  <dd className="text-2xl font-bold text-primary">{value}</dd>
                  <dd className="text-xs text-muted-foreground">{label}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        {stack.length > 0 && (
          <div className="overflow-hidden border-y bg-muted/40 py-3" aria-label="Technologies we teach">
            <div className="marquee flex w-max gap-8 whitespace-nowrap text-sm font-medium text-muted-foreground">
              {[...stack, ...stack].map((tech, i) => (
                <span key={`${tech}-${i}`}>{tech}</span>
              ))}
            </div>
          </div>
        )}

        <Section id="about" title={`About ${name}`} intro="A place to learn IT skills properly and get ready for work.">
          <div className="mx-auto grid max-w-4xl gap-4 sm:grid-cols-3">
            {ABOUT_POINTS.map((point) => (
              <div key={point} className="flex gap-3 rounded-xl border bg-card p-5">
                <CheckCircle2Icon className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" />
                <p className="text-sm">{point}</p>
              </div>
            ))}
          </div>
        </Section>

        <Section id="courses" title="Our courses" intro="Pick a course, see what you will learn, and ask us anything." tone="muted">
          {courses.isLoading ? (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {[0, 1, 2].map((n) => (
                <Skeleton key={n} className="h-48 rounded-xl" />
              ))}
            </div>
          ) : courses.isError ? (
            <p className="text-center text-sm text-muted-foreground">Courses could not be loaded right now. Please try again in a moment.</p>
          ) : list.length === 0 ? (
            <p className="text-center text-sm text-muted-foreground">New courses are being added. Ask us what is starting next.</p>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {list.map((course) => (
                <CourseCard key={course.id} course={course} />
              ))}
            </div>
          )}
        </Section>

        <Section id="why" title="Why learn with us">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <div key={title} className="group rounded-xl border bg-card p-6 transition-all hover:-translate-y-1 hover:shadow-lg">
                <span className="mb-4 inline-flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <h3 className="font-semibold">{title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{text}</p>
              </div>
            ))}
          </div>
        </Section>

        <Section id="how" title="How it works" tone="muted">
          <ol className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((step, index) => (
              <li key={step.title} className="relative rounded-xl border bg-card p-6">
                <span className="mb-3 inline-flex size-9 items-center justify-center rounded-full bg-primary font-bold text-primary-foreground">
                  {index + 1}
                </span>
                <h3 className="font-semibold">{step.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{step.text}</p>
              </li>
            ))}
          </ol>
        </Section>

        <Section id="enquiry" title="Talk to a counsellor" intro="Leave your number and we will call you back. It is free.">
          <EnquiryForm courses={list} />
        </Section>

        <Section id="contact" title="Contact us" tone="muted">
          <div className="mx-auto grid max-w-4xl gap-4 sm:grid-cols-2">
            {branding?.address && <ContactItem icon={<MapPinIcon />} label="Visit us" value={branding.address} />}
            {branding?.contactPhone && (
              <ContactItem icon={<PhoneIcon />} label="Call us" value={branding.contactPhone} href={`tel:${branding.contactPhone}`} />
            )}
            {branding?.contactEmail && (
              <ContactItem icon={<MailIcon />} label="Email us" value={branding.contactEmail} href={`mailto:${branding.contactEmail}`} />
            )}
            <ContactItem icon={<ClockIcon />} label="Already a student?" value="Login to your classes, tests and certificates" href="/login" />
          </div>
        </Section>
      </main>

      <footer className="border-t px-4 py-8">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-3 text-center">
          <p className="text-sm font-medium">{name}</p>
          <LegalLinks />
          <p className="text-xs text-muted-foreground">
            &copy; {new Date().getFullYear()} {name}. All rights reserved.
          </p>
        </div>
      </footer>
    </div>
  )
}

function CourseCard({ course }: { course: CourseSummaryResponse }) {
  const tags = (course.technologyStack ?? '').split(/[,/|]/).map((t) => t.trim()).filter(Boolean).slice(0, 4)
  return (
    <article className="flex flex-col overflow-hidden rounded-xl border bg-card transition-all hover:-translate-y-1 hover:shadow-lg">
      <div className="bg-gradient-to-br from-primary/80 to-primary/30 p-5 text-primary-foreground">
        <span className="rounded-full bg-background/25 px-2 py-0.5 text-xs font-medium capitalize">{course.level.toLowerCase()}</span>
        <h3 className="mt-3 text-lg font-semibold leading-snug">{course.title}</h3>
      </div>
      <div className="flex flex-1 flex-col gap-3 p-5">
        <p className="line-clamp-3 text-sm text-muted-foreground">{course.summary ?? 'Ask us for the full syllabus.'}</p>
        {tags.length > 0 && (
          <ul className="flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <li key={tag} className="rounded-md bg-muted px-2 py-0.5 text-xs">
                {tag}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-auto flex items-center justify-between pt-2 text-sm">
          <span className="text-muted-foreground">{course.durationHours ? `${course.durationHours} hours` : ''}</span>
          <a href={`#enquiry`} className="inline-flex items-center gap-1 font-medium text-primary hover:underline">
            Ask about this <ArrowRightIcon className="size-4" />
          </a>
        </div>
      </div>
    </article>
  )
}

function ContactItem({ icon, label, value, href }: { icon: ReactNode; label: string; value: string; href?: string }) {
  const body = (
    <>
      <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary [&_svg]:size-5">{icon}</span>
      <span>
        <span className="block text-xs text-muted-foreground">{label}</span>
        <span className="block text-sm font-medium">{value}</span>
      </span>
    </>
  )
  const cls = 'flex items-center gap-3 rounded-xl border bg-card p-4'
  if (!href) return <div className={cls}>{body}</div>
  return href.startsWith('/') ? (
    <Link to={href} className={`${cls} hover:shadow-md`}>
      {body}
    </Link>
  ) : (
    <a href={href} className={`${cls} hover:shadow-md`}>
      {body}
    </a>
  )
}

function EnquiryForm({ courses }: { courses: CourseSummaryResponse[] }) {
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
      toast.error(apiErrorMessage(error, 'Could not send your enquiry. Please try again.'))
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
        <h3 className="text-lg font-semibold">Thank you!</h3>
        <p className="text-sm text-muted-foreground">We have your details and will call you back soon.</p>
      </div>
    )
  }

  return (
    <form onSubmit={submit} className="mx-auto flex max-w-xl flex-col gap-4 rounded-xl border bg-card p-6 shadow-sm">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-name">Your name</Label>
          <Input id="enq-name" autoComplete="name" required value={form.name} onChange={(e) => set('name', e.target.value)} />
          {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-phone">Phone number</Label>
          <Input id="enq-phone" type="tel" autoComplete="tel" required value={form.phone} onChange={(e) => set('phone', e.target.value)} />
          {errors.phone && <p className="text-xs text-destructive">{errors.phone}</p>}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-email">Email (optional)</Label>
          <Input id="enq-email" type="email" autoComplete="email" value={form.email} onChange={(e) => set('email', e.target.value)} />
          {errors.email && <p className="text-xs text-destructive">{errors.email}</p>}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="enq-course">Course you are interested in</Label>
          <select
            id="enq-course"
            value={form.courseId}
            onChange={(e) => set('courseId', e.target.value)}
            className="border-input h-9 rounded-md border bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          >
            <option value="">Not sure yet</option>
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="enq-message">Message (optional)</Label>
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
        {mutation.isPending ? 'Sending...' : 'Request a call back'}
      </Button>
      <p className="text-center text-xs text-muted-foreground">
        By sending this you agree to our{' '}
        <Link to="/privacy" className="underline">
          Privacy Policy
        </Link>
        .
      </p>
    </form>
  )
}
