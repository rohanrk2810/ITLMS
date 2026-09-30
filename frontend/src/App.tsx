import { type ReactNode, Suspense, lazy, useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'

import type { Role } from '@/api/types'
import { bootstrapSession } from '@/api/auth'
import { BrandingEffect } from '@/components/branding-effect'
import { NAV_ITEMS } from '@/components/nav-items'
import { Toaster } from '@/components/ui/sonner'
import { AppLayout } from '@/layouts/app-layout'
import { AuthLayout } from '@/layouts/auth-layout'
import { mapActionUrl } from '@/lib/action-url'
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

const CoursesIndexPage = lazy(() =>
  import('@/pages/courses/courses-index-page').then((m) => ({ default: m.CoursesIndexPage })),
)
const CourseDetailIndexPage = lazy(() =>
  import('@/pages/courses/course-detail-index-page').then((m) => ({ default: m.CourseDetailIndexPage })),
)
const LessonPlayerPage = lazy(() =>
  import('@/pages/courses/lesson-player-page').then((m) => ({ default: m.LessonPlayerPage })),
)

const AssessmentsIndexPage = lazy(() =>
  import('@/pages/assessments/assessments-index-page').then((m) => ({ default: m.AssessmentsIndexPage })),
)
const QuizIndexPage = lazy(() =>
  import('@/pages/assessments/quiz-index-page').then((m) => ({ default: m.QuizIndexPage })),
)

const LiveClassesPage = lazy(() =>
  import('@/pages/live-classes/live-classes-page').then((m) => ({ default: m.LiveClassesPage })),
)
const LiveClassRoomPage = lazy(() =>
  import('@/pages/live-classes/live-class-room-page').then((m) => ({ default: m.LiveClassRoomPage })),
)
const ClassReviewPage = lazy(() =>
  import('@/pages/live-classes/class-review-page').then((m) => ({ default: m.ClassReviewPage })),
)

const FinanceIndexPage = lazy(() =>
  import('@/pages/finance/finance-index-page').then((m) => ({ default: m.FinanceIndexPage })),
)
const FeePlanDetailPage = lazy(() =>
  import('@/pages/finance/fee-plan-detail-page').then((m) => ({ default: m.FeePlanDetailPage })),
)

const CertificatesPage = lazy(() =>
  import('@/pages/certificates/certificates-index-page').then((m) => ({ default: m.CertificatesIndexPage })),
)
const VerifyCertificatePage = lazy(() =>
  import('@/pages/verify-certificate-page').then((m) => ({ default: m.VerifyCertificatePage })),
)

const LeadsPage = lazy(() => import('@/pages/admissions/leads-page').then((m) => ({ default: m.LeadsPage })))
const LeadDetailPage = lazy(() =>
  import('@/pages/admissions/lead-detail-page').then((m) => ({ default: m.LeadDetailPage })),
)

const StudentsPage = lazy(() => import('@/pages/students/students-page').then((m) => ({ default: m.StudentsPage })))
const StudentDetailPage = lazy(() =>
  import('@/pages/students/student-detail-page').then((m) => ({ default: m.StudentDetailPage })),
)

const BatchesPage = lazy(() => import('@/pages/batches/batches-page').then((m) => ({ default: m.BatchesPage })))
const BatchDetailPage = lazy(() =>
  import('@/pages/batches/batch-detail-page').then((m) => ({ default: m.BatchDetailPage })),
)
const AttendanceMarkPage = lazy(() =>
  import('@/pages/batches/attendance-mark-page').then((m) => ({ default: m.AttendanceMarkPage })),
)

const AssignmentDetailPage = lazy(() =>
  import('@/pages/assessments/assignment-detail-page').then((m) => ({ default: m.AssignmentDetailPage })),
)

const UsersPage = lazy(() => import('@/pages/users/users-page').then((m) => ({ default: m.UsersPage })))

const AuditLogsPage = lazy(() => import('@/pages/audit-logs-page').then((m) => ({ default: m.AuditLogsPage })))

const PlacementsIndexPage = lazy(() =>
  import('@/pages/placements/placements-index-page').then((m) => ({ default: m.PlacementsIndexPage })),
)
const JobDetailIndexPage = lazy(() =>
  import('@/pages/placements/job-detail-index-page').then((m) => ({ default: m.JobDetailIndexPage })),
)

const FilesPage = lazy(() => import('@/pages/files/files-page').then((m) => ({ default: m.FilesPage })))

const MyProgressPage = lazy(() =>
  import('@/pages/progress/my-progress-page').then((m) => ({ default: m.MyProgressPage })),
)

const CourseRequestsIndexPage = lazy(() =>
  import('@/pages/course-requests/course-requests-index-page').then((m) => ({ default: m.CourseRequestsIndexPage })),
)

const BrandingPage = lazy(() => import('@/pages/branding-page').then((m) => ({ default: m.BrandingPage })))

const AnnouncementsPage = lazy(() =>
  import('@/pages/announcements/announcements-page').then((m) => ({ default: m.AnnouncementsPage })),
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
const CERTIFICATE_ROLES: readonly Role[] = ['ADMIN', 'STUDENT']
const ADMISSION_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR']
const STUDENT_RECORD_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR', 'TRAINER']
const BATCH_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR', 'TRAINER']
const USER_MANAGEMENT_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR']
const PLACEMENT_ROLES: readonly Role[] = ['ADMIN', 'PLACEMENT', 'STUDENT']
const COURSE_REQUEST_ROLES: readonly Role[] = ['ADMIN', 'COORDINATOR', 'STUDENT']

// Nav items with a real page below - excluded from the generic placeholder loop.
const BUILT_PATHS = new Set([
  '/app/courses',
  '/app/live-classes',
  '/app/assessments',
  '/app/finance',
  '/app/certificates',
  '/app/admissions',
  '/app/students',
  '/app/batches',
  '/app/users',
  '/app/placements',
  '/app/files',
  '/app/announcements',
  '/app/course-requests',
  '/app/my-progress',
])

function PageFallback() {
  return <div className="p-6 text-sm text-muted-foreground">Loading...</div>
}

function RootRedirect() {
  const status = useAuthStore((state) => state.status)
  if (status === 'checking') return null
  return <Navigate to={status === 'authenticated' ? '/app' : '/login'} replace />
}

/**
 * An email or notification link is the frontend origin plus a backend path
 * (`/jobs/40`, `/student/fees`, ...) - it never goes through the bell, which
 * is the only place `mapActionUrl` was used before. Anything unmatched is a
 * genuine 404, not a path this app is ever expected to answer for.
 */
function CatchAll() {
  const location = useLocation()
  const mapped = mapActionUrl(location.pathname)
  return mapped ? <Navigate to={mapped} replace /> : <NotFoundPage />
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
      <BrandingEffect />
      <SessionBootstrap>
        <BrowserRouter>
          <Suspense fallback={<PageFallback />}>
            <Routes>
              <Route path="/" element={<RootRedirect />} />

              {/* Public: anyone with a certificate ID and code can check it, signed in or not. */}
              <Route path="/verify/:certificateNo" element={<VerifyCertificatePage />} />

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

                  {/* Courses, tests and live classes each branch by role at the page
                      level - a student gets their own view, everyone else the
                      authoring/management one - so the route stays a single path. */}
                  <Route element={<ProtectedRoute roles={ACADEMIC} />}>
                    <Route path="/app/courses" element={<CoursesIndexPage />} />
                    <Route path="/app/courses/:courseId" element={<CourseDetailIndexPage />} />
                    <Route path="/app/courses/:courseId/lessons/:lessonId" element={<LessonPlayerPage />} />

                    <Route path="/app/live-classes" element={<LiveClassesPage />} />
                    <Route path="/app/live-classes/:sessionId" element={<LiveClassRoomPage />} />
                    <Route path="/app/live-classes/:classSessionId/review" element={<ClassReviewPage />} />

                    <Route path="/app/assessments" element={<AssessmentsIndexPage />} />
                    <Route path="/app/assessments/tests/:quizId" element={<QuizIndexPage />} />
                    <Route path="/app/assessments/assignments/:assignmentId" element={<AssignmentDetailPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={FINANCE_ROLES} />}>
                    <Route path="/app/finance" element={<FinanceIndexPage />} />
                    <Route path="/app/finance/:feePlanId" element={<FeePlanDetailPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={['STUDENT']} />}>
                    <Route path="/app/my-progress" element={<MyProgressPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={COURSE_REQUEST_ROLES} />}>
                    <Route path="/app/course-requests" element={<CourseRequestsIndexPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={CERTIFICATE_ROLES} />}>
                    <Route path="/app/certificates" element={<CertificatesPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={ADMISSION_ROLES} />}>
                    <Route path="/app/admissions" element={<LeadsPage />} />
                    <Route path="/app/admissions/:leadId" element={<LeadDetailPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={STUDENT_RECORD_ROLES} />}>
                    <Route path="/app/students" element={<StudentsPage />} />
                    <Route path="/app/students/:studentId" element={<StudentDetailPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={BATCH_ROLES} />}>
                    <Route path="/app/batches" element={<BatchesPage />} />
                    <Route path="/app/batches/:batchId" element={<BatchDetailPage />} />
                    <Route
                      path="/app/batches/:batchId/sessions/:sessionId/attendance"
                      element={<AttendanceMarkPage />}
                    />
                  </Route>

                  <Route element={<ProtectedRoute roles={USER_MANAGEMENT_ROLES} />}>
                    <Route path="/app/users" element={<UsersPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={['ADMIN']} />}>
                    <Route path="/app/audit-logs" element={<AuditLogsPage />} />
                    <Route path="/app/branding" element={<BrandingPage />} />
                  </Route>

                  <Route element={<ProtectedRoute roles={PLACEMENT_ROLES} />}>
                    <Route path="/app/placements" element={<PlacementsIndexPage />} />
                    <Route path="/app/placements/:jobId" element={<JobDetailIndexPage />} />
                  </Route>

                  {/* Files and announcements are open to every signed-in role; each page
                      shows more (a staff directory, a "new announcement" button) by role
                      internally, the same way the notification bell already does. */}
                  <Route path="/app/files" element={<FilesPage />} />
                  <Route path="/app/announcements" element={<AnnouncementsPage />} />

                  {NAV_ITEMS.filter((item) => item.to !== '/app' && !BUILT_PATHS.has(item.to)).map((item) => (
                    <Route key={item.to} element={<ProtectedRoute roles={item.roles} />}>
                      <Route path={item.to} element={<PlaceholderPage title={item.label} />} />
                      <Route path={`${item.to}/:id`} element={<PlaceholderPage title={item.label} />} />
                    </Route>
                  ))}
                </Route>
              </Route>

              <Route path="*" element={<CatchAll />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
        <Toaster />
      </SessionBootstrap>
    </QueryClientProvider>
  )
}
