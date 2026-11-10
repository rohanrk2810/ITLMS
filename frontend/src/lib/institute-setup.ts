import type { UpdateBrandingInput } from '@/api/branding'

import catalog from './institute-setup-fields.json'
import { type SiteConfig, SITE_LIMITS, SITE_SECTIONS, parseSite } from './site-config'

/**
 * One file to set up the institute and the website: the same idea as the question bank. The institute (or ChatGPT,
 * given the prompt file) fills in a .json file, and it is checked and applied here. The list of fields is in
 * institute-setup-fields.json, which also produces the PDF data sheet (deploy/scripts/generate-setup-sheet.py), so
 * the prompt, the example, the checks and the sheet can never disagree.
 */

export interface FieldInfo {
  path: string
  label: string
  required?: boolean
  limit?: number
  list?: boolean
  what: string
  example: unknown
}

const FIELDS: FieldInfo[] = catalog.sections.flatMap((section) => section.fields as FieldInfo[])
const LABELS = new Map(FIELDS.map((f) => [f.path, f.label]))

export interface Problem {
  level: 'error' | 'warning'
  text: string
}

/** What a checked file would change. Only the parts that were in the file are touched. */
export interface Setup {
  institute: Partial<UpdateBrandingInput>
  /** The website keys that were in the file (a few written as dotted keys, such as social.instagram). */
  provided: string[]
  site: SiteConfig
}

export interface Checked {
  setup: Setup | null
  problems: Problem[]
  /** Plain lines saying what will be changed. */
  summary: string[]
}

// --- the example file and the prompt -------------------------------------------------------------------------------

function setPath(target: Record<string, unknown>, path: string, value: unknown) {
  const [head, ...rest] = path.split('.')
  if (rest.length === 0) {
    target[head] = value
    return
  }
  const next = (target[head] ?? {}) as Record<string, unknown>
  target[head] = next
  setPath(next, rest.join('.'), value)
}

/** A complete example, built from the field list. */
export function exampleSetup(): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const field of FIELDS) setPath(out, field.path, field.example)
  return out
}

export const EXAMPLE_JSON = JSON.stringify(exampleSetup(), null, 2)

/** The text to give ChatGPT: what to produce, every field, an example, and a place for the institute's facts. */
export function buildPrompt(): string {
  const lines: string[] = []
  lines.push('I want you to prepare the setup file for an institute and its website. Reply with ONE JSON file and nothing else.')
  lines.push('')
  lines.push('RULES')
  for (const rule of catalog.rules) lines.push(`- ${rule}`)
  lines.push('- Reply as a .json file (or one code block). Use exactly the names shown below. Text is plain text, no HTML.')
  lines.push('- Never invent facts. If you do not know something, leave that name out and ask me.')
  lines.push('')
  lines.push('THE FIELDS')
  for (const section of catalog.sections) {
    lines.push('')
    lines.push(`${section.title} (${section.note})`)
    for (const f of section.fields as FieldInfo[]) {
      const bits = [f.required ? 'required' : '', f.limit ? `up to ${f.limit}` : ''].filter(Boolean).join(', ')
      lines.push(`- ${f.path}: ${f.what}${bits ? ` (${bits})` : ''}`)
    }
  }
  lines.push('')
  lines.push('EXAMPLE OF THE FILE')
  lines.push(EXAMPLE_JSON)
  lines.push('')
  lines.push('MY INSTITUTE (write or paste everything about the institute here, in any language; ChatGPT writes the file in English):')
  lines.push('')
  return lines.join('\n')
}

// --- checking a file ----------------------------------------------------------------------------------------------

const INSTITUTE_KEYS = new Set(FIELDS.filter((f) => f.path.startsWith('institute.')).map((f) => f.path.slice('institute.'.length)))
const WEBSITE_KEYS = new Set(FIELDS.filter((f) => f.path.startsWith('website.')).map((f) => f.path.slice('website.'.length)))
const SECTION_IDS = new Set<string>(SITE_SECTIONS.map((s) => s.id))
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const COLOUR = /^#[0-9a-fA-F]{6}$/

