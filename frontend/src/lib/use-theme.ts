import { useEffect, useState } from 'react'

import { useUiConfig } from '@/lib/ui-config'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'theme'

function storedTheme(): Theme | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark') return stored
  } catch {
    // Private browsing, or storage blocked - fall through to the default.
  }
  return null
}

/**
 * Light or dark mode. A person's own choice (the toggle) is kept per browser; until they make one the
 * administrator's default applies, and failing that the system setting. `index.html` applies the stored choice
 * before React mounts so there is no flash.
 */
export function useTheme() {
  const defaultMode = useUiConfig().theme.defaultMode
  const [chosen, setChosen] = useState<Theme | null>(storedTheme)
  const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  const theme: Theme = chosen ?? (defaultMode === 'system' ? (systemDark ? 'dark' : 'light') : defaultMode)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    setChosen(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Nothing to persist to; the toggle still works for the rest of this session.
    }
  }

  return { theme, toggle }
}
