import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'

import { getCaptchaConfig } from '@/api/auth'

/** The slice of Cloudflare Turnstile's browser API that this app uses. */
interface TurnstileApi {
  render(
    element: HTMLElement,
    options: {
      sitekey: string
      theme?: 'auto' | 'light' | 'dark'
      callback: (token: string) => void
      'expired-callback'?: () => void
      'error-callback'?: () => void
    },
  ): string
  reset(widgetId?: string): void
  remove(widgetId?: string): void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

let scriptLoading: Promise<TurnstileApi> | null = null

/** Loads Cloudflare's script once. A failed load is forgotten so the next visit tries again. */
function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  if (!scriptLoading) {
    scriptLoading = new Promise<TurnstileApi>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
      script.async = true
      script.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error('Turnstile missing')))
      script.onerror = () => reject(new Error('Turnstile could not load'))
      document.head.appendChild(script)
    })
    scriptLoading.catch(() => {
      scriptLoading = null
    })
  }
  return scriptLoading
}

export interface CaptchaState {
  /** The server has the check switched on, so a token is needed to submit. */
  enabled: boolean
  /** The token to send with the form; null until the visitor has passed the check. */
  token: string | null
  /** Put this in the form where the check should appear. Nothing renders when the check is off. */
  widget: ReactNode
  /** A token works once, so call this after a failed submit to ask for a fresh one. */
  reset: () => void
  /** The check could not be shown (blocked script, offline). The form cannot be submitted. */
  failed: boolean
}

/**
 * The "verify you are human" check for a form. It asks the server whether the check is on (and with which public key),
 * shows Cloudflare's widget if so, and hands back the token to send with the form.
 */
export function useCaptcha(): CaptchaState {
  const config = useQuery({ queryKey: ['captcha-config'], queryFn: getCaptchaConfig, staleTime: Infinity })
  const enabled = config.data?.enabled ?? false
  const siteKey = config.data?.siteKey ?? ''

  const container = useRef<HTMLDivElement>(null)
  const widgetId = useRef<string | undefined>(undefined)
  const [token, setToken] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!enabled || !siteKey) return
    let cancelled = false
    void loadTurnstile()
      .then((turnstile) => {
        if (cancelled || !container.current) return
        widgetId.current = turnstile.render(container.current, {
          sitekey: siteKey,
          theme: 'auto',
          callback: (value) => {
            setToken(value)
            setFailed(false)
          },
          'expired-callback': () => setToken(null),
          'error-callback': () => {
            setToken(null)
            setFailed(true)
          },
        })
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
      if (widgetId.current !== undefined) {
        window.turnstile?.remove(widgetId.current)
        widgetId.current = undefined
      }
      setToken(null)
    }
  }, [enabled, siteKey])

  const reset = useCallback(() => {
    setToken(null)
    if (widgetId.current !== undefined) window.turnstile?.reset(widgetId.current)
  }, [])

  const widget = enabled ? (
    <div className="flex flex-col gap-1">
      <div ref={container} />
      {failed && (
        <p role="alert" className="text-xs text-destructive">
          The check could not load. Check your connection, turn off any blocker for this site, and reload the page.
        </p>
      )}
    </div>
  ) : null

  return { enabled, token, widget, reset, failed }
}
