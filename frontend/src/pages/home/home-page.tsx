import { type ReactNode, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  ArrowRightIcon,
  CheckCircle2Icon,
  ClockIcon,
  MailIcon,
  MapPinIcon,
  MenuIcon,
  MessageCircleIcon,
  PhoneIcon,
  QuoteIcon,
  XIcon,
} from 'lucide-react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'

import { assetUrl } from '@/api/branding'
import type { CourseSummaryResponse } from '@/api/courses'
import { getPublicCourses } from '@/api/public-site'
import { BrandMark } from '@/components/brand-mark'
import { LegalLinks } from '@/components/legal-links'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { type SiteConfig, isMapEmbed, phoneDigits, pick, siteImageUrl, useSite } from '@/lib/site-config'
import { useBranding } from '@/lib/use-branding'
import { useNormalTextSize } from '@/lib/use-normal-text-size'
import { useReveal } from '@/lib/use-reveal'
import { useAuthStore } from '@/stores/auth-store'

import {
  DEFAULT_ABOUT_INTRO,
  DEFAULT_ABOUT_POINTS,
  DEFAULT_FEATURES,
  DEFAULT_HERO_BADGE,
  DEFAULT_HERO_TEXT,
  DEFAULT_HERO_TITLE,
  DEFAULT_STEPS,
  DEFAULT_WHATSAPP_MESSAGE,
  FEATURE_ICONS,
  NAV_IDS,
  type Strings,
  S,
} from './home-content'
import { EnquiryForm } from './enquiry-form'

