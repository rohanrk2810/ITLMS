import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, ChevronLeft, Plus, Trash2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { assetUrl, removeBrandingImage, updateUiConfig, uploadBrandingImage } from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import type { Role } from '@/api/types'
import { NAV_ITEMS } from '@/components/nav-items'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { BrandingPage } from '@/pages/branding-page'
import { WebsiteTab } from '@/pages/website-tab'
import { BRANDING_KEY, useBranding } from '@/lib/use-branding'
import {
  ALWAYS_SHOWN,
  DARK_STYLES,
  DASHBOARD_BLOCKS,
  DEFAULT_UI_CONFIG,
  LIGHT_STYLES,
  type DefaultMode,
  type LookStyle,
  type RoleMenu,
  type UiConfig,
  parseUiConfig,
} from '@/lib/ui-config'

const ROLES: Role[] = ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT', 'PLACEMENT', 'FINANCE']
const RADII = [
  { label: 'Square', value: 0 },
  { label: 'Slight', value: 0.3 },
  { label: 'Normal', value: 0.625 },
  { label: 'Round', value: 1 },
  { label: 'Very round', value: 1.4 },
]
const WORD_SUGGESTIONS = ['Student', 'Batch', 'Course', 'Trainer', 'Admission', 'Fees']

function LookPicker({ label, styles, value, onChange }: { label: string; styles: Record<string, LookStyle>; value: string; onChange: (id: string) => void }) {
  return (
    <div className="flex flex-col gap-2 sm:col-span-3">
      <Label>{label}</Label>
      <div className="flex flex-wrap gap-2">
        {Object.entries(styles).map(([id, style]) => (
          <button
            key={id}
            type="button"
            onClick={() => onChange(id)}
            className={`flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm ${value === id ? 'border-primary ring-2 ring-primary/40' : ''}`}
          >
            <span className="size-4 rounded-full border" style={{ background: style.swatch }} />
            {style.label}
          </button>
        ))}
      </div>
    </div>
  )
}

const selectClass = 'h-9 rounded-md border bg-transparent px-3 text-sm'

const TABS = [
  { id: 'institute', label: 'Institute' },
  { id: 'portal', label: 'Portal' },
  { id: 'website', label: 'Website' },
] as const

/** ADMIN: the institute's identity (name, logo, signatory) and the portal's look, in one place. */
export function CustomizePage() {
  const [params, setParams] = useSearchParams()
  const requested = params.get('tab')
  const tab = requested === 'portal' || requested === 'website' ? requested : 'institute'
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <Link to="/app" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ChevronLeft className="size-4" /> Dashboard
        </Link>
        <h1 className="text-2xl font-semibold">Customize</h1>
      </div>
      <div role="tablist" className="flex gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setParams(t.id === 'institute' ? {} : { tab: t.id }, { replace: true })}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === t.id ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'institute' ? <BrandingPage /> : tab === 'website' ? <WebsiteTab /> : <PortalTab />}
    </div>
  )
}

