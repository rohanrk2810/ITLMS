import { type FormEvent, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import {
  type Branding,
  type UpdateBrandingInput,
  assetUrl,
  getBranding,
  removeBrandingImage,
  updateBranding,
  uploadBrandingImage,
} from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { BRANDING_KEY } from '@/lib/use-branding'

const EMPTY: UpdateBrandingInput = {
  name: '',
  tagline: '',
  primaryColor: '',
  contactEmail: '',
  contactPhone: '',
  website: '',
  address: '',
  signatoryName: '',
  signatoryTitle: '',
}

function toForm(b: Branding): UpdateBrandingInput {
  return {
    name: b.name,
    tagline: b.tagline ?? '',
    primaryColor: b.primaryColor ?? '',
    contactEmail: b.contactEmail ?? '',
    contactPhone: b.contactPhone ?? '',
    website: b.website ?? '',
    address: b.address ?? '',
    signatoryName: b.signatoryName ?? '',
    signatoryTitle: b.signatoryTitle ?? '',
  }
}

/** ADMIN: the institute's name, logo, colour and signatory. Everything else in the app reads from here. */
export function BrandingPage() {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: BRANDING_KEY, queryFn: getBranding })
  const [form, setForm] = useState<UpdateBrandingInput>(EMPTY)
  const loaded = useRef(false)

  useEffect(() => {
    if (query.data && !loaded.current) {
      loaded.current = true
      setForm(toForm(query.data))
    }
  }, [query.data])

  const apply = (next: Branding) => queryClient.setQueryData(BRANDING_KEY, next)

  const save = useMutation({
    mutationFn: () => updateBranding(form),
    onSuccess: (next) => {
      apply(next)
      toast.success('Branding saved. It now shows across the application.')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the branding.')),
  })

  const upload = useMutation({
    mutationFn: ({ kind, file }: { kind: 'logo' | 'favicon'; file: File }) => uploadBrandingImage(kind, file),
    onSuccess: (next) => {
      apply(next)
      toast.success('Image updated.')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not upload that image.')),
  })

  const remove = useMutation({
    mutationFn: (kind: 'logo' | 'favicon') => removeBrandingImage(kind),
    onSuccess: apply,
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not remove the image.')),
  })

  function set<K extends keyof UpdateBrandingInput>(key: K, value: string) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  const branding = query.data

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Link to="/app" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Dashboard
      </Link>
      <div>
        <h1 className="text-2xl font-semibold">Branding</h1>
        <p className="text-muted-foreground">
          The name and look of this institute. Changes appear on the sign-in page, the header, emails and certificates.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Institute</CardTitle>
            <CardDescription>Shown wherever the platform names itself.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label="Institute / platform name" className="sm:col-span-2">
              <Input value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={120} required />
            </Field>
            <Field label="Tagline" className="sm:col-span-2">
              <Input value={form.tagline ?? ''} onChange={(e) => set('tagline', e.target.value)} maxLength={200} />
            </Field>
            <Field label="Brand colour">
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  aria-label="Pick a brand colour"
                  className="h-9 w-12 cursor-pointer rounded border bg-transparent"
                  value={/^#[0-9a-fA-F]{6}$/.test(form.primaryColor ?? '') ? form.primaryColor! : '#f97316'}
                  onChange={(e) => set('primaryColor', e.target.value)}
                />
                <Input
                  value={form.primaryColor ?? ''}
                  onChange={(e) => set('primaryColor', e.target.value)}
                  placeholder="#f97316 (blank = default)"
                  maxLength={7}
                />
              </div>
            </Field>
            <Field label="Website">
              <Input value={form.website ?? ''} onChange={(e) => set('website', e.target.value)} maxLength={200} />
            </Field>
            <Field label="Contact email">
              <Input
                type="email"
                value={form.contactEmail ?? ''}
                onChange={(e) => set('contactEmail', e.target.value)}
                maxLength={160}
              />
            </Field>
            <Field label="Contact phone">
              <Input
                value={form.contactPhone ?? ''}
                onChange={(e) => set('contactPhone', e.target.value)}
                maxLength={30}
              />
            </Field>
            <Field label="Address" className="sm:col-span-2">
              <Input value={form.address ?? ''} onChange={(e) => set('address', e.target.value)} maxLength={400} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Authorised signatory</CardTitle>
            <CardDescription>Printed on certificates.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label="Name">
              <Input
                value={form.signatoryName ?? ''}
                onChange={(e) => set('signatoryName', e.target.value)}
                maxLength={120}
              />
            </Field>
            <Field label="Title">
              <Input
                value={form.signatoryTitle ?? ''}
                onChange={(e) => set('signatoryTitle', e.target.value)}
                maxLength={120}
              />
            </Field>
          </CardContent>
        </Card>

        <div>
          <Button type="submit" disabled={save.isPending || query.isPending}>
            {save.isPending ? 'Saving...' : 'Save branding'}
          </Button>
        </div>
      </form>

      <Card>
        <CardHeader>
          <CardTitle>Logo and favicon</CardTitle>
          <CardDescription>PNG, JPEG or WebP up to 1 MB (ICO also works for the favicon). SVG is not accepted.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-6 sm:grid-cols-2">
          <ImagePicker
            label="Logo"
            src={assetUrl(branding?.logoUrl ?? null)}
            busy={upload.isPending || remove.isPending}
            onPick={(file) => upload.mutate({ kind: 'logo', file })}
            onRemove={() => remove.mutate('logo')}
          />
          <ImagePicker
            label="Favicon"
            src={assetUrl(branding?.faviconUrl ?? null)}
            busy={upload.isPending || remove.isPending}
            onPick={(file) => upload.mutate({ kind: 'favicon', file })}
            onRemove={() => remove.mutate('favicon')}
          />
        </CardContent>
      </Card>
    </div>
  )
}

function Field({ label, className, children }: { label: string; className?: string; children: React.ReactNode }) {
  return (
    <div className={`flex flex-col gap-2 ${className ?? ''}`}>
      <Label>{label}</Label>
      {children}
    </div>
  )
}

function ImagePicker(props: {
  label: string
  src: string | null
  busy: boolean
  onPick: (file: File) => void
  onRemove: () => void
}) {
  const input = useRef<HTMLInputElement>(null)
  return (
    <div className="flex flex-col gap-3">
      <Label>{props.label}</Label>
      <div className="flex h-24 items-center justify-center rounded-md border bg-muted/40">
        {props.src ? (
          <img src={props.src} alt={`${props.label} preview`} className="max-h-20 max-w-full object-contain" />
        ) : (
          <span className="text-sm text-muted-foreground">Not set</span>
        )}
      </div>
      <input
        ref={input}
        type="file"
        accept="image/png,image/jpeg,image/webp,image/x-icon"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) {
            props.onPick(file)
          }
          e.target.value = ''
        }}
      />
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="outline" disabled={props.busy} onClick={() => input.current?.click()}>
          {props.src ? 'Replace' : 'Upload'}
        </Button>
        {props.src && (
          <Button type="button" size="sm" variant="ghost" disabled={props.busy} onClick={props.onRemove}>
            Remove
          </Button>
        )}
      </div>
    </div>
  )
}
