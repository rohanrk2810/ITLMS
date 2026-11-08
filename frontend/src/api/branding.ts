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
  appBackgroundUrl: string | null
  /** Sidebar paths students do not see (e.g. "/app/finance"). Empty: everything their role allows. */
  studentHiddenMenu: string[]
  /** JSON text of the portal customization; read it with parseUiConfig. */
  uiConfig: string
  version: number
}

export type BrandingImageKind = 'logo' | 'favicon' | 'login-background' | 'app-background'

export type UpdateBrandingInput = Omit<Branding, 'logoUrl' | 'faviconUrl' | 'loginBackgroundUrl' | 'appBackgroundUrl' | 'studentHiddenMenu' | 'uiConfig' | 'version'>

/** Anonymous: the login page reads this before anyone is signed in. */
export async function getBranding(): Promise<Branding> {
  const { data } = await apiClient.get<Branding>('/api/public/branding')
  return data
}

export async function updateBranding(input: UpdateBrandingInput): Promise<Branding> {
  const { data } = await apiClient.put<Branding>('/api/branding', input)
  return data
}

export async function updateUiConfig(config: string): Promise<Branding> {
  const { data } = await apiClient.put<Branding>('/api/branding/ui-config', { config })
  return data
}

export async function updateStudentMenu(hidden: string[]): Promise<Branding> {
  const { data } = await apiClient.put<Branding>('/api/branding/student-menu', { hidden })
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

/** Anonymous: the public front page reads its wording here. The text is JSON; see lib/site-config. */
export async function getSiteConfig(): Promise<{ config: string }> {
  const { data } = await apiClient.get<{ config: string }>('/api/public/branding/site')
  return data
}

export async function updateSiteConfig(config: string): Promise<{ config: string }> {
  const { data } = await apiClient.put<{ config: string }>('/api/branding/site-config', { config })
  return data
}

/** Adds a picture for the website and returns its id (store that in the website content). */
export async function uploadSiteImage(file: Blob, name = 'photo.jpg'): Promise<{ id: number; url: string }> {
  const form = new FormData()
  form.append('file', file, name)
  const { data } = await apiClient.post<{ id: number; url: string }>('/api/branding/site-images', form)
  return data
}

export async function deleteSiteImage(id: string | number): Promise<void> {
  await apiClient.delete(`/api/branding/site-images/${id}`)
}