function PortalTab() {
  const { branding } = useBranding()
  const queryClient = useQueryClient()
  const [cfg, setCfg] = useState<UiConfig>(DEFAULT_UI_CONFIG)
  const [role, setRole] = useState<Role>('STUDENT')
  const [loaded, setLoaded] = useState(false)

  // Load once from the server; later edits stay local until saved.
  useEffect(() => {
    if (branding && !loaded) {
      setCfg(parseUiConfig(branding.uiConfig))
      setLoaded(true)
    }
  }, [branding, loaded])

  const image = useMutation({
    mutationFn: ({ file }: { file: File | null }) =>
      file ? uploadBrandingImage('app-background', file) : removeBrandingImage('app-background'),
    onSuccess: (updated) => {
      queryClient.setQueryData(BRANDING_KEY, updated)
      toast.success('Background picture updated.')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not update the picture.')),
  })

  const save = useMutation({
    mutationFn: (next: UiConfig) => updateUiConfig(JSON.stringify(next)),
    onSuccess: (updated) => {
      queryClient.setQueryData(BRANDING_KEY, updated)
      toast.success('Saved. Everyone sees the change when their page loads.')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save.')),
  })

  const items = NAV_ITEMS.filter((item) => item.roles.includes(role))
  const menu: RoleMenu = cfg.menus[role] ?? {}
  const rank = (to: string) => {
    const i = menu.order?.indexOf(to) ?? -1
    return i === -1 ? Number.MAX_SAFE_INTEGER : i
  }
  const ordered = [...items].sort((a, b) => rank(a.to) - rank(b.to))

  function setMenu(patch: Partial<RoleMenu>) {
    setCfg({ ...cfg, menus: { ...cfg.menus, [role]: { ...menu, ...patch } } })
  }

  function move(index: number, by: -1 | 1) {
    const order = ordered.map((i) => i.to)
    const target = index + by
    if (target < 0 || target >= order.length) return
    ;[order[index], order[target]] = [order[target], order[index]]
    setMenu({ order })
  }

  function toggleHidden(to: string, shown: boolean) {
    const hidden = new Set(menu.hidden ?? [])
    if (shown) hidden.delete(to)
    else hidden.add(to)
    setMenu({ hidden: [...hidden] })
  }

  function setLabel(to: string, value: string) {
    const labels = { ...(menu.labels ?? {}) }
    if (value.trim()) labels[to] = value
    else delete labels[to]
    setMenu({ labels })
  }

  function toggleBlock(id: string, shown: boolean) {
    const hidden = new Set(cfg.dashboard.hidden)
    if (shown) hidden.delete(id)
    else hidden.add(id)
    setCfg({ ...cfg, dashboard: { hidden: [...hidden] } })
  }

  const terms = Object.entries(cfg.terms)
  function setTerms(next: [string, string][]) {
    setCfg({ ...cfg, terms: Object.fromEntries(next) })
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <p className="text-muted-foreground">
        Look, menus, dashboard, login page and wording. The logo and the login background are in the Institute tab.
      </p>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Theme</CardTitle>
          <CardDescription>A person who picks light or dark with the toggle keeps their own choice.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-3">
          <div className="flex flex-col gap-2 sm:col-span-3">
            <Label htmlFor="primary">Main colour (buttons, highlights)</Label>
            <div className="flex items-center gap-2">
              <input
                id="primary"
                type="color"
                className="h-9 w-14 cursor-pointer rounded-md border bg-transparent p-1"
                value={cfg.theme.primary || '#f2661a'}
                onChange={(e) => setCfg({ ...cfg, theme: { ...cfg.theme, primary: e.target.value } })}
              />
              <span className="text-sm text-muted-foreground">{cfg.theme.primary || 'Original orange'}</span>
              {cfg.theme.primary && (
                <Button type="button" size="sm" variant="ghost" onClick={() => setCfg({ ...cfg, theme: { ...cfg.theme, primary: '' } })}>
                  Back to original
                </Button>
              )}
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:col-span-3">
            <Label htmlFor="bgimage">Background picture (all pages)</Label>
            <div className="flex flex-wrap items-center gap-3">
              {branding?.appBackgroundUrl && (
                <img src={assetUrl(branding.appBackgroundUrl) ?? ''} alt="" className="h-14 w-24 rounded-md border object-cover" />
              )}
              <input
                id="bgimage"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) image.mutate({ file })
                  e.target.value = ''
                }}
              />
              {branding?.appBackgroundUrl && (
                <Button type="button" size="sm" variant="ghost" onClick={() => image.mutate({ file: null })}>
                  Remove picture
                </Button>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              The picture is saved as soon as you choose it. The colour above and the looks below stay over it; make it lighter or
              heavier here so text stays easy to read. Cards keep their solid colour; the side menu follows the same setting.
            </p>
            <Label htmlFor="bgstrength">Colour over the picture: {cfg.theme.bgStrength}%</Label>
            <input
              id="bgstrength"
              type="range"
              min={40}
              max={100}
              step={5}
              value={cfg.theme.bgStrength}
              onChange={(e) => setCfg({ ...cfg, theme: { ...cfg.theme, bgStrength: Number(e.target.value) } })}
            />
          </div>
          <LookPicker
            label="Dark mode look"
            styles={DARK_STYLES}
            value={cfg.theme.dark}
            onChange={(dark) => setCfg({ ...cfg, theme: { ...cfg.theme, dark } })}
          />
          <LookPicker
            label="Light mode look"
            styles={LIGHT_STYLES}
            value={cfg.theme.light}
            onChange={(light) => setCfg({ ...cfg, theme: { ...cfg.theme, light } })}
          />
          <div className="flex flex-col gap-2">
            <Label htmlFor="mode">Default mode</Label>
            <select
              id="mode"
              className={selectClass}
              value={cfg.theme.defaultMode}
              onChange={(e) => setCfg({ ...cfg, theme: { ...cfg.theme, defaultMode: e.target.value as DefaultMode } })}
            >
              <option value="system">Follow the device</option>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="radius">Corners</Label>
            <select
              id="radius"
              className={selectClass}
              value={cfg.theme.radius}
              onChange={(e) => setCfg({ ...cfg, theme: { ...cfg.theme, radius: Number(e.target.value) } })}
            >
              {RADII.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="font">Text size: {cfg.theme.fontScale}%</Label>
            <input
              id="font"
              type="range"
              min={85}
              max={125}
              step={5}
              value={cfg.theme.fontScale}
              onChange={(e) => setCfg({ ...cfg, theme: { ...cfg.theme, fontScale: Number(e.target.value) } })}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Menus</CardTitle>
          <CardDescription>
            Choose a role, then which items it sees, in what order and under what name. The dashboard and this page always stay.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <select aria-label="Role" className={`${selectClass} w-48`} value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
          <ul className="flex flex-col gap-2">
            {ordered.map((item, index) => {
              const locked = ALWAYS_SHOWN.includes(item.to)
              return (
                <li key={item.to} className="flex items-center gap-2">
                  <Checkbox
                    aria-label={`Show ${item.label}`}
                    checked={locked || !menu.hidden?.includes(item.to)}
                    disabled={locked}
                    onCheckedChange={(v) => toggleHidden(item.to, v === true)}
                  />
                  <Input
                    aria-label={`Name for ${item.label}`}
                    className="max-w-56"
                    placeholder={item.label}
                    value={menu.labels?.[item.to] ?? ''}
                    onChange={(e) => setLabel(item.to, e.target.value)}
                  />
                  <span className="hidden text-xs text-muted-foreground sm:inline">{item.to}</span>
                  <Button type="button" size="icon" variant="ghost" aria-label="Move up" disabled={index === 0} onClick={() => move(index, -1)}>
                    <ArrowUp className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label="Move down"
                    disabled={index === ordered.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    <ArrowDown className="size-4" />
                  </Button>
                </li>
              )
            })}
          </ul>
          <p className="text-xs text-muted-foreground">Tick = shown. Leave a name empty to keep the original.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Dashboard</CardTitle>
          <CardDescription>Untick what should not appear on anyone&apos;s dashboard.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2 sm:grid-cols-2">
          {DASHBOARD_BLOCKS.map((block) => (
            <label key={block.id} className="flex items-center gap-2 text-sm">
              <Checkbox checked={!cfg.dashboard.hidden.includes(block.id)} onCheckedChange={(v) => toggleBlock(block.id, v === true)} />
              {block.label}
            </label>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Login page</CardTitle>
          <CardDescription>Text shown above and below the sign-in form.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-col gap-2">
            <Label htmlFor="welcome">Welcome message</Label>
            <Input
              id="welcome"
              maxLength={200}
              value={cfg.login.welcome}
              onChange={(e) => setCfg({ ...cfg, login: { ...cfg.login, welcome: e.target.value } })}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="footer">Footer</Label>
            <Input
              id="footer"
              maxLength={200}
              value={cfg.login.footer}
              onChange={(e) => setCfg({ ...cfg, login: { ...cfg.login, footer: e.target.value } })}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Words</CardTitle>
          <CardDescription>
            Show a word differently on every page, for example Student &rarr; Learner. Plurals follow. It changes what is read on
            screen, not typed text, code or your data.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {terms.map(([from, to], i) => (
            <div key={i} className="flex items-center gap-2">
              <Input
                aria-label="Word"
                className="max-w-40"
                value={from}
                onChange={(e) => setTerms(terms.map((t, j): [string, string] => (j === i ? [e.target.value, t[1]] : t)))}
              />
              <span className="text-muted-foreground">&rarr;</span>
              <Input
                aria-label="Shown as"
                className="max-w-40"
                value={to}
                onChange={(e) => setTerms(terms.map((t, j): [string, string] => (j === i ? [t[0], e.target.value] : t)))}
              />
              <Button type="button" size="icon" variant="ghost" aria-label="Remove" onClick={() => setTerms(terms.filter((_, j) => j !== i))}>
                <Trash2 className="size-4" />
              </Button>
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" size="sm" variant="outline" onClick={() => setTerms([...terms, ['', '']])}>
              <Plus className="size-4" /> Add a word
            </Button>
            {WORD_SUGGESTIONS.filter((w) => !(w in cfg.terms)).map((w) => (
              <Button key={w} type="button" size="sm" variant="ghost" onClick={() => setTerms([...terms, [w, '']])}>
                {w}
              </Button>
            ))}
          </div>
        </CardContent>
      </Card>

      <div className="flex gap-2">
        <Button disabled={save.isPending} onClick={() => save.mutate(cfg)}>
          {save.isPending ? 'Saving...' : 'Save'}
        </Button>
        <Button variant="outline" onClick={() => setCfg({ ...DEFAULT_UI_CONFIG, site: cfg.site })}>
          Reset everything
        </Button>
      </div>
    </div>
  )
}