/** The public front page. A signed-in visitor goes straight to the app, as before. */
export function HomePage() {
  const status = useAuthStore((state) => state.status)
  // An administrator checking their edits opens /?preview=1 and stays on the page.
  const preview = useSearchParams()[0].has('preview')
  useNormalTextSize()
  if (status === 'checking') return null
  if (status === 'authenticated' && !preview) return <Navigate to="/app" replace />
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
  const site = useSite()
  const s = S
  const [menuOpen, setMenuOpen] = useState(false)
  const courses = useQuery({ queryKey: ['public-courses'], queryFn: getPublicCourses, staleTime: 5 * 60_000 })
  const list = courses.data?.content ?? []

  const show = (id: string) => !site.hidden.includes(id)
  const has: Record<string, boolean> = {
    about: true,
    courses: true,
    why: true,
    how: true,
    gallery: site.gallery.length > 0,
    faculty: site.faculty.length > 0,
    testimonials: site.testimonials.length > 0,
    faq: site.faqs.length > 0,
    contact: true,
  }
  const nav = NAV_IDS.filter((id) => show(id) && has[id])

  const tagline = pick(site.heroTitle, branding?.tagline || pick(DEFAULT_HERO_TITLE))
  const features = (site.features ?? DEFAULT_FEATURES).map((f, i) => ({ Icon: FEATURE_ICONS[i % FEATURE_ICONS.length], title: pick(f.title), text: pick(f.text) }))
  const steps = site.steps ?? DEFAULT_STEPS
  const aboutPoints = site.aboutPoints ?? DEFAULT_ABOUT_POINTS
  const hero = site.heroImage ? siteImageUrl(site.heroImage) : assetUrl(branding?.loginBackgroundUrl ?? null)
  const stack = Array.from(new Set(list.flatMap((c) => (c.technologyStack ?? '').split(/[,/|]/).map((x) => x.trim()).filter(Boolean)))).slice(0, 14)
  const stats = (
    site.stats ?? [
      { value: courses.data ? String(courses.data.totalElements) : '–', label: s.statCourses },
      { value: 'Live', label: s.statClasses },
      { value: 'QR', label: s.statCertificates },
      { value: '24x7', label: s.statNotes },
    ]
  ).map((x) => ({ value: x.value, label: pick(x.label) }))

  const whatsappDigits = phoneDigits(site.social.whatsapp)
  const whatsappLink = whatsappDigits
    ? `https://wa.me/${whatsappDigits}?text=${encodeURIComponent(pick(site.whatsappMessage, pick(DEFAULT_WHATSAPP_MESSAGE)))}`
    : ''
  const socials = [
    { label: 'WhatsApp', href: whatsappLink },
    { label: 'Instagram', href: site.social.instagram },
    { label: 'YouTube', href: site.social.youtube },
    { label: 'Facebook', href: site.social.facebook },
    { label: 'LinkedIn', href: site.social.linkedin },
  ].filter((x) => x.href)
  const announcement = site.announcement.enabled ? pick(site.announcement.text) : ''

  return (
    <div className="min-h-svh bg-background text-foreground">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-background focus:p-2">
        {s.skip}
      </a>

      {announcement && <AnnouncementBar text={announcement} link={site.announcement.link} />}

      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4">
          <a href="#top" className="flex items-center gap-2 font-semibold" aria-label={`${name} home`}>
            <BrandMark className="size-7" />
          </a>
          <nav aria-label="Main" className="ml-6 hidden items-center gap-5 text-sm lg:flex">
            {nav.map((id) => (
              <a key={id} href={`#${id}`} className="text-muted-foreground transition-colors hover:text-foreground">
                {s.nav[id]}
              </a>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <ThemeToggle />
            <Button asChild size="sm" variant="outline" className="hidden sm:inline-flex">
              <Link to="/register">{s.register}</Link>
            </Button>
            <Button asChild size="sm">
              <Link to="/login">{s.login}</Link>
            </Button>
            <Button
              size="icon"
              variant="ghost"
              className="lg:hidden"
              aria-label={menuOpen ? s.closeMenu : s.openMenu}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? <XIcon /> : <MenuIcon />}
            </Button>
          </div>
        </div>
        {menuOpen && (
          <nav aria-label="Mobile" className="flex flex-col gap-1 border-t px-4 py-2 lg:hidden">
            {nav.map((id) => (
              <a key={id} href={`#${id}`} onClick={() => setMenuOpen(false)} className="rounded px-2 py-2 text-sm hover:bg-muted">
                {s.nav[id]}
              </a>
            ))}
            <Link to="/register" className="rounded px-2 py-2 text-sm hover:bg-muted">
              {s.register}
            </Link>
          </nav>
        )}
      </header>

      <main id="main">
        <Hero name={name} tagline={tagline} s={s} site={site} image={hero} separate={!!site.heroImage} stats={stats} />

        {stack.length > 0 && (
          <div className="overflow-hidden border-y bg-muted/40 py-3" aria-label="Technologies we teach">
            <div className="marquee flex w-max gap-8 whitespace-nowrap text-sm font-medium text-muted-foreground">
              {[...stack, ...stack].map((tech, i) => (
                <span key={`${tech}-${i}`}>{tech}</span>
              ))}
            </div>
          </div>
        )}

        {show('about') && (
          <Section id="about" title={`${s.aboutTitle} ${name}`} intro={pick(site.aboutIntro, pick(DEFAULT_ABOUT_INTRO))}>
            <div className="mx-auto grid max-w-4xl gap-4 sm:grid-cols-3">
              {aboutPoints.map((point, i) => (
                <div key={i} className="flex gap-3 rounded-xl border bg-card p-5">
                  <CheckCircle2Icon className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" />
                  <p className="text-sm">{pick(point)}</p>
                </div>
              ))}
            </div>
          </Section>
        )}

        {show('courses') && (
          <Section id="courses" title={s.coursesTitle} intro={s.coursesIntro} tone="muted">
            {courses.isLoading ? (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {[0, 1, 2].map((n) => (
                  <Skeleton key={n} className="h-48 rounded-xl" />
                ))}
              </div>
            ) : courses.isError ? (
              <p className="text-center text-sm text-muted-foreground">{s.coursesFailed}</p>
            ) : list.length === 0 ? (
              <p className="text-center text-sm text-muted-foreground">{s.coursesNone}</p>
            ) : (
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {list.map((course) => (
                  <CourseCard key={course.id} course={course} site={site} s={s} />
                ))}
              </div>
            )}
          </Section>
        )}

        {show('why') && (
          <Section id="why" title={s.whyTitle}>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {features.map(({ Icon, title, text }, i) => (
                <div key={i} className="group rounded-xl border bg-card p-6 transition-all hover:-translate-y-1 hover:shadow-lg">
                  <span className="mb-4 inline-flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                    <Icon className="size-5" aria-hidden="true" />
                  </span>
                  <h3 className="font-semibold">{title}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">{text}</p>
                </div>
              ))}
            </div>
          </Section>
        )}

        {show('how') && (
          <Section id="how" title={s.howTitle} tone="muted">
            <ol className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {steps.map((step, index) => (
                <li key={index} className="relative rounded-xl border bg-card p-6">
                  <span className="mb-3 inline-flex size-9 items-center justify-center rounded-full bg-primary font-bold text-primary-foreground">
                    {index + 1}
                  </span>
                  <h3 className="font-semibold">{pick(step.title)}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">{pick(step.text)}</p>
                </li>
              ))}
            </ol>
          </Section>
        )}

        {show('gallery') && has.gallery && (
          <Section id="gallery" title={s.galleryTitle}>
            <Gallery site={site} closeLabel={s.close} />
          </Section>
        )}

        {show('faculty') && has.faculty && (
          <Section id="faculty" title={s.facultyTitle} tone="muted">
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {site.faculty.map((person, i) => (
                <figure key={i} className="flex flex-col items-center gap-3 rounded-xl border bg-card p-6 text-center">
                  {person.image ? (
                    <img src={siteImageUrl(person.image)} alt={person.name} loading="lazy" className="size-28 rounded-full object-cover" />
                  ) : (
                    <span className="flex size-28 items-center justify-center rounded-full bg-primary/10 text-3xl font-semibold text-primary" aria-hidden="true">
                      {person.name.slice(0, 1).toUpperCase()}
                    </span>
                  )}
                  <figcaption>
                    <span className="block font-semibold">{person.name}</span>
                    <span className="block text-sm text-muted-foreground">{pick(person.role)}</span>
                  </figcaption>
                </figure>
              ))}
            </div>
          </Section>
        )}

        {show('partners') && site.partners.length > 0 && (
          <Section id="partners" title={s.partnersTitle}>
            <ul className="mx-auto flex max-w-5xl flex-wrap items-center justify-center gap-x-10 gap-y-6">
              {site.partners.map((partner, i) => (
                <li key={i} className="flex h-14 items-center">
                  {partner.image ? (
                    <img src={siteImageUrl(partner.image)} alt={partner.name} loading="lazy" className="max-h-12 max-w-36 object-contain opacity-80 grayscale transition hover:opacity-100 hover:grayscale-0" />
                  ) : (
                    <span className="text-lg font-semibold text-muted-foreground">{partner.name}</span>
                  )}
                </li>
              ))}
            </ul>
          </Section>
        )}

        {show('testimonials') && has.testimonials && (
          <Section id="testimonials" title={s.testimonialsTitle} tone="muted">
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {site.testimonials.map((t, i) => (
                <figure key={i} className="flex flex-col gap-3 rounded-xl border bg-card p-6">
                  <QuoteIcon className="size-5 text-primary" aria-hidden="true" />
                  <blockquote className="text-sm leading-relaxed">{pick(t.text)}</blockquote>
                  <figcaption className="mt-auto flex items-center gap-3 text-sm">
                    {t.image && <img src={siteImageUrl(t.image)} alt="" loading="lazy" className="size-10 rounded-full object-cover" />}
                    <span>
                      <span className="block font-semibold">{t.name}</span>
                      {pick(t.role) && <span className="block text-xs text-muted-foreground">{pick(t.role)}</span>}
                    </span>
                  </figcaption>
                </figure>
              ))}
            </div>
          </Section>
        )}

        {show('faq') && has.faq && (
          <Section id="faq" title={s.faqTitle}>
            <div className="mx-auto flex max-w-3xl flex-col gap-3">
              {site.faqs.map((item, i) => (
                <details key={i} className="group rounded-xl border bg-card p-4 open:shadow-sm">
                  <summary className="cursor-pointer list-none font-medium marker:hidden [&::-webkit-details-marker]:hidden">
                    <span className="flex items-center justify-between gap-3">
                      {pick(item.q)}
                      <span className="text-primary transition-transform group-open:rotate-45" aria-hidden="true">
                        +
                      </span>
                    </span>
                  </summary>
                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{pick(item.a)}</p>
                </details>
              ))}
            </div>
          </Section>
        )}

        {show('enquiry') && (
          <Section id="enquiry" title={s.enquiryTitle} intro={s.enquiryIntro} tone="muted">
            <EnquiryForm courses={list} />
          </Section>
        )}

        {show('contact') && (
          <Section id="contact" title={s.contactTitle}>
            <div className="mx-auto grid max-w-5xl gap-6 lg:grid-cols-2">
              <div className="flex flex-col gap-4">
                {branding?.address && <ContactItem icon={<MapPinIcon />} label={s.visit} value={branding.address} />}
                {branding?.contactPhone && <ContactItem icon={<PhoneIcon />} label={s.call} value={branding.contactPhone} href={`tel:${branding.contactPhone}`} />}
                {branding?.contactEmail && <ContactItem icon={<MailIcon />} label={s.email} value={branding.contactEmail} href={`mailto:${branding.contactEmail}`} />}
                <ContactItem icon={<ClockIcon />} label={s.student} value={s.studentText} href="/login" />
              </div>
              {site.mapUrl && <MapBlock url={site.mapUrl} address={branding?.address ?? ''} label={s.openMap} />}
            </div>
          </Section>
        )}
      </main>

      <footer className="border-t px-4 py-8">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-3 text-center">
          <p className="text-sm font-medium">{name}</p>
          {socials.length > 0 && (
            <ul aria-label={s.follow} className="flex flex-wrap justify-center gap-2">
              {socials.map((social) => (
                <li key={social.label}>
                  <a href={social.href} target="_blank" rel="noreferrer noopener" className="rounded-full border px-3 py-1 text-xs hover:bg-muted">
                    {social.label}
                  </a>
                </li>
              ))}
            </ul>
          )}
          <LegalLinks />
          <p className="text-xs text-muted-foreground">
            &copy; {new Date().getFullYear()} {name}. {s.rights}
          </p>
        </div>
      </footer>

      {site.whatsappButton && whatsappLink && (
        <a
          href={whatsappLink}
          target="_blank"
          rel="noreferrer noopener"
          aria-label={s.whatsapp}
          className="fixed bottom-16 right-4 z-40 flex size-14 items-center justify-center rounded-full bg-[#25D366] text-white shadow-lg transition-transform hover:scale-105"
        >
          <MessageCircleIcon className="size-7" />
        </a>
      )}
    </div>
  )
}

/** The embed address is shown as it is; a share link cannot be embedded, so the map is found from the institute's address. */
function MapBlock({ url, address, label }: { url: string; address: string; label: string }) {
  const embed = isMapEmbed(url) ? url : address ? `https://maps.google.com/maps?q=${encodeURIComponent(address)}&output=embed` : ''
  return (
    <div className="flex flex-col gap-2">
      {embed && (
        <iframe src={embed} title="Map" loading="lazy" referrerPolicy="no-referrer-when-downgrade" className="h-72 w-full rounded-xl border lg:h-full lg:min-h-72" />
      )}
      {!isMapEmbed(url) && (
        <Button asChild variant="outline">
          <a href={url} target="_blank" rel="noreferrer noopener">
            <MapPinIcon /> {label}
          </a>
        </Button>
      )}
    </div>
  )
}

function AnnouncementBar({ text, link }: { text: string; link: string }) {
  const body = <span className="text-sm font-medium">{text}</span>
  const cls = 'block bg-primary px-4 py-2 text-center text-primary-foreground'
  if (!link) return <div className={cls}>{body}</div>
  return link.startsWith('/') ? (
    <Link to={link} className={`${cls} hover:opacity-90`}>
      {body}
    </Link>
  ) : (
    <a href={link} className={`${cls} hover:opacity-90`}>
      {body}
    </a>
  )
}

function Hero({
  name,
  tagline,
  s,
  site,
  image,
  separate,
  stats,
}: {
  name: string
  tagline: string
  s: Strings
  site: SiteConfig
  image: string | null
  separate: boolean
  stats: { value: string; label: string }[]
}) {
  const copy = (
    <>
      <span className="rounded-full border bg-background/70 px-3 py-1 text-xs font-medium text-muted-foreground backdrop-blur">
        {pick(site.heroBadge, pick(DEFAULT_HERO_BADGE))}
      </span>
      <h1 className="text-4xl font-bold tracking-tight sm:text-6xl">
        {name}
        <span className="mt-2 block bg-gradient-to-r from-primary to-foreground bg-clip-text pb-2 leading-[1.2] text-transparent">{tagline}</span>
      </h1>
      <p className="max-w-2xl text-lg text-muted-foreground">{pick(site.heroText, pick(DEFAULT_HERO_TEXT))}</p>
      <div className={`flex flex-wrap gap-3 ${separate ? '' : 'justify-center'}`}>
        <Button asChild size="lg">
          <a href="#enquiry">
            {s.counselling} <ArrowRightIcon />
          </a>
        </Button>
        <Button asChild size="lg" variant="outline">
          <a href="#courses">{s.explore}</a>
        </Button>
      </div>
    </>
  )
  return (
    <section id="top" className="relative isolate overflow-hidden px-4 pb-20 pt-16 sm:pt-24">
      {image && !separate && <img src={image} alt="" className="absolute inset-0 -z-20 size-full object-cover opacity-20" />}
      <div className="blob absolute -left-24 top-0 -z-10 size-96 rounded-full bg-primary/25 blur-3xl" aria-hidden="true" />
      <div className="blob blob-slow absolute -right-24 top-32 -z-10 size-96 rounded-full bg-accent blur-3xl" aria-hidden="true" />
      {separate && image ? (
        <div className="mx-auto grid max-w-6xl items-center gap-10 lg:grid-cols-2">
          <div className="flex flex-col items-start gap-6 text-left">{copy}</div>
          <img src={image} alt="" className="aspect-[4/3] w-full rounded-2xl object-cover shadow-xl" />
        </div>
      ) : (
        <div className="mx-auto flex max-w-4xl flex-col items-center gap-6 text-center">{copy}</div>
      )}
      <dl className="mx-auto mt-10 grid w-full max-w-3xl grid-cols-2 gap-3 sm:grid-cols-4">
        {stats.map(({ value, label }, i) => (
          <div key={i} className="rounded-xl border bg-background/70 p-4 text-center backdrop-blur">
            <dt className="sr-only">{label}</dt>
            <dd className="text-2xl font-bold text-primary">{value}</dd>
            <dd className="text-xs text-muted-foreground">{label}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function CourseCard({ course, site, s }: { course: CourseSummaryResponse; site: SiteConfig; s: Strings }) {
  const tags = (course.technologyStack ?? '').split(/[,/|]/).map((t) => t.trim()).filter(Boolean).slice(0, 4)
  const photo = site.courseCards.images[String(course.id)]
  const fee = site.courseCards.showFee && course.fee ? `${s.fee}: ₹${new Intl.NumberFormat('en-IN').format(course.fee)}` : ''
  const duration = site.courseCards.showDuration && course.durationHours ? `${course.durationHours} ${s.hours}` : ''
  return (
    <article className="flex flex-col overflow-hidden rounded-xl border bg-card transition-all hover:-translate-y-1 hover:shadow-lg">
      {photo && <img src={siteImageUrl(photo)} alt="" loading="lazy" className="h-40 w-full object-cover" />}
      <div className={`p-5 text-primary-foreground ${photo ? 'bg-primary' : 'bg-gradient-to-br from-primary/80 to-primary/30'}`}>
        <span className="rounded-full bg-background/25 px-2 py-0.5 text-xs font-medium capitalize">{course.level.toLowerCase()}</span>
        <h3 className="mt-3 text-lg font-semibold leading-snug">{course.title}</h3>
      </div>
      <div className="flex flex-1 flex-col gap-3 p-5">
        <p className="line-clamp-3 text-sm text-muted-foreground">{course.summary ?? s.syllabus}</p>
        {tags.length > 0 && (
          <ul className="flex flex-wrap gap-1.5">
            {tags.map((tag) => (
              <li key={tag} className="rounded-md bg-muted px-2 py-0.5 text-xs">
                {tag}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-auto flex items-center justify-between gap-2 pt-2 text-sm">
          <span className="text-muted-foreground">{[duration, fee].filter(Boolean).join(' · ')}</span>
          <a href="#enquiry" className="inline-flex shrink-0 items-center gap-1 font-medium text-primary hover:underline">
            {s.askAbout} <ArrowRightIcon className="size-4" />
          </a>
        </div>
      </div>
    </article>
  )
}

function Gallery({ site, closeLabel }: { site: SiteConfig; closeLabel: string }) {
  const [open, setOpen] = useState<number | null>(null)
  const photo = open === null ? null : site.gallery[open]
  return (
    <>
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {site.gallery.map((item, i) => (
          <li key={i}>
            <button type="button" onClick={() => setOpen(i)} className="group relative block aspect-[4/3] w-full overflow-hidden rounded-xl border">
              <img
                src={siteImageUrl(item.image)}
                alt={pick(item.caption)}
                loading="lazy"
                className="size-full object-cover transition-transform duration-500 group-hover:scale-105"
              />
            </button>
          </li>
        ))}
      </ul>
      <Dialog open={photo !== null} onOpenChange={(o) => !o && setOpen(null)}>
        <DialogContent className="max-w-4xl p-2">
          <DialogTitle className="sr-only">{photo ? pick(photo.caption) || 'Photo' : 'Photo'}</DialogTitle>
          <DialogDescription className="sr-only">{closeLabel}</DialogDescription>
          {photo && (
            <figure>
              <img src={siteImageUrl(photo.image)} alt={pick(photo.caption)} className="max-h-[75vh] w-full rounded-md object-contain" />
              {pick(photo.caption) && <figcaption className="p-2 text-center text-sm text-muted-foreground">{pick(photo.caption)}</figcaption>}
            </figure>
          )}
        </DialogContent>
      </Dialog>
    </>
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