/** Takes the JSON out of a reply: a code block, or text with the JSON in the middle. */
export function extractJson(text: string): string {
  const fenced = /```(?:json)?\s*([\s\S]*?)```/i.exec(text)
  const body = fenced ? fenced[1] : text
  const start = body.indexOf('{')
  const end = body.lastIndexOf('}')
  return start >= 0 && end > start ? body.slice(start, end + 1) : body
}

const isObject = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v)

export function checkSetup(text: string): Checked {
  const problems: Problem[] = []
  const summary: string[] = []
  let raw: unknown
  try {
    raw = JSON.parse(extractJson(text))
  } catch {
    return { setup: null, problems: [{ level: 'error', text: 'This is not a valid JSON file. Ask ChatGPT to give the answer again as a .json file.' }], summary }
  }
  if (!isObject(raw)) {
    return { setup: null, problems: [{ level: 'error', text: 'The file must be one JSON object with "institute" and/or "website" in it.' }], summary }
  }
  for (const key of Object.keys(raw)) {
    if (key !== 'institute' && key !== 'website') problems.push({ level: 'warning', text: `"${key}" is not used and was ignored.` })
  }

  // The institute
  const institute: Partial<UpdateBrandingInput> = {}
  const instituteRaw = isObject(raw.institute) ? raw.institute : {}
  if (raw.institute !== undefined && !isObject(raw.institute)) problems.push({ level: 'error', text: '"institute" must be an object.' })
  const limits: Record<string, number> = {}
  for (const f of FIELDS) if (f.path.startsWith('institute.') && f.limit) limits[f.path.slice('institute.'.length)] = f.limit
  const instituteNames: string[] = []
  for (const [key, value] of Object.entries(instituteRaw)) {
    if (!INSTITUTE_KEYS.has(key)) {
      problems.push({ level: 'warning', text: `institute.${key} is not used and was ignored.` })
      continue
    }
    if (value === null || value === '') continue
    if (typeof value !== 'string') {
      problems.push({ level: 'error', text: `institute.${key} must be text.` })
      continue
    }
    let v = value.trim()
    const max = limits[key]
    if (max && v.length > max) {
      problems.push({ level: 'warning', text: `institute.${key} was longer than ${max} characters and was shortened.` })
      v = v.slice(0, max)
    }
    if (key === 'primaryColor' && !COLOUR.test(v)) {
      problems.push({ level: 'error', text: 'institute.primaryColor must look like #1a2b3c.' })
      continue
    }
    if (key === 'contactEmail' && !EMAIL.test(v)) {
      problems.push({ level: 'error', text: 'institute.contactEmail is not a valid email address.' })
      continue
    }
    ;(institute as Record<string, string>)[key] = v
    instituteNames.push(LABELS.get(`institute.${key}`) ?? key)
  }
  if (instituteNames.length > 0) summary.push(`Institute: ${instituteNames.join(', ')}`)

  // The website
  const websiteRaw = isObject(raw.website) ? raw.website : {}
  if (raw.website !== undefined && !isObject(raw.website)) problems.push({ level: 'error', text: '"website" must be an object.' })
  const mapped: Record<string, unknown> = {}
  const provided: string[] = []
  const websiteNames: string[] = []
  for (const [key, value] of Object.entries(websiteRaw)) {
    if (!WEBSITE_KEYS.has(key)) {
      problems.push({ level: 'warning', text: `website.${key} is not used and was ignored.` })
      continue
    }
    if (value === null || value === undefined) continue
    const label = LABELS.get(`website.${key}`) ?? key
    switch (key) {
      case 'trainers':
        mapped.faculty = value
        provided.push('faculty')
        break
      case 'showCourseFee':
      case 'showCourseDuration': {
        const sub = key === 'showCourseFee' ? 'showFee' : 'showDuration'
        mapped.courseCards = { ...(isObject(mapped.courseCards) ? mapped.courseCards : {}), [sub]: value }
        provided.push(`courseCards.${sub}`)
        break
      }
      case 'hiddenSections': {
        const ids = Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
        const unknown = ids.filter((id) => !SECTION_IDS.has(id))
        if (unknown.length > 0) problems.push({ level: 'warning', text: `Unknown sections to hide were ignored: ${unknown.join(', ')}.` })
        mapped.hidden = ids.filter((id) => SECTION_IDS.has(id))
        provided.push('hidden')
        break
      }
      case 'social':
      case 'announcement': {
        if (!isObject(value)) {
          problems.push({ level: 'error', text: `website.${key} must be an object.` })
          continue
        }
        mapped[key] = value
        for (const sub of Object.keys(value)) provided.push(`${key}.${sub}`)
        break
      }
      default:
        mapped[key] = value
        provided.push(key)
    }
    websiteNames.push(label)

    // A list: say how many items were kept.
    if (Array.isArray(value)) {
      const parsed = parseSite(JSON.stringify({ [key === 'trainers' ? 'faculty' : key === 'hiddenSections' ? 'hidden' : key]: value }))
      const target = key === 'trainers' ? 'faculty' : key === 'hiddenSections' ? 'hidden' : key
      const kept = (parsed as unknown as Record<string, unknown[] | undefined>)[target]?.length ?? 0
      const limit = (SITE_LIMITS as Record<string, number>)[target === 'aboutPoints' ? 'points' : target]
      if (kept < value.length && key !== 'hiddenSections') {
        problems.push({
          level: 'warning',
          text: `${label}: ${kept} of ${value.length} items kept (the others were empty, incomplete${limit ? `, or over the limit of ${limit}` : ''}).`,
        })
      }
      const mark = websiteNames.indexOf(label)
      if (mark >= 0) websiteNames[mark] = `${label} (${kept})`
    }
  }
  if (websiteNames.length > 0) summary.push(`Website: ${websiteNames.join(', ')}`)

  if (summary.length === 0 && !problems.some((p) => p.level === 'error')) {
    problems.push({ level: 'error', text: 'Nothing to change: the file has no "institute" or "website" content.' })
  }
  if (problems.some((p) => p.level === 'error')) return { setup: null, problems, summary }
  const site = parseSite(JSON.stringify(mapped))
  return { setup: { institute, provided, site }, problems, summary }
}

