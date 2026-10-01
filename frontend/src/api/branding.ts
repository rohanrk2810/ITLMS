import { env } from '@/lib/env'

import { apiClient } from './client'

export interface Branding {
  name: string
  tagline: string | null
  primaryColor: string | null
  contactEmail: string | null
  contactPhone: string | null
  website: string | null
  address: string | null
  signatoryName: string | null
  signatoryTitle: string | null
  /** Relative to the API; run through {@link assetUrl} before using it as an image source. */
  logoUrl: string | null
  faviconUrl: string | null
  loginBackgroundUrl: string | null
  version: number
}

export type BrandingImageKind = 'logo' | 'favicon' | 'login-background'

export type UpdateBrandingInput = Omit<Branding, 'logoUrl' | 'faviconUrl' | 'loginBackgroundUrl' | 'version'>

/** Anonymous: the login page reads this before anyone is signed in. */
export async function getBranding(): Promise<Branding> {
  const { data } = await apiClient.get<Branding>('/api/public/branding')
  return data
}

export async function updateBranding(input: UpdateBrandingInput): Promise<Branding> {
  const { data } = await apiClient.put<Branding>('/api/branding', input)
  return data
}

export async function uploadBrandingImage(kind: BrandingImageKind, file: File): Promise<Branding> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<Branding>(`/api/branding/${kind}`, form)
  return data
}

export async function removeBrandingImage(kind: BrandingImageKind): Promise<Branding> {
  const { data } = await apiClient.delete<Branding>(`/api/branding/${kind}`)
  return data
}

export function assetUrl(path: string | null): string | null {
  return path ? `${env.apiBaseUrl}${path}` : null
}
