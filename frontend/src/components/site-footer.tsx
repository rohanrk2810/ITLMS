import { type FormEvent, type ReactNode, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { assetUrl } from '@/api/branding'
import type { CourseSummaryResponse } from '@/api/courses'
import { getPublicCourses } from '@/api/public-site'
import { LegalLinks } from '@/components/legal-links'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { EnquiryForm } from '@/pages/home/enquiry-form'
import { phoneDigits, useSite } from '@/lib/site-config'
import { useBranding } from '@/lib/use-branding'

/**
 * Footer links. Every one opens a page of this app (routes in App.tsx, pages in pages/site and pages/courses),
 * never another website.
 */
const COLUMNS: { title: string; links: { label: string; to: string }[] }[] = [
  {
    title: 'Explore',
    links: [
      { label: 'Courses', to: '/courses' },
      { label: 'Programs', to: '/programs' },
      { label: 'Pricing', to: '/pricing' },
      { label: 'Blog', to: '/blog' },
      { label: 'Success Stories', to: '/success-stories' },
    ],
  },
  {
    title: 'Resources',
    links: [
      { label: 'Learning Paths', to: '/learning-paths' },
      { label: 'Career Guide', to: '/career-guide' },
      { label: 'Student Community', to: '/community' },
      { label: 'Free Resources', to: '/free-resources' },
      { label: 'FAQ', to: '/faq' },
    ],
  },
  {
    title: 'Company',
    links: [
      { label: 'About Us', to: '/about' },
      { label: 'Contact Us', to: '/contact' },
      { label: 'Become an Instructor', to: '/become-an-instructor' },
      { label: 'Careers', to: '/careers' },
      { label: 'Affiliates', to: '/affiliates' },
    ],
  },
]

/** The site footer: brand and tagline, social links, three link columns and a "Stay Updated" box. */
export function SiteFooter() {
  const { branding, name } = useBranding()
  const site = useSite()
  const logo = assetUrl(branding?.logoUrl ?? null)
  const [email, setEmail] = useState('')
  const [open, setOpen] = useState(false)
  const courses = useQuery({ queryKey: ['public-courses'], queryFn: getPublicCourses, staleTime: 5 * 60_000 })
  const list: CourseSummaryResponse[] = courses.data?.content ?? []

  const whatsapp = phoneDigits(site.social.whatsapp)
  const socials = [
    { label: 'Facebook', href: site.social.facebook, icon: <FacebookIcon /> },
    { label: 'LinkedIn', href: site.social.linkedin, icon: <LinkedinIcon /> },
    { label: 'YouTube', href: site.social.youtube, icon: <YoutubeIcon /> },
    { label: 'Instagram', href: site.social.instagram, icon: <InstagramIcon /> },
    { label: 'WhatsApp', href: whatsapp ? `https://wa.me/${whatsapp}` : '', icon: <WhatsappIcon /> },
  ].filter((x) => x.href)

  function subscribe(event: FormEvent) {
    event.preventDefault()
    if (email.trim()) setOpen(true)
  }

  return (
    <footer className="bg-neutral-950 text-neutral-300">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-14 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr_1.4fr]">
        <div className="flex flex-col gap-4">
          <Link to="/" className="flex items-center gap-3">
            {logo ? (
              <img src={logo} alt="" className="size-12 rounded-xl bg-white/10 object-contain p-1" />
            ) : (
              <span className="flex size-12 items-center justify-center rounded-xl bg-primary text-lg font-bold text-primary-foreground">
                {name.slice(0, 1)}
              </span>
            )}
            <span className="text-lg font-bold leading-tight text-white">{name}</span>
          </Link>
          {branding?.tagline && <p className="max-w-xs text-sm leading-relaxed text-neutral-400">{branding.tagline}</p>}
          {socials.length > 0 && (
            <ul aria-label="Follow us" className="flex flex-wrap gap-2">
              {socials.map((social) => (
                <li key={social.label}>
                  <a
                    href={social.href}
                    target="_blank"
                    rel="noreferrer noopener"
                    aria-label={social.label}
                    className="flex size-10 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-white transition-colors hover:bg-primary"
                  >
                    {social.icon}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>

        {COLUMNS.map((column) => (
          <nav key={column.title} aria-label={column.title}>
            <h3 className="mb-4 text-sm font-semibold text-white">{column.title}</h3>
            <ul className="flex flex-col gap-3 text-sm">
              {column.links.map((link) => (
                <li key={link.label}>
                  <Link to={link.to} className="text-neutral-400 transition-colors hover:text-white">
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}

        <div>
          <h3 className="mb-4 text-sm font-semibold text-white">Stay Updated</h3>
          <p className="mb-4 text-sm text-neutral-400">Subscribe to get the latest courses and updates.</p>
          <form onSubmit={subscribe} className="flex overflow-hidden rounded-lg border border-white/10 bg-white/5">
            <input
              type="email"
              required
              aria-label="Your email"
              placeholder="Enter your email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="min-w-0 flex-1 bg-transparent px-3 py-2.5 text-sm text-white outline-none placeholder:text-neutral-500"
            />
            <Button type="submit" className="rounded-none">
              Subscribe
            </Button>
          </form>
        </div>
      </div>

      <div className="border-t border-white/10 px-4 py-6">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-3 text-center">
          <LegalLinks className="text-neutral-400" />
          <p className="text-xs text-neutral-500">
            &copy; {new Date().getFullYear()} {name}. All rights reserved.
          </p>
        </div>
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Stay updated</DialogTitle>
            <DialogDescription>Add your name and phone number and the admission team will keep you posted.</DialogDescription>
          </DialogHeader>
          <EnquiryForm courses={list} initialEmail={email.trim()} />
        </DialogContent>
      </Dialog>
    </footer>
  )
}

function Svg({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden="true">
      {children}
    </svg>
  )
}

function FacebookIcon() {
  return (
    <Svg>
      <path d="M13.5 21v-8h2.7l.4-3.2h-3.1V7.8c0-.9.3-1.5 1.6-1.5h1.7V3.4c-.3 0-1.3-.1-2.4-.1-2.4 0-4 1.5-4 4.1v2.4H7.7V13h2.7v8h3.1z" />
    </Svg>
  )
}

function LinkedinIcon() {
  return (
    <Svg>
      <path d="M6.9 9.2H3.7V20h3.2V9.2zM5.3 4a1.9 1.9 0 100 3.8 1.9 1.9 0 000-3.8zM20.3 13.7c0-3-1.6-4.7-4-4.7-1.4 0-2.3.7-2.7 1.4V9.2h-3.1V20h3.2v-5.9c0-1.5.6-2.5 1.9-2.5 1.2 0 1.7.9 1.7 2.5V20h3.2l-.2-6.3z" />
    </Svg>
  )
}

function YoutubeIcon() {
  return (
    <Svg>
      <path d="M21.6 7.2a2.5 2.5 0 00-1.8-1.8C18.2 5 12 5 12 5s-6.2 0-7.8.4A2.5 2.5 0 002.4 7.2C2 8.8 2 12 2 12s0 3.2.4 4.8a2.5 2.5 0 001.8 1.8C5.8 19 12 19 12 19s6.2 0 7.8-.4a2.5 2.5 0 001.8-1.8C22 15.2 22 12 22 12s0-3.2-.4-4.8zM10 15V9l5.2 3L10 15z" />
    </Svg>
  )
}

function InstagramIcon() {
  return (
    <Svg>
      <path d="M12 7.4A4.6 4.6 0 1012 16.6 4.6 4.6 0 0012 7.4zm0 7.6a3 3 0 110-6 3 3 0 010 6zM17.2 5.5a1.1 1.1 0 100 2.2 1.1 1.1 0 000-2.2z" />
      <path d="M16.5 3h-9A4.5 4.5 0 003 7.5v9A4.5 4.5 0 007.5 21h9a4.5 4.5 0 004.5-4.5v-9A4.5 4.5 0 0016.5 3zm3 13.5a3 3 0 01-3 3h-9a3 3 0 01-3-3v-9a3 3 0 013-3h9a3 3 0 013 3v9z" />
    </Svg>
  )
}

function WhatsappIcon() {
  return (
    <Svg>
      <path d="M12 3a9 9 0 00-7.7 13.6L3 21l4.5-1.2A9 9 0 1012 3zm0 16.3c-1.4 0-2.7-.4-3.8-1.1l-.3-.2-2.7.7.7-2.6-.2-.3A7.3 7.3 0 1112 19.3zm4-5.5c-.2-.1-1.3-.6-1.5-.7-.2-.1-.3-.1-.5.1l-.7.9c-.1.1-.3.2-.5.1a6 6 0 01-3-2.6c-.2-.4.2-.4.6-1.2.1-.1 0-.3 0-.4l-.7-1.6c-.2-.4-.4-.4-.5-.4h-.4c-.1 0-.4.1-.6.3-.2.2-.8.8-.8 1.9s.8 2.2.9 2.4c.1.1 1.6 2.5 4 3.5 1.5.6 2 .7 2.8.6.4-.1 1.3-.5 1.5-1.1.2-.5.2-1 .1-1.1l-.4-.2z" />
    </Svg>
  )
}
