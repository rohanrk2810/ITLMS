import { Navigate, Outlet, useLocation } from 'react-router-dom'

import type { Role } from '@/api/types'
import { hasRole, useAuthStore } from '@/stores/auth-store'

/**
 * Gates a route tree on being signed in, and optionally on role.
 *
 * <p>This is UX only - it stops a user seeing a page their role has nothing
 * to do on. It is not the security boundary: every API call behind it is
 * checked again by the owning service (`@PreAuthorize`), because Doc S12 is
 * explicit that backend permission checks must not rely on frontend
 * visibility, and this router is exactly that frontend.
 */
export function ProtectedRoute({ roles }: { roles?: readonly Role[] }) {
  const { status, user } = useAuthStore()
  const location = useLocation()

  if (status === 'checking') {
    return (
      <div className="flex min-h-svh items-center justify-center text-muted-foreground text-sm">
        Signing you in...
      </div>
    )
  }

  if (status === 'unauthenticated' || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (roles && !hasRole(user.role, roles)) {
    return <Navigate to="/app/forbidden" replace />
  }

  return <Outlet />
}
