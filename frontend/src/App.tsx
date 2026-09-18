import { type ReactNode, Suspense, lazy, useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import type { Role } from '@/api/types'
import { bootstrapSession } from '@/api/auth'
import { NAV_ITEMS } from '@/components/nav-items'
import { Toaster } from '@/components/ui/sonner'
import { AppLayout } from '@/layouts/app-layout'
import { AuthLayout } from '@/layouts/auth-layout'
import { PlaceholderPage } from '@/pages/placeholder-page'
import { ProtectedRoute } from '@/routes/protected-route'
import { useAuthStore } from '@/stores/auth-store'

// Every page beyond the dashboard is lazy: the main bundle stays small, and
// a module a visitor's role never opens (say, Finance for a student) is
// never fetched at all.
const LoginPage = lazy(() => import('@/pages/login-page').then((m) => ({ default: m.LoginPage })))
const RegisterPage = lazy(() => import('@/pages/register-page').then((m) => ({ default: m.RegisterPage })))
const ForgotPasswordPage = lazy(() =>
  import('@/pages/forgot-password-page').then((m) => ({ default: m.ForgotPasswordPage })),
)
const ResetPasswordPage = lazy(() =>
  import('@/pages/reset-password-page').then((m) => ({ default: m.ResetPasswordPage })),
)
const DashboardPage = lazy(() => import('@/pages/dashboard-page').then((m) => ({ default: m.DashboardPage })))
const ChangePasswordPage = lazy(() =>
  import('@/pages/change-password-page').then((m) => ({ default: m.ChangePasswordPage })),
)
const ForbiddenPage = lazy(() => import('@/pages/forbidden-page').then((m) => ({ default: m.ForbiddenPage })))
const NotFoundPage = lazy(() => import('@/pages/not-found-page').then((m) => ({ default: m.NotFoundPage })))

const MyCoursesPage = lazy(() => import('@/pages/courses/my-courses-page').then((m) => ({ default: m.MyCoursesPage })))
const CourseDetailPage = lazy(() =>
  import('@/pages/courses/course-detail-page').then((m) => ({ default: m.CourseDetailPage })),
)
const LessonPlayerPage = lazy(() =>
  import('@/pages/courses/lesson-player-page').then((m) => ({ default: m.LessonPlayerPage })),
)

const TestsPage = lazy(() => import('@/pages/assessments/tests-page').then((m) => ({ default: m.TestsPage })))
const QuizAttemptPage = lazy(() =>
  import('@/pages/assessments/quiz-attempt-page').then((m) => ({ default: m.QuizAttemptPage })),
)

const LiveClassesPage = lazy(() =>
  import('@/pages/live-classes/live-classes-page').then((m) => ({ default: m.LiveClassesPage })),
)
const LiveClassRoomPage = lazy(() =>
  import('@/pages/live-classes/live-class-room-page').then((m) => ({ default: m.LiveClassRoomPage })),
)

const FinancePage = lazy(() => import('@/pages/finance/finance-page').then((m) => ({ default: m.FinancePage })))

const CertificatesPage = lazy(() =>
  import('@/pages/certificates/certificates-page').then((m) => ({ default: m.CertificatesPage })),
)

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
})

const ACADEMIC: readonly Role[] = ['ADMIN', 'COORDINATOR', 'TRAINER', 'STUDENT']
const FINANCE_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR', 'FINANCE', 'STUDENT']
const CERTIFICATE_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR', 'STUDENT']

// Nav items with a real page below - excluded from the generic placeholder loop.
const BUILT_PATHS = new Set(['/app/courses', '/app/live-classes', '/app/assessments', '/app/finance', '/app/certificates'])

function PageFallback() {
  return <div className="p-6 text-sm text-muted-foreground">Loading...</div>
}

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
          <Suspense fallback={<PageFallback />}>
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
                  <Route path="/app/change-password" element={<ChangePasswordPage />} />
                  <Route path="/app/forbidden" element={<ForbiddenPage />} />

                  <Route element={<ProtectedRoute roles={ACADEMIC} />}>
                    <Route path="/app/courses" element={<MyCoursesPage />} />
                    <Route path="/app/courses/:courseId" element={<CourseDetailPage />} />
                    <Route path="/app/courses/:courseId/lessons/:lessonId" element={<LessonPlayerPage />} />

                    <Route path="/app/live-classes" element={<LiveClassesPage />} />
                    <Route path="/app/live-classes/:sessionId" element={<LiveClassRoomPage />} />

                    <Route path="/app/assessments" element={<TestsPage />} />
                    <Route path="/app/assessments/tests/:quizId" element={<QuizAttemptPage />} />
                    <Route
                      path="/app/assessments/assignments/:assignmentId"
                      element={<PlaceholderPage title="Assignment" />}
                    />
                  </Route>

                  <Route element={<ProtectedRoute roles={FINANCE_ROLES} />}>
                    <Route path="/app/finance" element={<FinancePage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={CERTIFICATE_ROLES} />}>
                    <Route path="/app/certificates" element={<CertificatesPage />} />
                  </Route>

                  {NAV_ITEMS.filter((item) => item.to !== '/app' && !BUILT_PATHS.has(item.to)).map((item) => (
                    <Route key={item.to} element={<ProtectedRoute roles={item.roles} />}>
                      <Route path={item.to} element={<PlaceholderPage title={item.label} />} />
                      <Route path={`${item.to}/:id`} element={<PlaceholderPage title={item.label} />} />
                    </Route>
                  ))}
                </Route>
              </Route>

              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
        <Toaster />
      </SessionBootstrap>
    </QueryClientProvider>
  )
}
