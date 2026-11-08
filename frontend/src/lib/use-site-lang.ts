import { useCallback, useEffect, useState } from 'react'

import type { Lang } from '@/lib/site-config'

const KEY = 'site-language'

function initial(): Lang {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'en' || saved === 'mr') return saved
  } catch {
    // Storage blocked: fall through to the browser's language.
  }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('mr') ? 'mr' : 'en'
}

/**
 * The visitor's language for the public website: their last choice, else the browser's, else English.
 * When the language button is off, the site is always English.
 */
export function useSiteLang(enabled: boolean): [Lang, (next: Lang) => void] {
  const [chosen, setLang] = useState<Lang>(initial)
  const lang: Lang = enabled ? chosen : 'en'
  useEffect(() => {
    document.documentElement.lang = lang
    return () => {
      document.documentElement.lang = 'en'
    }
  }, [lang])
  const choose = useCallback((next: Lang) => {
    setLang(next)
    try {
      localStorage.setItem(KEY, next)
    } catch {
      // The choice just is not remembered.
    }
  }, [])
  return [lang, choose]
}
