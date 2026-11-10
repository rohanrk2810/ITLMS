import { type ReactNode, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ClockIcon, ExternalLinkIcon, MailIcon, MapPinIcon, MessageCircleIcon, PhoneIcon, QuoteIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { getAllPublicCourses } from '@/api/public-site'
import type { CourseSummaryResponse } from '@/api/courses'
import { CourseActions } from '@/components/course-actions'
import { CourseTile } from '@/components/course-tile'
import { EnquiryBlock } from '@/components/enquiry-block'
import { SiteShell } from '@/components/site-shell'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatMoney, hasCourseFee } from '@/lib/format'
import { isMapEmbed, phoneDigits, pick, siteImageUrl, useSite } from '@/lib/site-config'
import { useBranding } from '@/lib/use-branding'
import { DEFAULT_ABOUT_INTRO, DEFAULT_ABOUT_POINTS, DEFAULT_FEATURES } from '@/pages/home/home-content'

import { BLOG_POSTS, CAREER_STEPS, DEFAULT_FAQS, FREE_RESOURCES, LEVEL_BLURB } from './site-page-content'

function useCourses() {
  const query = useQuery({ queryKey: ['public-courses', 'all'], queryFn: getAllPublicCourses, staleTime: 5 * 60_000 })
  return { ...query, courses: useMemo(() => query.data?.content ?? [], [query.data]) }
}

const LEVEL_ORDER = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']
const byLevel = (a: string, b: string) => (LEVEL_ORDER.indexOf(a) + 99) % 99 - (LEVEL_ORDER.indexOf(b) + 99) % 99 || a.localeCompare(b)

function Loading() {
  return (
    <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {[0, 1, 2].map((i) => (
        <Skeleton key={i} className="h-56" />
      ))}
    </div>
  )
}

function Empty({ children }: { children: ReactNode }) {
  return <p className="text-center text-sm text-muted-foreground">{children}</p>
}

function Panel({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border bg-card p-6">
      {title && <h2 className="mb-3 text-lg font-semibold">{title}</h2>}
      <div className="flex flex-col gap-3 text-sm leading-relaxed text-muted-foreground">{children}</div>
    </section>
  )
}

function Cta({ children }: { children: ReactNode }) {
  return <div className="mt-8 flex flex-wrap justify-center gap-3">{children}</div>
}

// ---------------------------------------------------------------------------------------------------------------

/** /about */
export function AboutPage() {
  const { name } = useBranding()
  const site = useSite()
  const points = site.aboutPoints ?? DEFAULT_ABOUT_POINTS
  const features = site.features ?? DEFAULT_FEATURES
  return (
    <SiteShell title={`About ${name}`} intro={pick(site.aboutIntro, DEFAULT_ABOUT_INTRO)}>
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <Panel title="Why learn with us">
          <ul className="flex list-disc flex-col gap-2 pl-5">
            {points.map((point, i) => (
              <li key={i}>{pick(point)}</li>
            ))}
          </ul>
        </Panel>
        <div className="grid gap-4 sm:grid-cols-2">
          {features.map((feature, i) => (
            <Panel key={i} title={pick(feature.title)}>
              <p>{pick(feature.text)}</p>
            </Panel>
          ))}
        </div>
        {site.faculty.length > 0 && (
          <Panel title="Our trainers">
            <ul className="grid gap-4 sm:grid-cols-2">
              {site.faculty.map((person, i) => (
                <li key={i} className="flex items-center gap-3">
                  {person.image && <img src={siteImageUrl(person.image)} alt="" loading="lazy" className="size-12 rounded-full object-cover" />}
                  <span>
                    <span className="block font-medium text-foreground">{person.name}</span>
                    {pick(person.role) && <span className="block text-xs">{pick(person.role)}</span>}
                  </span>
                </li>
              ))}
            </ul>
          </Panel>
        )}
      </div>
      <Cta>
        <Button asChild>
          <Link to="/courses">See our courses</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/contact">Contact us</Link>
        </Button>
      </Cta>
    </SiteShell>
  )
}

/** /programs: the courses grouped by level. */
export function ProgramsPage() {
  const { courses, isLoading, isError } = useCourses()
  const levels = Array.from(new Set(courses.map((c) => c.level))).sort(byLevel)
  return (
    <SiteShell title="Programs" intro="Our courses grouped by level, so you can start where you are.">
      {isLoading && <Loading />}
      {isError && <Empty>The programs could not be loaded. Please try again.</Empty>}
      {!isLoading && !isError && courses.length === 0 && <Empty>No programs are open right now.</Empty>}
      <div className="flex flex-col gap-12">
        {levels.map((level) => (
          <section key={level}>
            <h2 className="text-2xl font-semibold capitalize">{level.toLowerCase()}</h2>
            <p className="mb-4 text-sm text-muted-foreground">{LEVEL_BLURB[level] ?? ''}</p>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {courses
                .filter((c) => c.level === level)
                .map((course) => (
                  <CourseTile key={course.id} course={course} />
                ))}
            </div>
          </section>
        ))}
      </div>
    </SiteShell>
  )
}

