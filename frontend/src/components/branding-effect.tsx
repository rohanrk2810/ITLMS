import { useEffect } from 'react'

import { assetUrl } from '@/api/branding'
import { useBranding } from '@/lib/use-branding'

/** White text on a dark brand colour, black on a light one (WCAG relative-luminance test). */
function readableOn(hex: string): string {
  const channel = (i: number) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5)
  return luminance > 0.4 ? '#111111' : '#ffffff'
}

/**
 * Applies the institute's identity to the page itself: the browser-tab title and icon and the
 * brand colour. Renders nothing. The colour is set on the root element, so it wins over the
 * stylesheet defaults for both light and dark mode.
 */
export function BrandingEffect() {
  const { branding, name } = useBranding()

  useEffect(() => {
    document.title = name
  }, [name])

  useEffect(() => {
    const href = assetUrl(branding?.faviconUrl ?? null)
    if (!href) {
      return
    }
    let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
    if (!link) {
      link = document.createElement('link')
      link.rel = 'icon'
      document.head.appendChild(link)
    }
    link.removeAttribute('type')
    link.href = href
  }, [branding?.faviconUrl])

  useEffect(() => {
    const root = document.documentElement
    const vars = ['--primary', '--primary-foreground', '--ring', '--sidebar-primary', '--sidebar-primary-foreground']
    const color = branding?.primaryColor
    if (!color) {
      vars.forEach((v) => root.style.removeProperty(v))
      return
    }
    const on = readableOn(color)
    root.style.setProperty('--primary', color)
    root.style.setProperty('--ring', color)
    root.style.setProperty('--sidebar-primary', color)
    root.style.setProperty('--primary-foreground', on)
    root.style.setProperty('--sidebar-primary-foreground', on)
  }, [branding?.primaryColor])

  return null
}
