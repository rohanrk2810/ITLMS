import { Navigate, Outlet } from 'react-router-dom'
import { GraduationCap } from 'lucide-react'

import { useAuthStore } from '@/stores/auth-store'

/** The centred-card shell every unauthenticated page (sign in, register, reset) shares. */
export function AuthLayout() {
  const { status } = useAuthStore()

  if (status === 'authenticated') {
    return <Navigate to="/app" replace />
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted/40 p-6">
      <div className="flex items-center gap-2 text-lg font-semibold">
        <GraduationCap className="size-6" />
        IT Institute LMS
      </div>
      <div className="w-full max-w-sm">
        <Outlet />
      </div>
    </div>
  )
}
