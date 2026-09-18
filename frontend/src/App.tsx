import { type ReactNode, useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { bootstrapSession } from '@/api/auth'
import { NAV_ITEMS } from '@/components/nav-items'
import { Toaster } from '@/components/ui/sonner'
import { AppLayout } from '@/layouts/app-layout'
import { AuthLayout } from '@/layouts/auth-layout'
import { DashboardPage } from '@/pages/dashboard-page'
import { ForbiddenPage } from '@/pages/forbidden-page'
import { ForgotPasswordPage } from '@/pages/forgot-password-page'
import { LoginPage } from '@/pages/login-page'
import { NotFoundPage } from '@/pages/not-found-page'
import { PlaceholderPage } from '@/pages/placeholder-page'
import { RegisterPage } from '@/pages/register-page'
import { ResetPasswordPage } from '@/pages/reset-password-page'
import { ProtectedRoute } from '@/routes/protected-route'
import { useAuthStore } from '@/stores/auth-store'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
})

function RootRedirect() {
  const status = useAuthStore((state) => state.status)
  if (status === 'checking') return null
  return <Navigate to={status === 'authenticated' ? '/app' : '/login'} replace />
}

/** Tries a stored refresh token once, before the router renders anything that needs to know who's signed in. */
function SessionBootstrap({ children }: { children: ReactNode }) {
  useEffect(() => {
    void bootstrapSession()
  }, [])
  return children
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionBootstrap>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<RootRedirect />} />

            <Route element={<AuthLayout />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
            </Route>

            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/app" element={<DashboardPage />} />
                <Route path="/app/forbidden" element={<ForbiddenPage />} />
                {NAV_ITEMS.filter((item) => item.to !== '/app').map((item) => (
                  <Route key={item.to} element={<ProtectedRoute roles={item.roles} />}>
                    <Route path={item.to} element={<PlaceholderPage title={item.label} />} />
                  </Route>
                ))}
              </Route>
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </BrowserRouter>
        <Toaster />
      </SessionBootstrap>
    </QueryClientProvider>
  )
}
