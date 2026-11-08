import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, ExternalLink, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { updateUiConfig } from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ABOUT_POINTS, DEFAULT_ABOUT_INTRO, DEFAULT_HERO_BADGE, DEFAULT_HERO_TEXT, FEATURES, STEPS } from '@/pages/home/home-content'
import { DEFAULT_SITE, SITE_LIMITS, SITE_SECTIONS, type SiteConfig, parseUiConfig } from '@/lib/ui-config'
import { BRANDING_KEY, useBranding } from '@/lib/use-branding'

const SERVER_LIMIT = 20000

/** What the editor shows: every list filled with what the page uses today, so there is something to edit. */
function toForm(site: SiteConfig) {
  return {
    ...site,
    stats: site.stats ?? [],
    aboutPoints: site.aboutPoints ?? [...ABOUT_POINTS],
    features: site.features ?? FEATURES.map(({ title, text }) => ({ title, text })),
    steps: site.steps ?? STEPS.map(({ title, text }) => ({ title, text })),
  }
}
type Form = ReturnType<typeof toForm>

function move<T>(list: T[], index: number, by: -1 | 1): T[] {
  const target = index + by
  if (target < 0 || target >= list.length) return list
  const next = [...list]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
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

function Field({ id, label, value, max, onChange, hint }: { id: string; label: string; value: string; max: number; onChange: (v: string) => void; hint?: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} maxLength={max} value={value} onChange={(e) => onChange(e.target.value)} />
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

/** Replaces one entry of a list, returning a new list. */
function patchAt<T>(list: T[], index: number, patch: Partial<T>): T[] {
  return list.map((item, n) => (n === index ? { ...item, ...patch } : item))
}

export function WebsiteTab() {
  const { branding } = useBranding()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<Form>(toForm(DEFAULT_SITE))
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    if (branding && !loaded) {
      setForm(toForm(parseUiConfig(branding.uiConfig).site))
      setLoaded(true)
    }
  }, [branding, loaded])

  function build(site: SiteConfig) {
    return JSON.stringify({ ...parseUiConfig(branding?.uiConfig), site })
  }
  const size = build(form).length

  const save = useMutation({
    mutationFn: (site: SiteConfig) => updateUiConfig(build(site)),
    onSuccess: (updated) => {
      queryClient.setQueryData(BRANDING_KEY, updated)
      toast.success('Saved. The website shows it as soon as the page loads.')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save.')),
  })

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((prev) => ({ ...prev, [key]: value }))

  function itemEditor(key: 'features' | 'steps', limit: number, noun: string) {
    const list = form[key]
    return (
      <div className="flex flex-col gap-3">
        {list.map((item, i) => (
          <div key={i} className="flex items-start gap-2 rounded-lg border p-3">
            <div className="grid flex-1 gap-2">
              <Input aria-label={`${noun} ${i + 1} title`} placeholder="Title" maxLength={60} value={item.title} onChange={(e) => set(key, patchAt(list, i, { title: e.target.value }))} />
              <Input aria-label={`${noun} ${i + 1} text`} placeholder="One or two lines" maxLength={200} value={item.text} onChange={(e) => set(key, patchAt(list, i, { text: e.target.value }))} />
            </div>
            <RowButtons index={i} count={list.length} onMove={(by) => set(key, move(list, i, by))} onRemove={() => set(key, list.filter((_, n) => n !== i))} />
          </div>
        ))}
        <Button type="button" variant="outline" className="self-start" disabled={list.length >= limit} onClick={() => set(key, [...list, { title: '', text: '' }])}>
          <Plus /> Add {noun.toLowerCase()}
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          The words on the public front page. The institute name, address, phone and email come from the Institute tab, and courses appear
          from the course list.
        </p>
        <Button asChild variant="outline" size="sm">
          <a href="/?preview=1" target="_blank" rel="noreferrer">
            Open the website <ExternalLink />
          </a>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Top of the page</CardTitle>
          <CardDescription>Leave a box empty to keep the standard wording.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          <Field id="hero-badge" label="Small label" value={form.heroBadge} max={40} onChange={(v) => set('heroBadge', v)} hint={`Standard: ${DEFAULT_HERO_BADGE}`} />
          <Field id="hero-title" label="Headline under the institute name" value={form.heroTitle} max={80} onChange={(v) => set('heroTitle', v)} hint="Standard: the tagline from the Institute tab." />
          <Field id="hero-text" label="Sentence below" value={form.heroText} max={240} onChange={(v) => set('heroText', v)} hint={`Standard: ${DEFAULT_HERO_TEXT}`} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Figures</CardTitle>
          <CardDescription>
            Up to {SITE_LIMITS.stats}, such as &quot;500+&quot; and &quot;Students trained&quot;. Only put real numbers here. With none, the page shows its automatic figures.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {form.stats.map((stat, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input aria-label={`Figure ${i + 1} value`} className="w-28" placeholder="500+" maxLength={12} value={stat.value} onChange={(e) => set('stats', patchAt(form.stats, i, { value: e.target.value }))} />
              <Input aria-label={`Figure ${i + 1} label`} placeholder="Students trained" maxLength={30} value={stat.label} onChange={(e) => set('stats', patchAt(form.stats, i, { label: e.target.value }))} />
              <RowButtons index={i} count={form.stats.length} onMove={(by) => set('stats', move(form.stats, i, by))} onRemove={() => set('stats', form.stats.filter((_, n) => n !== i))} />
            </div>
          ))}
          <Button type="button" variant="outline" className="self-start" disabled={form.stats.length >= SITE_LIMITS.stats} onClick={() => set('stats', [...form.stats, { value: '', label: '' }])}>
            <Plus /> Add figure
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">About</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <Field id="about-intro" label="Introduction" value={form.aboutIntro} max={200} onChange={(v) => set('aboutIntro', v)} hint={`Standard: ${DEFAULT_ABOUT_INTRO}`} />
          <Label>Points</Label>
          {form.aboutPoints.map((point, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input aria-label={`Point ${i + 1}`} maxLength={160} value={point} onChange={(e) => set('aboutPoints', form.aboutPoints.map((p, n) => (n === i ? e.target.value : p)))} />
              <RowButtons index={i} count={form.aboutPoints.length} onMove={(by) => set('aboutPoints', move(form.aboutPoints, i, by))} onRemove={() => set('aboutPoints', form.aboutPoints.filter((_, n) => n !== i))} />
            </div>
          ))}
          <Button type="button" variant="outline" className="self-start" disabled={form.aboutPoints.length >= SITE_LIMITS.points} onClick={() => set('aboutPoints', [...form.aboutPoints, ''])}>
            <Plus /> Add point
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Why learn with us</CardTitle>
          <CardDescription>Icons are chosen automatically in order. Up to {SITE_LIMITS.features} cards.</CardDescription>
        </CardHeader>
        <CardContent>{itemEditor('features', SITE_LIMITS.features, 'Card')}</CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">How it works</CardTitle>
          <CardDescription>Up to {SITE_LIMITS.steps} numbered steps.</CardDescription>
        </CardHeader>
        <CardContent>{itemEditor('steps', SITE_LIMITS.steps, 'Step')}</CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Student feedback</CardTitle>
          <CardDescription>
            Only add what a student really said, with their permission. With none, this section is not shown. Up to {SITE_LIMITS.testimonials}.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {form.testimonials.map((t, i) => (
            <div key={i} className="flex items-start gap-2 rounded-lg border p-3">
              <div className="grid flex-1 gap-2">
                <Input aria-label={`Feedback ${i + 1} name`} placeholder="Name" maxLength={60} value={t.name} onChange={(e) => set('testimonials', patchAt(form.testimonials, i, { name: e.target.value }))} />
                <Input aria-label={`Feedback ${i + 1} course or job`} placeholder="Course or job, e.g. Java Full Stack" maxLength={80} value={t.role} onChange={(e) => set('testimonials', patchAt(form.testimonials, i, { role: e.target.value }))} />
                <Input aria-label={`Feedback ${i + 1} text`} placeholder="What they said" maxLength={300} value={t.text} onChange={(e) => set('testimonials', patchAt(form.testimonials, i, { text: e.target.value }))} />
              </div>
              <RowButtons index={i} count={form.testimonials.length} onMove={(by) => set('testimonials', move(form.testimonials, i, by))} onRemove={() => set('testimonials', form.testimonials.filter((_, n) => n !== i))} />
            </div>
          ))}
          <Button type="button" variant="outline" className="self-start" disabled={form.testimonials.length >= SITE_LIMITS.testimonials} onClick={() => set('testimonials', [...form.testimonials, { name: '', role: '', text: '' }])}>
            <Plus /> Add feedback
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Sections shown</CardTitle>
          <CardDescription>Untick a section to leave it off the page.</CardDescription>
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
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        <Button disabled={save.isPending || size > SERVER_LIMIT} onClick={() => save.mutate(form)}>
          {save.isPending ? 'Saving...' : 'Save'}
        </Button>
        <Button variant="outline" onClick={() => setForm(toForm(DEFAULT_SITE))}>
          Back to the standard wording
        </Button>
        <span className={`text-xs ${size > SERVER_LIMIT ? 'text-destructive' : 'text-muted-foreground'}`}>
          {size > SERVER_LIMIT
            ? `Too long by ${size - SERVER_LIMIT} characters. Shorten some text.`
            : `${Math.round((size / SERVER_LIMIT) * 100)}% of the space used (shared with the portal settings)`}
        </span>
      </div>
    </div>
  )
}