// --- applying -----------------------------------------------------------------------------------------------------

const sameName = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

/** The current website with the parts that were in the file replaced. A person or partner who keeps their name keeps their photo. */
export function mergeSite(current: SiteConfig, setup: Setup): SiteConfig {
  const next: SiteConfig = { ...current, courseCards: { ...current.courseCards }, social: { ...current.social }, announcement: { ...current.announcement } }
  const incoming = setup.site
  for (const key of setup.provided) {
    if (key === 'faculty') {
      next.faculty = incoming.faculty.map((p) => ({ ...p, image: current.faculty.find((c) => sameName(c.name, p.name))?.image ?? '' }))
    } else if (key === 'partners') {
      next.partners = incoming.partners.map((p) => ({ ...p, image: current.partners.find((c) => sameName(c.name, p.name))?.image ?? '' }))
    } else if (key === 'testimonials') {
      next.testimonials = incoming.testimonials.map((p) => ({ ...p, image: current.testimonials.find((c) => sameName(c.name, p.name))?.image ?? '' }))
    } else if (key.startsWith('courseCards.')) {
      const sub = key.slice('courseCards.'.length) as 'showFee' | 'showDuration'
      next.courseCards[sub] = incoming.courseCards[sub]
    } else if (key.startsWith('social.')) {
      const sub = key.slice('social.'.length) as keyof SiteConfig['social']
      if (sub in next.social) next.social[sub] = incoming.social[sub]
    } else if (key.startsWith('announcement.')) {
      const sub = key.slice('announcement.'.length) as keyof SiteConfig['announcement']
      if (sub in next.announcement) (next.announcement as unknown as Record<string, unknown>)[sub] = incoming.announcement[sub]
    } else if (key in incoming) {
      ;(next as unknown as Record<string, unknown>)[key] = (incoming as unknown as Record<string, unknown>)[key]
    }
  }
  return next
}

/** The institute fields the update needs, with the file's values on top of what is saved now. */
export function mergeInstitute(
  current: Pick<UpdateBrandingInput, 'name' | 'tagline' | 'primaryColor' | 'contactEmail' | 'contactPhone' | 'website' | 'address' | 'signatoryName' | 'signatoryTitle'>,
  incoming: Partial<UpdateBrandingInput>,
): UpdateBrandingInput {
  return { ...current, ...incoming }
}
