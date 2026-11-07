import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { assetUrl } from '@/api/branding'
import { BrandMark } from '@/components/brand-mark'
import { LegalLinks } from '@/components/legal-links'
import { useBranding } from '@/lib/use-branding'
import { useUiConfig } from '@/lib/ui-config'
import { useAuthStore } from '@/stores/auth-store'

/** The centred-card shell every unauthenticated page (sign in, register, reset) shares. */
export function AuthLayout() {
  const { status } = useAuthStore()
  const { branding } = useBranding()
  const { login } = useUiConfig()
  // The registration form carries a full profile, so it gets a wide card instead of a long narrow one.
  const width = useLocation().pathname.endsWith('/register') ? 'max-w-3xl' : 'max-w-sm'

  if (status === 'authenticated') {
    return <Navigate to="/app" replace />
  }

  const background = assetUrl(branding?.loginBackgroundUrl ?? null)

  return (
    <div
      className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted/40 bg-cover bg-center p-6"
      style={background ? { backgroundImage: `url(${background})` } : undefined}
    >
      <div
        className={
          background
            ? `flex w-full ${width} flex-col items-center gap-4 rounded-xl bg-background/95 p-4 shadow-lg backdrop-blur-sm`
            : `flex w-full ${width} flex-col items-center gap-6`
        }
      >
        <div className="flex items-center gap-2 text-lg font-semibold">
          <BrandMark className="size-8" />
        </div>
        {login.welcome && <p className="text-center text-sm text-muted-foreground">{login.welcome}</p>}
        <div className="w-full">
          <Outlet />
        </div>
        {login.footer && <p className="text-center text-xs text-muted-foreground">{login.footer}</p>}
        <LegalLinks />
      </div>
    </div>
  )
}
