import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

import { env } from '@/lib/env'
import { getSiteConfig } from '@/api/branding'

/** A piece of text. Anything left empty falls back to the built-in wording. */
export type T = string
export const emptyT = (): T => ''

export interface Item {
  title: T
  text: T
}
export interface Stat {
  value: string
  label: T
}
export interface Testimonial {
  name: string
  role: T
  text: T
  image: string
}
export interface Faq {
  q: T
  a: T
}
export interface Faculty {
  name: string
  role: T
  image: string
}
export interface GalleryPhoto {
  image: string
  caption: T
}
export interface Partner {
  name: string
  image: string
}

/** What the public front page says and shows. Pictures are stored by id (see /api/branding/site-images). */
export interface SiteConfig {
  heroBadge: T
  heroTitle: T
  heroText: T
  heroImage: string
  stats?: Stat[]
  aboutIntro: T
  aboutPoints?: T[]
  features?: Item[]
  steps?: Item[]
  gallery: GalleryPhoto[]
  faculty: Faculty[]
  partners: Partner[]
  testimonials: Testimonial[]
  faqs: Faq[]
  courseCards: { showFee: boolean; showDuration: boolean; images: Record<string, string> }
  social: { whatsapp: string; instagram: string; youtube: string; facebook: string; linkedin: string }
  whatsappButton: boolean
  whatsappMessage: T
  /** A Google Maps address: the embed address (Share > Embed a map) or an ordinary share link. */
  mapUrl: string
  announcement: { enabled: boolean; text: T; link: string }
  /** Section ids to leave out of the page. */
  hidden: string[]
}

export const SITE_SECTIONS = [
  { id: 'about', label: 'About' },
  { id: 'courses', label: 'Courses' },
  { id: 'why', label: 'Why us' },
  { id: 'how', label: 'How it works' },
  { id: 'gallery', label: 'Photo gallery' },
  { id: 'faculty', label: 'Trainers' },
  { id: 'partners', label: 'Hiring partners' },
  { id: 'testimonials', label: 'Student feedback' },
  { id: 'faq', label: 'Questions and answers' },
  { id: 'enquiry', label: 'Enquiry form' },
  { id: 'contact', label: 'Contact' },
] as const

/** The server accepts 60,000 characters; pictures are stored by id so these caps leave plenty of room. */
export const SITE_LIMITS = {
  stats: 4,
  points: 6,
  features: 9,
  steps: 6,
  gallery: 18,
  faculty: 12,
  partners: 18,
  testimonials: 6,
  faqs: 14,
}

export const SERVER_LIMIT = 60_000

export const DEFAULT_SITE: SiteConfig = {
  heroBadge: emptyT(),
  heroTitle: emptyT(),
  heroText: emptyT(),
  heroImage: '',
  aboutIntro: emptyT(),
  gallery: [],
  faculty: [],
  partners: [],
  testimonials: [],
  faqs: [],
  courseCards: { showFee: false, showDuration: true, images: {} },
  social: { whatsapp: '', instagram: '', youtube: '', facebook: '', linkedin: '' },
  whatsappButton: false,
  whatsappMessage: emptyT(),
  mapUrl: '',
  announcement: { enabled: false, text: emptyT(), link: '' },
  hidden: [],
}

// --- reading the stored JSON: anything missing or the wrong shape is ignored, and it never throws -----------------

type Raw = Record<string, unknown>
const obj = (v: unknown): Raw => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Raw) : {})
const arr = (v: unknown): Raw[] => (Array.isArray(v) ? v.filter((x): x is Raw => !!x && typeof x === 'object') : [])
const str = (v: unknown, max: number): string => (typeof v === 'string' ? v.trim().slice(0, max) : '')
/** Text is stored as a plain string. Content saved while two languages existed ({ en, mr }) still reads: the English is kept. */
const tx = (v: unknown, max: number): T => (typeof v === 'string' ? str(v, max) : str(obj(v).en, max))
const hasText = (t: T) => t !== ''
const imageId = (v: unknown): string => (typeof v === 'string' && /^\d{1,12}$/.test(v) ? v : typeof v === 'number' && Number.isInteger(v) && v > 0 ? String(v) : '')
const cap = <X>(list: X[], n: number): X[] => list.slice(0, n)

