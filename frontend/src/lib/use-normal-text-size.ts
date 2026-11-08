import { useEffect } from 'react'

import { useUiConfig } from '@/lib/ui-config'

/**
 * The portal's text-size setting is for the app itself. Public pages (the website, sign-in, register)
 * keep the normal size, and the setting comes back when the visitor moves into the app.
 */
export function useNormalTextSize() {
  const fontScale = useUiConfig().theme.fontScale
  useEffect(() => {
    const root = document.documentElement
    root.style.fontSize = ''
    return () => {
      root.style.fontSize = fontScale === 100 ? '' : `${fontScale}%`
    }
  }, [fontScale])
}
