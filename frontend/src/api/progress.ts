import { apiClient } from './client'

export type Band = 'GOOD' | 'FAIR' | 'LOW' | 'NONE'

export interface Indicator {
  key: string
  label: string
  /** Null when there is nothing behind the number yet: shown as "no data", never as 0%. */
  percent: number | null
  band: Band
  detail: string
}

export interface ReportEnrollment {
  enrollmentId: number
  status: string
  batchId: number
  batchCode: string | null
  batchName: string | null
  batchStatus: string | null
  courseId: number
  courseTitle: string | null
  trainerName: string | null
  mode: string | null
  startDate: string | null
  endDate: string | null
}

export interface ModuleProgress {
  moduleId: number
  title: string
  lessons: number
  completed: number
}

export interface CourseProgress {
  courseId: number
  courseTitle: string | null
  batchId: number
  status: string
  progressPercent: number
  completedLessons: number
  totalLessons: number
  videoLessons: number
  videoLessonsCompleted: number
  modules: ModuleProgress[]
}

export interface BatchAttendance {
  batchId: number
  batchCode: string | null
  attended: number
  counted: number
  percent: number | null
}

export interface Attendance {
  percent: number | null
  attended: number
  counted: number
  absent: number
  excused: number
  byBatch: BatchAttendance[]
}

export interface LiveParticipation {
  sessionsHeld: number
  sessionsJoined: number
  minutesInRoom: number
  averageAttendancePercent: number | null
  lastJoinedAt: string | null
}

export interface PendingWork {
  id: number
  title: string
  dueAt: string | null
  overdue: boolean
}

export interface WeakTest {
  id: number
  title: string
  percent: number
  passPercent: number
}

export interface TestsSummary {
  attempted: number
  passed: number
  averagePercent: number | null
  bestPercent: number | null
  terminated: number
  resultsPending: number
  pending: PendingWork[]
  below: WeakTest[]
}

export interface CodingSummary {
  questionsAttempted: number
  testCasesPassed: number
  testCasesTotal: number
  averagePercent: number | null
  lowest: { questionId: number; question: string; passed: number; total: number }[]
}

export interface AssignmentsSummary {
  assigned: number
  submitted: number
  evaluated: number
  averagePercent: number | null
  returned: number
  late: number
  pending: PendingWork[]
}

export interface Suggestion {
  type: string
  /** 1 is the most urgent. */
  priority: number
  title: string
  detail: string
  /** A page in this app. */
  link: string
}

export interface StudentProgressReport {
  studentId: number
  profile: { fullName: string; studentCode: string; email: string; phone: string; status: string } | null
  generatedAt: string
  indicators: Indicator[]
  enrollments: ReportEnrollment[]
  courses: CourseProgress[] | null
  attendance: Attendance
  liveClasses: LiveParticipation | null
  tests: TestsSummary | null
  coding: CodingSummary | null
  assignments: AssignmentsSummary | null
  suggestions: Suggestion[]
  /** Sections that could not be loaded just now. */
  unavailable: string[]
}

/** The signed-in student's own report. */
export async function getMyProgress(): Promise<StudentProgressReport> {
  const { data } = await apiClient.get<StudentProgressReport>('/api/reports/me/progress')
  return data
}

/** Any student's report - administrators and coordinators, or a trainer of one of their batches. */
export async function getStudentProgress(studentId: number | string): Promise<StudentProgressReport> {
  const { data } = await apiClient.get<StudentProgressReport>(`/api/reports/students/${studentId}/progress`)
  return data
}
