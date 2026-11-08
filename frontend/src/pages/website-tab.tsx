import { type ReactNode, useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, ExternalLink, ImagePlus, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { deleteSiteImage, getSiteConfig, updateSiteConfig, uploadSiteImage } from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import { getPublicCourses } from '@/api/public-site'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { shrinkImage } from '@/lib/image'
import {
  DEFAULT_SITE,
  type Lang,
  SERVER_LIMIT,
  SITE_LIMITS,
  SITE_SECTIONS,
  type SiteConfig,
  type T,
  emptyT,
  parseSite,
  pick,
  siteImageUrl,
} from '@/lib/site-config'
import {
  DEFAULT_ABOUT_INTRO,
  DEFAULT_ABOUT_POINTS,
  DEFAULT_FEATURES,
  DEFAULT_HERO_BADGE,
  DEFAULT_HERO_TEXT,
  DEFAULT_HERO_TITLE,
  DEFAULT_STEPS,
  DEFAULT_WHATSAPP_MESSAGE,
} from '@/pages/home/home-content'

/** What the editor shows: every list filled with what the page uses today, so there is something to edit. */
function toForm(site: SiteConfig): SiteConfig {
  return {
    ...site,
    stats: site.stats ?? [],
    aboutPoints: site.aboutPoints ?? DEFAULT_ABOUT_POINTS.map((p) => ({ ...p })),
    features: site.features ?? DEFAULT_FEATURES.map((f) => ({ title: { ...f.title }, text: { ...f.text } })),
    steps: site.steps ?? DEFAULT_STEPS.map((f) => ({ title: { ...f.title }, text: { ...f.text } })),
  }
}

function move<X>(list: X[], index: number, by: -1 | 1): X[] {
  const target = index + by
  if (target < 0 || target >= list.length) return list
  const next = [...list]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

const OTHER: Record<Lang, Lang> = { en: 'mr', mr: 'en' }

/** One text, edited in the chosen language; the other language is shown as a hint so the two stay in step. */
function TField({
  label,
  value,
  lang,
  max,
  onChange,
  hint,
  multiline,
}: {
  label: string
  value: T
  lang: Lang
  max: number
  onChange: (v: T) => void
  hint?: string
  multiline?: boolean
}) {
  const id = `f-${label.replace(/\W+/g, '-')}-${Math.abs(hashOf(label + max))}`
  const common = {
    id,
    maxLength: max,
    value: value[lang],
    placeholder: value[OTHER[lang]] ? `${OTHER[lang] === 'en' ? 'English' : 'मराठी'}: ${value[OTHER[lang]]}`.slice(0, 80) : undefined,
    onChange: (e: { target: { value: string } }) => onChange({ ...value, [lang]: e.target.value }),
  }
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {multiline ? (
        <textarea rows={3} className="border-input rounded-md border bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50" {...common} />
      ) : (
        <Input {...common} />
      )}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

function hashOf(text: string): number {
  let h = 0
  for (const c of text) h = (h * 31 + c.charCodeAt(0)) | 0
  return h
}

function RowButtons({ index, count, onMove, onRemove }: { index: number; count: number; onMove: (by: -1 | 1) => void; onRemove: () => void }) {
  return (
    <div className="flex shrink-0 gap-1">
      <Button type="button" size="icon" variant="ghost" aria-label="Move up" disabled={index === 0} onClick={() => onMove(-1)}>
        <ArrowUp />
      </Button>
      <Button type="button" size="icon" variant="ghost" aria-label="Move down" disabled={index === count - 1} onClick={() => onMove(1)}>
        <ArrowDown />
      </Button>
      <Button type="button" size="icon" variant="ghost" aria-label="Remove" onClick={onRemove}>
        <Trash2 />
      </Button>
    </div>
  )
}

/** A list of cards the admin can add to, remove from and reorder. */
function List<I>({
  items,
  onChange,
  make,
  limit,
  noun,
  children,
}: {
  items: I[]
  onChange: (next: I[]) => void
  make: () => I
  limit: number
  noun: string
  children: (item: I, patch: (p: Partial<I>) => void, index: number) => ReactNode
}) {
  return (
    <div className="flex flex-col gap-3">
      {items.map((item, i) => (
        <div key={i} className="flex items-start gap-2 rounded-lg border p-3">
          <div className="grid min-w-0 flex-1 gap-2">{children(item, (p) => onChange(items.map((x, n) => (n === i ? { ...x, ...p } : x))), i)}</div>
          <RowButtons index={i} count={items.length} onMove={(by) => onChange(move(items, i, by))} onRemove={() => onChange(items.filter((_, n) => n !== i))} />
        </div>
      ))}
      <Button type="button" variant="outline" className="self-start" disabled={items.length >= limit} onClick={() => onChange([...items, make()])}>
        <Plus /> Add {noun}
      </Button>
    </div>
  )
}

/** Picks a picture, shrinks it, uploads it, and reports its id. A replaced picture is deleted when the page is saved. */
function ImageField({
  label,
  id,
  onChange,
  onDrop,
  maxSide = 1600,
  keepAlpha = false,
}: {
  label: string
  id: string
  onChange: (id: string) => void
  onDrop: (id: string) => void
  maxSide?: number
  keepAlpha?: boolean
}) {
  const [busy, setBusy] = useState(false)
  async function choose(file: File | undefined) {
    if (!file) return
    setBusy(true)
    try {
      const blob = await shrinkImage(file, maxSide, keepAlpha)
      const saved = await uploadSiteImage(blob, file.name)
      if (id) onDrop(id)
      onChange(String(saved.id))
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not upload the picture.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="flex items-center gap-3">
      {id ? (
        <img src={siteImageUrl(id)} alt="" className="size-14 rounded-md border object-cover" />
      ) : (
        <span className="flex size-14 items-center justify-center rounded-md border border-dashed text-muted-foreground">
          <ImagePlus className="size-5" aria-hidden="true" />
        </span>
      )}
      <label className="cursor-pointer">
        <span className="inline-flex h-8 items-center rounded-md border px-3 text-sm hover:bg-muted">
          {busy ? 'Uploading...' : id ? `Change ${label}` : `Add ${label}`}
        </span>
        <input type="file" accept="image/png,image/jpeg,image/webp" className="sr-only" disabled={busy} onChange={(e) => void choose(e.target.files?.[0])} />
      </label>
      {id && (
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={() => {
            onDrop(id)
            onChange('')
          }}
        >
          Remove
        </Button>
      )}
    </div>
  )
}

function Plain({ label, value, onChange, max, placeholder, hint }: { label: string; value: string; onChange: (v: string) => void; max: number; placeholder?: string; hint?: string }) {
  const id = `p-${label.replace(/\W+/g, '-')}`
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} maxLength={max} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

export function WebsiteTab() {
  const queryClient = useQueryClient()
  const stored = useQuery({ queryKey: ['site-config'], queryFn: getSiteConfig, staleTime: 0 })
  const courses = useQuery({ queryKey: ['public-courses'], queryFn: getPublicCourses, staleTime: 5 * 60_000 })
  const [form, setForm] = useState<SiteConfig>(toForm(DEFAULT_SITE))
  const [loaded, setLoaded] = useState(false)
  const [editLang, setLang] = useState<Lang>('en')
  const [dropped, setDropped] = useState<string[]>([])

  useEffect(() => {
    if (stored.data && !loaded) {
      setForm(toForm(parseSite(stored.data.config)))
      setLoaded(true)
    }
  }, [stored.data, loaded])

  const json = JSON.stringify(form)
  const size = json.length

  const save = useMutation({
    mutationFn: () => updateSiteConfig(json),
    onSuccess: async (updated) => {
      queryClient.setQueryData(['site-config'], updated)
      // Pictures taken off the page are removed from the server only now that the page no longer points at them.
      await Promise.allSettled(dropped.map((id) => deleteSiteImage(id)))
      setDropped([])
      toast.success('Saved. The website shows it as soon as the page loads.')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save.')),
  })

  // With the language button off the site is English only, so there is nothing to switch.
  const lang: Lang = form.languageSwitch ? editLang : 'en'
  const set = <K extends keyof SiteConfig>(key: K, value: SiteConfig[K]) => setForm((prev) => ({ ...prev, [key]: value }))
  const drop = (id: string) => setDropped((prev) => (prev.includes(id) ? prev : [...prev, id]))
  const list = courses.data?.content ?? []
  const lbl = lang === 'en' ? 'English' : 'मराठी'

  if (stored.isLoading) return <p className="text-sm text-muted-foreground">Loading...</p>

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-xl text-sm text-muted-foreground">
          The public front page. The institute name, address, phone and email come from the Institute tab, and courses appear from the course
          list. Pictures are shrunk and uploaded as soon as you pick them; the page changes when you press Save.
        </p>
        <Button asChild variant="outline" size="sm">
          <a href="/?preview=1" target="_blank" rel="noreferrer">
            Open the website <ExternalLink />
          </a>
        </Button>
      </div>

      {form.languageSwitch && (
        <div className="sticky top-0 z-10 flex flex-wrap items-center gap-3 rounded-lg border bg-background/95 p-3 backdrop-blur">
          <span className="text-sm font-medium">Writing in:</span>
          {(['en', 'mr'] as const).map((l) => (
            <Button key={l} type="button" size="sm" variant={lang === l ? 'default' : 'outline'} onClick={() => setLang(l)}>
              {l === 'en' ? 'English' : 'मराठी'}
            </Button>
          ))}
          <span className="text-xs text-muted-foreground">Visitors can switch language. Whatever you leave empty shows the standard wording.</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Announcement bar</CardTitle>
          <CardDescription>A coloured strip above everything, e.g. &quot;New batch starts on 15th&quot;.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.announcement.enabled} onCheckedChange={(on) => set('announcement', { ...form.announcement, enabled: on === true })} />
            Show it
          </label>
          <TField label={`Text (${lbl})`} value={form.announcement.text} lang={lang} max={160} onChange={(v) => set('announcement', { ...form.announcement, text: v })} />
          <Plain label="Link (optional)" value={form.announcement.link} max={300} placeholder="https://..." onChange={(v) => set('announcement', { ...form.announcement, link: v })} hint="A web address, or a page of this site such as /register." />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Top of the page</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3">
          <TField label={`Small label (${lbl})`} value={form.heroBadge} lang={lang} max={40} onChange={(v) => set('heroBadge', v)} hint={`Standard: ${pick(DEFAULT_HERO_BADGE, lang)}`} />
          <TField label={`Headline under the institute name (${lbl})`} value={form.heroTitle} lang={lang} max={80} onChange={(v) => set('heroTitle', v)} hint={`Standard: the tagline from the Institute tab, else "${pick(DEFAULT_HERO_TITLE, lang)}"`} />
          <TField label={`Sentence below (${lbl})`} value={form.heroText} lang={lang} max={240} onChange={(v) => set('heroText', v)} hint={`Standard: ${pick(DEFAULT_HERO_TEXT, lang)}`} />
          <Label>Hero picture (optional)</Label>
          <ImageField label="picture" id={form.heroImage} onChange={(id) => set('heroImage', id)} onDrop={drop} />
          <p className="text-xs text-muted-foreground">With a picture the top is split: words on the left, the picture on the right. Without it, the login background is shown faintly behind the words.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Figures</CardTitle>
          <CardDescription>Up to {SITE_LIMITS.stats}, such as &quot;500+&quot; and &quot;Students trained&quot;. Only real numbers. With none, the page shows its automatic figures.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.stats ?? []} onChange={(v) => set('stats', v)} make={() => ({ value: '', label: emptyT() })} limit={SITE_LIMITS.stats} noun="figure">
            {(stat, patch, i) => (
              <div className="flex items-end gap-2">
                <Input aria-label={`Figure ${i + 1} value`} className="w-28" placeholder="500+" maxLength={12} value={stat.value} onChange={(e) => patch({ value: e.target.value })} />
                <div className="flex-1">
                  <TField label={`Figure ${i + 1} label (${lbl})`} value={stat.label} lang={lang} max={30} onChange={(v) => patch({ label: v })} />
                </div>
              </div>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">About</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <TField label={`Introduction (${lbl})`} value={form.aboutIntro} lang={lang} max={200} onChange={(v) => set('aboutIntro', v)} hint={`Standard: ${pick(DEFAULT_ABOUT_INTRO, lang)}`} />
          <List items={form.aboutPoints ?? []} onChange={(v) => set('aboutPoints', v)} make={emptyT} limit={SITE_LIMITS.points} noun="point">
            {(point, _patch, i) => (
              <TField label={`Point ${i + 1} (${lbl})`} value={point} lang={lang} max={160} onChange={(v) => set('aboutPoints', (form.aboutPoints ?? []).map((p, n) => (n === i ? v : p)))} />
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Courses on the page</CardTitle>
          <CardDescription>What each course card shows. Courses themselves come from the course list (published ones only).</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.courseCards.showDuration} onCheckedChange={(on) => set('courseCards', { ...form.courseCards, showDuration: on === true })} />
            Show the duration (hours)
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.courseCards.showFee} onCheckedChange={(on) => set('courseCards', { ...form.courseCards, showFee: on === true })} />
            Show the fee
          </label>
          {list.length === 0 ? (
            <p className="text-xs text-muted-foreground">No published courses yet, so there is nothing to attach a picture to.</p>
          ) : (
            list.map((course) => (
              <div key={course.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3">
                <span className="text-sm font-medium">{course.title}</span>
                <ImageField
                  label="picture"
                  id={form.courseCards.images[String(course.id)] ?? ''}
                  maxSide={1000}
                  onDrop={drop}
                  onChange={(id) => {
                    const images = { ...form.courseCards.images }
                    if (id) images[String(course.id)] = id
                    else delete images[String(course.id)]
                    set('courseCards', { ...form.courseCards, images })
                  }}
                />
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Why learn with us</CardTitle>
          <CardDescription>Icons are chosen automatically in order. Up to {SITE_LIMITS.features} cards.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.features ?? []} onChange={(v) => set('features', v)} make={() => ({ title: emptyT(), text: emptyT() })} limit={SITE_LIMITS.features} noun="card">
            {(item, patch, i) => (
              <>
                <TField label={`Card ${i + 1} title (${lbl})`} value={item.title} lang={lang} max={60} onChange={(v) => patch({ title: v })} />
                <TField label={`Card ${i + 1} text (${lbl})`} value={item.text} lang={lang} max={200} onChange={(v) => patch({ text: v })} />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">How it works</CardTitle>
          <CardDescription>Up to {SITE_LIMITS.steps} numbered steps.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.steps ?? []} onChange={(v) => set('steps', v)} make={() => ({ title: emptyT(), text: emptyT() })} limit={SITE_LIMITS.steps} noun="step">
            {(item, patch, i) => (
              <>
                <TField label={`Step ${i + 1} title (${lbl})`} value={item.title} lang={lang} max={60} onChange={(v) => patch({ title: v })} />
                <TField label={`Step ${i + 1} text (${lbl})`} value={item.text} lang={lang} max={200} onChange={(v) => patch({ text: v })} />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Photo gallery</CardTitle>
          <CardDescription>Your institute, classes and events. Up to {SITE_LIMITS.gallery} photos. With none, the section is not shown.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.gallery} onChange={(v) => set('gallery', v)} make={() => ({ image: '', caption: emptyT() })} limit={SITE_LIMITS.gallery} noun="photo">
            {(item, patch, i) => (
              <>
                <ImageField label="photo" id={item.image} onDrop={drop} onChange={(id) => patch({ image: id })} />
                <TField label={`Photo ${i + 1} caption (${lbl})`} value={item.caption} lang={lang} max={80} onChange={(v) => patch({ caption: v })} />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Trainers</CardTitle>
          <CardDescription>Photo, name and what they teach. Up to {SITE_LIMITS.faculty}. With none, the section is not shown.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.faculty} onChange={(v) => set('faculty', v)} make={() => ({ name: '', role: emptyT(), image: '' })} limit={SITE_LIMITS.faculty} noun="trainer">
            {(item, patch, i) => (
              <>
                <ImageField label="photo" id={item.image} maxSide={600} onDrop={drop} onChange={(id) => patch({ image: id })} />
                <Plain label={`Trainer ${i + 1} name`} value={item.name} max={60} onChange={(v) => patch({ name: v })} />
                <TField label={`Trainer ${i + 1} role (${lbl})`} value={item.role} lang={lang} max={80} onChange={(v) => patch({ role: v })} hint="For example: Java trainer, 8 years in the industry." />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Hiring partners</CardTitle>
          <CardDescription>Logos of companies where students work. Only companies that agree to be shown. Up to {SITE_LIMITS.partners}.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.partners} onChange={(v) => set('partners', v)} make={() => ({ name: '', image: '' })} limit={SITE_LIMITS.partners} noun="company">
            {(item, patch, i) => (
              <>
                <ImageField label="logo" id={item.image} maxSide={400} keepAlpha onDrop={drop} onChange={(id) => patch({ image: id })} />
                <Plain label={`Company ${i + 1} name`} value={item.name} max={60} onChange={(v) => patch({ name: v })} hint="Shown as text if there is no logo." />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Student feedback</CardTitle>
          <CardDescription>Only what a student really said, with their permission. Up to {SITE_LIMITS.testimonials}. With none, the section is not shown.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.testimonials} onChange={(v) => set('testimonials', v)} make={() => ({ name: '', role: emptyT(), text: emptyT(), image: '' })} limit={SITE_LIMITS.testimonials} noun="feedback">
            {(item, patch, i) => (
              <>
                <ImageField label="photo" id={item.image} maxSide={300} onDrop={drop} onChange={(id) => patch({ image: id })} />
                <Plain label={`Feedback ${i + 1} name`} value={item.name} max={60} onChange={(v) => patch({ name: v })} />
                <TField label={`Feedback ${i + 1} course or job (${lbl})`} value={item.role} lang={lang} max={80} onChange={(v) => patch({ role: v })} />
                <TField label={`Feedback ${i + 1} words (${lbl})`} value={item.text} lang={lang} max={300} multiline onChange={(v) => patch({ text: v })} />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Questions and answers</CardTitle>
          <CardDescription>What people often ask. Up to {SITE_LIMITS.faqs}. With none, the section is not shown.</CardDescription>
        </CardHeader>
        <CardContent>
          <List items={form.faqs} onChange={(v) => set('faqs', v)} make={() => ({ q: emptyT(), a: emptyT() })} limit={SITE_LIMITS.faqs} noun="question">
            {(item, patch, i) => (
              <>
                <TField label={`Question ${i + 1} (${lbl})`} value={item.q} lang={lang} max={160} onChange={(v) => patch({ q: v })} />
                <TField label={`Answer ${i + 1} (${lbl})`} value={item.a} lang={lang} max={600} multiline onChange={(v) => patch({ a: v })} />
              </>
            )}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Links, WhatsApp and map</CardTitle>
          <CardDescription>Leave a box empty to leave that link out.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2">
          <Plain label="WhatsApp number" value={form.social.whatsapp} max={20} placeholder="919876543210" onChange={(v) => set('social', { ...form.social, whatsapp: v })} hint="With the country code, no + or spaces." />
          <Plain label="Instagram" value={form.social.instagram} max={300} placeholder="https://instagram.com/..." onChange={(v) => set('social', { ...form.social, instagram: v })} />
          <Plain label="YouTube" value={form.social.youtube} max={300} placeholder="https://youtube.com/..." onChange={(v) => set('social', { ...form.social, youtube: v })} />
          <Plain label="Facebook" value={form.social.facebook} max={300} placeholder="https://facebook.com/..." onChange={(v) => set('social', { ...form.social, facebook: v })} />
          <Plain label="LinkedIn" value={form.social.linkedin} max={300} placeholder="https://linkedin.com/..." onChange={(v) => set('social', { ...form.social, linkedin: v })} />
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <Checkbox checked={form.whatsappButton} onCheckedChange={(on) => set('whatsappButton', on === true)} />
            Show a floating WhatsApp button (needs the WhatsApp number)
          </label>
          <div className="sm:col-span-2">
            <TField label={`First message the visitor sends (${lbl})`} value={form.whatsappMessage} lang={lang} max={200} onChange={(v) => set('whatsappMessage', v)} hint={`Standard: ${pick(DEFAULT_WHATSAPP_MESSAGE, lang)}`} />
          </div>
          <div className="sm:col-span-2">
            <Plain
              label="Google Map address"
              value={form.mapUrl}
              max={700}
              placeholder="https://www.google.com/maps/embed?pb=..."
              onChange={(v) => set('mapUrl', v.trim())}
              hint='In Google Maps: Share > Embed a map > copy the part inside src="...". Only Google Maps embed addresses are accepted.'
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Sections shown</CardTitle>
          <CardDescription>Untick a section to leave it off the page. Sections with nothing in them are left off anyway.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2 sm:grid-cols-2">
          {SITE_SECTIONS.map((section) => (
            <label key={section.id} className="flex items-center gap-2 text-sm">
              <Checkbox
                checked={!form.hidden.includes(section.id)}
                onCheckedChange={(on) => set('hidden', on === true ? form.hidden.filter((h) => h !== section.id) : [...form.hidden, section.id])}
              />
              {section.label}
            </label>
          ))}
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.languageSwitch} onCheckedChange={(on) => set('languageSwitch', on === true)} />
            Language button (English / मराठी) - off by default; when on, write each text in both languages
          </label>
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        <Button disabled={save.isPending || size > SERVER_LIMIT} onClick={() => save.mutate()}>
          {save.isPending ? 'Saving...' : 'Save'}
        </Button>
        <Button
          variant="outline"
          onClick={() => {
            for (const id of collectImages(form)) drop(id)
            setForm(toForm(DEFAULT_SITE))
          }}
        >
          Back to the standard wording
        </Button>
        <span className={`text-xs ${size > SERVER_LIMIT ? 'text-destructive' : 'text-muted-foreground'}`}>
          {size > SERVER_LIMIT ? `Too long by ${size - SERVER_LIMIT} characters. Shorten some text.` : `${Math.round((size / SERVER_LIMIT) * 100)}% of the space used`}
        </span>
      </div>
    </div>
  )
}

/** Every picture id the page points at, so a reset can remove them from the server after saving. */
function collectImages(site: SiteConfig): string[] {
  return [
    site.heroImage,
    ...site.gallery.map((g) => g.image),
    ...site.faculty.map((f) => f.image),
    ...site.partners.map((p) => p.image),
    ...site.testimonials.map((t) => t.image),
    ...Object.values(site.courseCards.images),
  ].filter(Boolean)
}
