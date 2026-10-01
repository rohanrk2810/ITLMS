import { useSyncExternalStore } from 'react'

/** Colours that say the same thing in every chart: good, bad, in between, nothing yet. */
export const SEMANTIC = {
  good: '#10b981',
  bad: '#ef4444',
  warn: '#f59e0b',
  neutral: '#94a3b8',
} as const

/** For categories with no good/bad meaning. Distinct in light and dark, and not only by hue. */
export const PALETTE = ['#2563eb', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444', '#64748b'] as const

export interface ChartColors {
  text: string
  muted: string
  grid: string
  primary: string
  card: string
}

function read(): string {
  const style = getComputedStyle(document.documentElement)
  const get = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  const dark = document.documentElement.classList.contains('dark')
  const colors: ChartColors = {
    text: get('--foreground', dark ? '#f8fafc' : '#0f172a'),
    muted: get('--muted-foreground', dark ? '#94a3b8' : '#64748b'),
    grid: get('--border', dark ? '#334155' : '#e2e8f0'),
    primary: get('--primary', '#f97316'),
    card: get('--card', dark ? '#0f172a' : '#ffffff'),
  }
  return JSON.stringify(colors)
}

function subscribe(onChange: () => void) {
  // The dark-mode toggle and the institute brand colour both change these on the root element.
  const observer = new MutationObserver(onChange)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
  return () => observer.disconnect()
}

/** The current theme colours for canvas drawing, which cannot use CSS variables itself. Re-reads when the theme changes. */
export function useChartColors(): ChartColors {
  const snapshot = useSyncExternalStore(subscribe, read, () => '{}')
  return JSON.parse(snapshot) as ChartColors
}
