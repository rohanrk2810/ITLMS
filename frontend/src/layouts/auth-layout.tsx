import { Navigate, Outlet } from 'react-router-dom'

import { assetUrl } from '@/api/branding'
import { BrandMark } from '@/components/brand-mark'
import { useBranding } from '@/lib/use-branding'
import { useAuthStore } from '@/stores/auth-store'

/** The centred-card shell every unauthenticated page (sign in, register, reset) shares. */
export function AuthLayout() {
  const { status } = useAuthStore()
  const { branding } = useBranding()

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
            ? 'flex w-full max-w-sm flex-col items-center gap-4 rounded-xl bg-background/95 p-4 shadow-lg backdrop-blur-sm'
            : 'flex w-full max-w-sm flex-col items-center gap-6'
        }
      >
        <div className="flex items-center gap-2 text-lg font-semibold">
          <BrandMark className="size-8" />
        </div>
        <div className="w-full">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
