import { useMemo } from 'react'

import type { Role } from '@/api/types'
import { useBranding } from '@/lib/use-branding'

export type DefaultMode = 'system' | 'light' | 'dark'

export interface RoleMenu {
  /** App paths this role does not see. The dashboard and the customization page itself can never be hidden. */
  hidden?: string[]
  /** App paths in the order wanted; anything not listed follows in its usual order. */
  order?: string[]
  /** A new name for a menu item, by app path. */
  labels?: Record<string, string>
}

export interface UiConfig {
  theme: {
    /** Used until a person picks light or dark for themselves with the toggle. */
    defaultMode: DefaultMode
    /** Corner roundness in rem (0 square ... 1.25 very round). */
    radius: number
    /** Text size in percent of the normal size. */
    fontScale: number
  }
  menus: Partial<Record<Role, RoleMenu>>
  /** Dashboard blocks to hide, by id (see {@link DASHBOARD_BLOCKS}). */
  dashboard: { hidden: string[] }
  login: { welcome: string; footer: string }
  /** Words shown differently everywhere on screen: { "Student": "Learner", "Batch": "Class" }. */
  terms: Record<string, string>
}

export const DEFAULT_UI_CONFIG: UiConfig = {
  theme: { defaultMode: 'system', radius: 0.625, fontScale: 100 },
  menus: {},
  dashboard: { hidden: [] },
  login: { welcome: '', footer: '' },
  terms: {},
}

/** Every block of the dashboard an administrator may hide. */
export const DASHBOARD_BLOCKS: { id: string; label: string }[] = [
  { id: 'stat-students', label: 'Students admitted' },
  { id: 'stat-enrollments', label: 'Enrollments' },
  { id: 'stat-revenue', label: 'Revenue collected' },
  { id: 'stat-overdue', label: 'Overdue' },
  { id: 'stat-certificates', label: 'Certificates issued' },
  { id: 'stat-quizzes', label: 'Quiz attempts' },
  { id: 'stat-attendance', label: 'Attendance' },
  { id: 'stat-placements', label: 'Placements' },
  { id: 'charts', label: 'Charts' },
  { id: 'audit-link', label: 'Audit trail link' },
  { id: 'quick-links', label: 'Quick links (trainer, student, finance, placement)' },
  { id: 'student-charts', label: 'Student charts' },
  { id: 'announcements', label: 'Announcements' },
]

const strings = (value: unknown): string[] => (Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [])
const stringMap = (value: unknown): Record<string, string> =>
  value && typeof value === 'object'
    ? Object.fromEntries(Object.entries(value).filter(([k, v]) => k.trim() && typeof v === 'string' && v.trim()))
    : {}

/** Reads the stored JSON, ignoring anything that is missing or the wrong shape. Never throws. */
export function parseUiConfig(text: string | null | undefined): UiConfig {
  let raw: Record<string, unknown> = {}
  try {
    const parsed: unknown = JSON.parse(text || '{}')
    if (parsed && typeof parsed === 'object') raw = parsed as Record<string, unknown>
  } catch {
    // Fall back to the defaults.
  }
  const theme = (raw.theme ?? {}) as Record<string, unknown>
  const mode = theme.defaultMode
  const menus: UiConfig['menus'] = {}
  for (const [role, value] of Object.entries((raw.menus ?? {}) as Record<string, Record<string, unknown>>)) {
    if (!value || typeof value !== 'object') continue
    menus[role as Role] = { hidden: strings(value.hidden), order: strings(value.order), labels: stringMap(value.labels) }
  }
  const login = (raw.login ?? {}) as Record<string, unknown>
  return {
    theme: {
      defaultMode: mode === 'light' || mode === 'dark' ? mode : 'system',
      radius: typeof theme.radius === 'number' ? Math.min(1.5, Math.max(0, theme.radius)) : DEFAULT_UI_CONFIG.theme.radius,
      fontScale: typeof theme.fontScale === 'number' ? Math.min(125, Math.max(85, theme.fontScale)) : 100,
    },
    menus,
    dashboard: { hidden: strings((raw.dashboard as Record<string, unknown> | undefined)?.hidden) },
    login: { welcome: typeof login.welcome === 'string' ? login.welcome : '', footer: typeof login.footer === 'string' ? login.footer : '' },
    terms: stringMap(raw.terms),
  }
}

export function useUiConfig(): UiConfig {
  const text = useBranding().branding?.uiConfig
  return useMemo(() => parseUiConfig(text), [text])
}

/** Paths nobody may hide, so an administrator cannot lock themselves out of the settings. */
export const ALWAYS_SHOWN = ['/app', '/app/customize']

// --- renaming words on screen ---------------------------------------------------------------------------------

const SKIP = 'input, textarea, select, code, pre, script, style, [contenteditable="true"], .monaco-editor, [data-no-terms]'

function replacer(terms: Record<string, string>): ((text: string) => string) | null {
  const entries = Object.entries(terms).filter(([from]) => from.trim())
  if (entries.length === 0) return null
  const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  // Longest first, so "Live classes" wins over "classes"; an optional plural s is carried over.
  entries.sort((a, b) => b[0].length - a[0].length)
  const lookup = new Map(entries.map(([from, to]) => [from.toLowerCase(), to]))
  const pattern = new RegExp(`\\b(${entries.map(([from]) => escape(from)).join('|')})(s?)\\b`, 'gi')
  return (text) =>
    text.replace(pattern, (_match, word: string, plural: string) => {
      const base = lookup.get(word.toLowerCase())
      if (base === undefined) return _match
      const cased = word[0] === word[0].toUpperCase() && word[0] !== word[0].toLowerCase() ? base[0].toUpperCase() + base.slice(1) : base
      return cased + plural
    })
}

/**
 * Replaces the words on screen as the page changes. Only text a person reads is touched: not form fields, code or
 * editors. Returns a function that stops it and restores nothing (a reload shows the original words again).
 */
export function applyTerms(terms: Record<string, string>): () => void {
  const replace = replacer(terms)
  if (!replace) return () => undefined

  const fix = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const parent = node.parentElement
      if (!parent || parent.closest(SKIP)) return
      const before = node.nodeValue ?? ''
      const after = replace(before)
      if (after !== before) node.nodeValue = after
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      const el = node as Element
      if (el.closest(SKIP)) return
      const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT)
      let current = walker.nextNode()
      while (current) {
        fix(current)
        current = walker.nextNode()
      }
    }
  }

  fix(document.body)
  const observer = new MutationObserver((mutations) => {
    observer.disconnect()
    for (const m of mutations) {
      if (m.type === 'characterData') fix(m.target)
      else m.addedNodes.forEach(fix)
    }
    observer.observe(document.body, { childList: true, subtree: true, characterData: true })
  })
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })
  return () => observer.disconnect()
}