/** /pricing: every course with its fee, and how to pay. */
export function PricingPage() {
  const { courses, isLoading, isError } = useCourses()
  return (
    <SiteShell title="Pricing" intro="What each course costs. Pay online and your receipt arrives by email straight away.">
      {isLoading && <Loading />}
      {isError && <Empty>The fees could not be loaded. Please try again.</Empty>}
      {!isLoading && !isError && courses.length === 0 && <Empty>No courses are open right now.</Empty>}
      {courses.length > 0 && (
        <div className="overflow-x-auto rounded-xl border">
          <table className="w-full min-w-[34rem] text-left text-sm">
            <thead className="bg-muted/50 text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Course</th>
                <th className="px-4 py-3">Level</th>
                <th className="px-4 py-3">Duration</th>
                <th className="px-4 py-3">Fee</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {courses.map((course: CourseSummaryResponse) => (
                <tr key={course.id} className="border-t">
                  <td className="px-4 py-3 font-medium">{course.title}</td>
                  <td className="px-4 py-3 capitalize">{course.level.toLowerCase()}</td>
                  <td className="px-4 py-3">{course.durationHours ? `${course.durationHours} hours` : '-'}</td>
                  <td className="px-4 py-3 font-semibold">{hasCourseFee(course.fee) ? formatMoney(course.fee ?? 0) : 'On request'}</td>
                  <td className="min-w-48 px-4 py-3">
                    <CourseActions course={course} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="mx-auto mt-8 grid max-w-3xl gap-4 sm:grid-cols-2">
        <Panel title="How to pay">
          <p>Sign in as a student, press Buy on a course and pay with UPI, card or net banking. You are added to a batch as soon as the payment is confirmed.</p>
          <p>You can also pay at the institute; ask for the receipt.</p>
        </Panel>
        <Panel title="Receipts and refunds">
          <p>Every payment gets a PDF receipt by email, and you can download it again from Fees in your portal.</p>
          <p>
            Refunds follow our{' '}
            <Link to="/refund" className="underline">
              Refund Policy
            </Link>
            .
          </p>
        </Panel>
      </div>
    </SiteShell>
  )
}

/** /blog */
export function BlogPage() {
  return (
    <SiteShell title="Blog" intro="Short, practical reads for people starting out in IT." narrow>
      <div className="flex flex-col gap-4">
        {BLOG_POSTS.map((post) => (
          <details key={post.title} className="group rounded-xl border bg-card p-5 open:shadow-sm">
            <summary className="cursor-pointer list-none marker:hidden [&::-webkit-details-marker]:hidden">
              <h2 className="text-lg font-semibold">{post.title}</h2>
              <p className="mt-1 text-sm text-muted-foreground">{post.summary}</p>
              <span className="mt-2 inline-block text-xs font-medium text-primary group-open:hidden">Read more</span>
            </summary>
            <div className="mt-4 flex flex-col gap-3 text-sm leading-relaxed">
              {post.body.map((paragraph, i) => (
                <p key={i}>{paragraph}</p>
              ))}
            </div>
          </details>
        ))}
      </div>
      <Cta>
        <Button asChild>
          <Link to="/courses">Find a course</Link>
        </Button>
      </Cta>
    </SiteShell>
  )
}

/** /success-stories: the feedback the administrator added under Customize > Website. */
export function SuccessStoriesPage() {
  const site = useSite()
  return (
    <SiteShell title="Success stories" intro="What our students say.">
      {site.testimonials.length === 0 ? (
        <Empty>Student stories will appear here soon.</Empty>
      ) : (
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
      )}
      <Cta>
        <Button asChild>
          <Link to="/courses">Start your own</Link>
        </Button>
      </Cta>
    </SiteShell>
  )
}

/** /learning-paths: from beginner to advanced, with the courses at each step. */
export function LearningPathsPage() {
  const { courses, isLoading, isError } = useCourses()
  const levels = Array.from(new Set(courses.map((c) => c.level))).sort(byLevel)
  return (
    <SiteShell title="Learning paths" intro="A step-by-step route from the basics to job-ready skills." narrow>
      {isLoading && <Loading />}
      {isError && <Empty>The courses could not be loaded. Please try again.</Empty>}
      <ol className="flex flex-col gap-5">
        {levels.map((level, index) => (
          <li key={level} className="rounded-xl border bg-card p-6">
            <div className="flex items-center gap-3">
              <span className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">{index + 1}</span>
              <h2 className="text-lg font-semibold capitalize">{level.toLowerCase()}</h2>
            </div>
            <p className="mt-2 text-sm text-muted-foreground">{LEVEL_BLURB[level] ?? ''}</p>
            <ul className="mt-3 flex flex-col gap-1 text-sm">
              {courses
                .filter((c) => c.level === level)
                .map((course) => (
                  <li key={course.id} className="flex items-center justify-between gap-3 border-t py-2 first:border-t-0">
                    <span className="font-medium">{course.title}</span>
                    <span className="text-xs text-muted-foreground">{course.durationHours ? `${course.durationHours} hours` : ''}</span>
                  </li>
                ))}
            </ul>
          </li>
        ))}
      </ol>
      {!isLoading && !isError && levels.length === 0 && <Empty>Our learning paths will appear here once courses are published.</Empty>}
      <Cta>
        <Button asChild>
          <Link to="/courses">Browse all courses</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/career-guide">Read the career guide</Link>
        </Button>
      </Cta>
    </SiteShell>
  )
}

/** /career-guide */
export function CareerGuidePage() {
  return (
    <SiteShell title="Career guide" intro="Six steps from learning to your first job." narrow>
      <ol className="flex flex-col gap-4">
        {CAREER_STEPS.map((step, index) => (
          <li key={step.title} className="flex gap-4 rounded-xl border bg-card p-5">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">{index + 1}</span>
            <div>
              <h2 className="font-semibold">{step.title}</h2>
              <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{step.text}</p>
            </div>
          </li>
        ))}
      </ol>
      <Cta>
        <Button asChild>
          <Link to="/courses">Pick a course</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/contact">Talk to a counsellor</Link>
        </Button>
      </Cta>
    </SiteShell>
  )
}

/** /community: WhatsApp when the administrator has set it, otherwise a way to reach the team. */
export function CommunityPage() {
  const site = useSite()
  const digits = phoneDigits(site.social.whatsapp)
  return (
    <SiteShell title="Student community" intro="Learn alongside other students and talk to the team." narrow>
      <div className="flex flex-col gap-5">
        <Panel title="Learn together">
          <p>Learning goes faster with people around you. Our students and trainers share doubts, tips and news about new batches.</p>
          <p>Inside the portal you also get live classes, tests and announcements for your batch.</p>
        </Panel>
        <Panel title="Join in">
          {digits ? (
            <>
              <p>Message us on WhatsApp and we will add you to the right group.</p>
              <div>
                <Button asChild>
                  <a href={`https://wa.me/${digits}?text=${encodeURIComponent('Hello, I would like to join the student community.')}`} target="_blank" rel="noreferrer noopener">
                    <MessageCircleIcon /> Message us on WhatsApp
                  </a>
                </Button>
              </div>
            </>
          ) : (
            <>
              <p>Send us a message and we will tell you how to join.</p>
              <div>
                <Button asChild>
                  <Link to="/contact">Contact us</Link>
                </Button>
              </div>
            </>
          )}
        </Panel>
      </div>
    </SiteShell>
  )
}

/** /free-resources */
export function FreeResourcesPage() {
  return (
    <SiteShell title="Free resources" intro="Good places to practise, whatever you decide to study." narrow>
      <ul className="flex flex-col gap-4">
        {FREE_RESOURCES.map((item) => (
          <li key={item.href}>
            <a
              href={item.href}
              target="_blank"
              rel="noreferrer noopener"
              className="flex items-start justify-between gap-4 rounded-xl border bg-card p-5 transition-shadow hover:shadow-md"
            >
              <span>
                <span className="block font-semibold">{item.title}</span>
                <span className="mt-1 block text-sm text-muted-foreground">{item.text}</span>
              </span>
              <ExternalLinkIcon className="mt-1 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            </a>
          </li>
        ))}
      </ul>
      <p className="mt-6 text-center text-xs text-muted-foreground">These are independent websites that open in a new tab.</p>
    </SiteShell>
  )
}

/** /faq: the questions the administrator wrote, or a short default set. */
export function FaqPage() {
  const site = useSite()
  const items = site.faqs.length > 0 ? site.faqs.map((f) => ({ q: pick(f.q), a: pick(f.a) })) : DEFAULT_FAQS
  return (
    <SiteShell title="Frequently asked questions" intro="Quick answers. Still stuck? Send us a message." narrow>
      <div className="flex flex-col gap-3">
        {items.map((item, i) => (
          <details key={i} className="group rounded-xl border bg-card p-4 open:shadow-sm">
            <summary className="cursor-pointer list-none font-medium marker:hidden [&::-webkit-details-marker]:hidden">
              <span className="flex items-center justify-between gap-3">
                {item.q}
                <span className="text-primary transition-transform group-open:rotate-45" aria-hidden="true">
                  +
                </span>
              </span>
            </summary>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{item.a}</p>
          </details>
        ))}
      </div>
      <Cta>
        <Button asChild>
          <Link to="/contact">Ask us</Link>
        </Button>
      </Cta>
    </SiteShell>
  )
}

/** /contact */
export function ContactPage() {
  const { branding } = useBranding()
  const site = useSite()
  const address = branding?.address ?? ''
  const embed = isMapEmbed(site.mapUrl)
    ? site.mapUrl
    : address
      ? `https://maps.google.com/maps?q=${encodeURIComponent(address)}&output=embed`
      : ''
  return (
    <SiteShell title="Contact us" intro="Call, write or send a message and the team will get back to you.">
      <div className="grid gap-8 lg:grid-cols-2">
        <div className="flex flex-col gap-4">
          {branding?.contactPhone && <Item icon={<PhoneIcon />} label="Phone" value={branding.contactPhone} href={`tel:${branding.contactPhone.replace(/\s+/g, '')}`} />}
          {branding?.contactEmail && <Item icon={<MailIcon />} label="Email" value={branding.contactEmail} href={`mailto:${branding.contactEmail}`} />}
          {address && <Item icon={<MapPinIcon />} label="Address" value={address} />}
          <Item icon={<ClockIcon />} label="Already a student?" value="Sign in to your portal" href="/login" />
          {embed && (
            <iframe src={embed} title="Map" loading="lazy" referrerPolicy="no-referrer-when-downgrade" className="h-64 w-full rounded-xl border" />
          )}
          {!isMapEmbed(site.mapUrl) && site.mapUrl && (
            <Button asChild variant="outline">
              <a href={site.mapUrl} target="_blank" rel="noreferrer noopener">
                <MapPinIcon /> Open in Google Maps
              </a>
            </Button>
          )}
        </div>
        <div>
          <EnquiryBlock />
        </div>
      </div>
    </SiteShell>
  )
}

function Item({ icon, label, value, href }: { icon: ReactNode; label: string; value: string; href?: string }) {
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

// ---------------------------------------------------------------------------------------------------------------
// Pages for people who want to work or partner with us: a short explanation and the enquiry form, with a first line
// of the message already written so the team knows what the enquiry is about.
// ---------------------------------------------------------------------------------------------------------------

function ApplyPage({ title, intro, points, message }: { title: string; intro: string; points: string[]; message: string }) {
  return (
    <SiteShell title={title} intro={intro}>
      <div className="grid gap-8 lg:grid-cols-2">
        <Panel title="What to expect">
          <ul className="flex list-disc flex-col gap-2 pl-5">
            {points.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        </Panel>
        <EnquiryBlock message={message} />
      </div>
    </SiteShell>
  )
}

/** /become-an-instructor */
export function InstructorPage() {
  return (
    <ApplyPage
      title="Become an instructor"
      intro="Teach what you know to students who are ready to work."
      points={[
        'Tell us your subject, your experience and when you are free.',
        'The team reads every message and calls you back.',
        'Classes can be in the classroom or live online, with attendance, tests and material handled in the portal.',
      ]}
      message="I would like to teach with you. My subject and experience: "
    />
  )
}

/** /careers */
export function CareersPage() {
  return (
    <ApplyPage
      title="Careers"
      intro="Work with a team that helps people start their careers."
      points={[
        'Tell us the kind of work you are looking for and a little about yourself.',
        'We keep your details and contact you when a suitable position opens.',
        'Trainers can apply from the Become an instructor page.',
      ]}
      message="I would like to work with you. The role I am looking for and my experience: "
    />
  )
}

/** /affiliates */
export function AffiliatesPage() {
  return (
    <ApplyPage
      title="Affiliates"
      intro="Partner with us and bring learners to the right course."
      points={[
        'Colleges, communities and creators can partner with us.',
        'Tell us who you are and who you reach, and the team will explain how a partnership works.',
        'We reply to every message.',
      ]}
      message="I would like to partner with you as an affiliate. About me and the people I reach: "
    />
  )
}
