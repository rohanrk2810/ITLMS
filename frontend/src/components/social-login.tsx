import { type FormEvent, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'

import { getSocialConfig, loginWithGoogle, loginWithWhatsAppCode, requestWhatsAppCode } from '@/api/auth'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** The slice of Google Identity Services that this app uses. */
interface GoogleIdApi {
  initialize(options: { client_id: string; callback: (response: { credential?: string }) => void }): void
  renderButton(element: HTMLElement, options: { theme?: string; size?: string; text?: string; width?: number }): void
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdApi } }
  }
}

let scriptLoading: Promise<GoogleIdApi> | null = null

/** Loads the Google script once. A failed load is forgotten so the next visit tries again. */
function loadGoogle(): Promise<GoogleIdApi> {
  if (window.google?.accounts.id) return Promise.resolve(window.google.accounts.id)
  if (!scriptLoading) {
    scriptLoading = new Promise<GoogleIdApi>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      script.onload = () =>
        window.google?.accounts.id ? resolve(window.google.accounts.id) : reject(new Error('Google missing'))
      script.onerror = () => reject(new Error('Google could not load'))
      document.head.appendChild(script)
    })
    scriptLoading.catch(() => {
      scriptLoading = null
    })
  }
  return scriptLoading
}

interface Props {
  /** The "verify you are human" token from the sign-in form; needed to ask for a WhatsApp code when that check is on. */
  captchaToken: string | null
  captchaRequired: boolean
  /** The token works once, so the form is asked for a fresh one after each request. */
  onCaptchaUsed: () => void
  onSignedIn: () => void
}

/** "Continue with Google" and "Sign in with a WhatsApp code", each shown only when the server has it switched on. */
export function SocialLogin({ captchaToken, captchaRequired, onCaptchaUsed, onSignedIn }: Props) {
  // The desktop app's embedded browser cannot complete the Google pop-up, so these are for the website.
  const inDesktopApp = navigator.userAgent.includes('Electron')
  const config = useQuery({ queryKey: ['social-config'], queryFn: getSocialConfig, staleTime: Infinity })
  const googleClientId = inDesktopApp ? '' : (config.data?.googleClientId ?? '')
  const whatsapp = !inDesktopApp && (config.data?.whatsappEnabled ?? false)

  const googleButton = useRef<HTMLDivElement>(null)
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [open, setOpen] = useState(false)
  const [sent, setSent] = useState(false)

  const googleMutation = useMutation({
    mutationFn: loginWithGoogle,
    onSuccess: onSignedIn,
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not sign in with Google.')),
  })

  useEffect(() => {
    if (!googleClientId) return
    let cancelled = false
    void loadGoogle()
      .then((google) => {
        if (cancelled || !googleButton.current) return
        google.initialize({
          client_id: googleClientId,
          callback: (response) => {
            if (response.credential) googleMutation.mutate(response.credential)
          },
        })
        google.renderButton(googleButton.current, { theme: 'outline', size: 'large', text: 'continue_with', width: 320 })
      })
      .catch(() => toast.error('Google sign-in could not load. Check your connection and reload the page.'))
    return () => {
      cancelled = true
    }
    // googleMutation.mutate is stable; the button must be drawn once per client id.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [googleClientId])

  const requestMutation = useMutation({
    mutationFn: () => requestWhatsAppCode(phone, captchaToken),
    onSuccess: (message) => {
      setSent(true)
      toast.success(message.message)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not send the code.')),
    onSettled: onCaptchaUsed,
  })

  const verifyMutation = useMutation({
    mutationFn: () => loginWithWhatsAppCode(phone, code),
    onSuccess: onSignedIn,
    onError: (error) => toast.error(apiErrorMessage(error, 'The code is wrong or has expired.')),
  })

  if (!googleClientId && !whatsapp) return null

  function handleRequest(event: FormEvent) {
    event.preventDefault()
    requestMutation.mutate()
  }

  function handleVerify(event: FormEvent) {
    event.preventDefault()
    verifyMutation.mutate()
  }

  return (
    <div className="mt-4 flex flex-col gap-3">
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        or
        <span className="h-px flex-1 bg-border" />
      </div>

      {googleClientId && <div ref={googleButton} className="flex justify-center" />}

      {whatsapp && !open && (
        <Button type="button" variant="outline" onClick={() => setOpen(true)}>
          Sign in with a WhatsApp code
        </Button>
      )}

      {whatsapp && open && !sent && (
        <form className="flex flex-col gap-3" onSubmit={handleRequest}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="whatsapp-phone">Mobile number on WhatsApp</Label>
            <Input
              id="whatsapp-phone"
              type="tel"
              autoComplete="tel"
              placeholder="9876543210"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              required
            />
          </div>
          <Button type="submit" disabled={requestMutation.isPending || (captchaRequired && !captchaToken)}>
            {requestMutation.isPending ? 'Sending...' : 'Send code on WhatsApp'}
          </Button>
        </form>
      )}

      {whatsapp && open && sent && (
        <form className="flex flex-col gap-3" onSubmit={handleVerify}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="whatsapp-code">6-digit code</Label>
            <Input
              id="whatsapp-code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
              required
            />
          </div>
          <Button type="submit" disabled={verifyMutation.isPending || code.length !== 6}>
            {verifyMutation.isPending ? 'Signing in...' : 'Verify and sign in'}
          </Button>
          <button
            type="button"
            className="text-xs text-muted-foreground hover:underline"
            onClick={() => {
              setSent(false)
              setCode('')
            }}
          >
            Use a different number or send again
          </button>
        </form>
      )}
    </div>
  )
}