/** Only web addresses (or this site's own paths) may be links, so stored text can never become a script. */
export function safeUrl(v: unknown, max = 300): string {
  const s = str(v, max)
  return /^(https:\/\/|http:\/\/|\/|#)/i.test(s) ? s : ''
}
const MAP_EMBED = /^https:\/\/(www\.google\.com|maps\.google\.com)\/maps\/embed/i
const MAP_LINK = /^https:\/\/(maps\.app\.goo\.gl\/|goo\.gl\/maps\/|(www\.)?google\.[a-z.]+\/maps)/i

/** True for the address Google Maps gives under Share > Embed a map; anything else is an ordinary share link. */
export const isMapEmbed = (url: string) => MAP_EMBED.test(url)

/**
 * Pasting the whole `<iframe ...>` that Google gives is fine: its address is taken out of it.
 * Only Google Maps addresses are kept, either an embed address or an ordinary share link.
 */
export function cleanMapUrl(v: unknown): string {
  let s = typeof v === 'string' ? v.trim() : ''
  const inside = /src\s*=\s*["']([^"']+)["']/i.exec(s)
  if (inside) s = inside[1]
  // A link pasted without its https:// start (maps.app.goo.gl/...) is still a Google Maps link.
  if (/^(maps\.app\.goo\.gl\/|goo\.gl\/maps\/|(www\.)?google\.[a-z.]+\/maps)/i.test(s)) s = `https://${s}`
  s = s.slice(0, 700)
  return MAP_EMBED.test(s) || MAP_LINK.test(s) ? s : ''
}
/** Keeps the digits of a phone number, with the country code, for a wa.me link. */
export const phoneDigits = (v: string) => v.replace(/\D/g, '')

export function parseSite(text: string | null | undefined): SiteConfig {
  let raw: Raw = {}
  try {
    raw = obj(JSON.parse(text || '{}'))
  } catch {
    // Fall back to the defaults.
  }
  const items = (v: unknown, limit: number): Item[] | undefined =>
    Array.isArray(v)
      ? cap(
          arr(v)
            .map((x) => ({ title: tx(x.title, 60), text: tx(x.text, 200) }))
            .filter((x) => hasText(x.title) || hasText(x.text)),
          limit,
        )
      : undefined
  const statList = Array.isArray(raw.stats)
    ? cap(
        arr(raw.stats)
          .map((x) => ({ value: str(x.value, 12), label: tx(x.label, 30) }))
          .filter((x) => x.value !== '' || hasText(x.label)),
        SITE_LIMITS.stats,
      )
    : undefined
  const courseCards = obj(raw.courseCards)
  const images: Record<string, string> = {}
  for (const [id, pic] of Object.entries(obj(courseCards.images))) {
    if (/^\d{1,12}$/.test(id) && imageId(pic)) images[id] = imageId(pic)
  }
  const social = obj(raw.social)
  const announcement = obj(raw.announcement)
  return {
    heroBadge: tx(raw.heroBadge, 40),
    heroTitle: tx(raw.heroTitle, 80),
    heroText: tx(raw.heroText, 240),
    heroImage: imageId(raw.heroImage),
    // No figures means "show the automatic ones".
    stats: statList && statList.length > 0 ? statList : undefined,
    aboutIntro: tx(raw.aboutIntro, 200),
    aboutPoints: Array.isArray(raw.aboutPoints)
      ? cap(
          (raw.aboutPoints as unknown[]).map((p) => tx(p, 160)).filter(hasText),
          SITE_LIMITS.points,
        )
      : undefined,
    features: items(raw.features, SITE_LIMITS.features),
    steps: items(raw.steps, SITE_LIMITS.steps),
    gallery: cap(
      arr(raw.gallery)
        .map((x) => ({ image: imageId(x.image), caption: tx(x.caption, 80) }))
        .filter((x) => x.image !== ''),
      SITE_LIMITS.gallery,
    ),
    faculty: cap(
      arr(raw.faculty)
        .map((x) => ({ name: str(x.name, 60), role: tx(x.role, 80), image: imageId(x.image) }))
        .filter((x) => x.name !== ''),
      SITE_LIMITS.faculty,
    ),
    partners: cap(
      arr(raw.partners)
        .map((x) => ({ name: str(x.name, 60), image: imageId(x.image) }))
        .filter((x) => x.image !== '' || x.name !== ''),
      SITE_LIMITS.partners,
    ),
    testimonials: cap(
      arr(raw.testimonials)
        .map((x) => ({ name: str(x.name, 60), role: tx(x.role, 80), text: tx(x.text, 300), image: imageId(x.image) }))
        .filter((x) => x.name !== '' && hasText(x.text)),
      SITE_LIMITS.testimonials,
    ),
    faqs: cap(
      arr(raw.faqs)
        .map((x) => ({ q: tx(x.q, 160), a: tx(x.a, 600) }))
        .filter((x) => hasText(x.q) && hasText(x.a)),
      SITE_LIMITS.faqs,
    ),
    courseCards: {
      showFee: courseCards.showFee === true,
      showDuration: courseCards.showDuration !== false,
      images,
    },
    social: {
      whatsapp: str(social.whatsapp, 20),
      instagram: safeUrl(social.instagram),
      youtube: safeUrl(social.youtube),
      facebook: safeUrl(social.facebook),
      linkedin: safeUrl(social.linkedin),
    },
    whatsappButton: raw.whatsappButton === true,
    whatsappMessage: tx(raw.whatsappMessage, 200),
    mapUrl: cleanMapUrl(raw.mapUrl),
    announcement: {
      enabled: announcement.enabled === true,
      text: tx(announcement.text, 160),
      link: safeUrl(announcement.link),
    },
    hidden: Array.isArray(raw.hidden) ? (raw.hidden as unknown[]).filter((h): h is string => typeof h === 'string') : [],
  }
}

/** The text, or the standard wording when it was left empty. */
export function pick(t: T | undefined, fallback = ''): string {
  return t || fallback
}

export function siteImageUrl(id: string): string {
  return `${env.apiBaseUrl}/api/public/branding/site-images/${id}`
}

/** The saved website content, read once for the front page. Falls back to the defaults while loading or if the server is down. */
export function useSite(): SiteConfig {
  const { data } = useQuery({ queryKey: ['site-config'], queryFn: getSiteConfig, staleTime: 60_000 })
  return useMemo(() => parseSite(data?.config), [data?.config])
}
