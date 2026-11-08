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

/** The visitor's language for the public website: their last choice, else the browser's, else English. */
export function useSiteLang(): [Lang, (next: Lang) => void] {
  const [lang, setLang] = useState<Lang>(initial)
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
