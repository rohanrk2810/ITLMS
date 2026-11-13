import { apiClient } from './client'
import type { PageResponse } from './types'

export interface BatchSummaryResponse {
  id: number
  batchCode: string
  name: string
  courseId: number
  courseTitle: string
  trainerId: number | null
  trainerName: string | null
  startDate: string
  endDate: string
  startTime: string
  endTime: string
  classDays: string
  mode: string
  capacity: number
  enrolledCount: number
  status: string
  /** What the whole batch costs; empty when the course fee applies. */
  fee: number | null
}

/** A course a batch holds, with the trainer who teaches it there. */
export interface BatchCourse {
  courseId: number
  courseCode: string | null
  courseTitle: string | null
  trainerId: number | null
  trainerName: string | null
  /** The course the batch was created for; it cannot be taken out. */
  main: boolean
}

export interface BatchResponse {
  id: number
  batchCode: string
  name: string
  courseId: number
  courseCode: string
  courseTitle: string
  trainerId: number | null
  trainerName: string | null
  startDate: string
  endDate: string
  startTime: string
  endTime: string
  classDays: string[]
  mode: string
  capacity: number
  enrolledCount: number
  seatsAvailable: number
  classroom: string | null
  meetingUrl: string | null
  status: string
  coTrainers: { trainerId: number; trainerName: string; role: string }[]
  /** What the whole batch costs. A student placed in all of it pays this and nothing more for its courses. */
  fee: number | null
  /** Every course the batch holds, the one it was created for first. */
  courses: BatchCourse[]
}

export interface EnrollmentResponse {
  id: number
  studentId: number
  userId: number
  studentCode: string
  studentName: string
  courseId: number
  batchId: number
  enrolledAt: string
  status: string
  completionDate: string | null
  droppedReason: string | null
  /** The last day the student may use the course; empty means no limit. */
  accessUntil: string | null
  /** Placed in this one course alone, at its own fee, instead of in the whole batch. */
  courseOnly: boolean
}

export interface SessionResponse {
  id: number
  batchId: number
  batchCode: string | null
  courseTitle: string | null
  trainerId: number | null
  trainerName: string | null
  sessionDate: string
  startTime: string
  endTime: string
  topic: string | null
  mode: string
  meetingUrl: string | null
  room: string | null
  status: string
  attendanceMarked: boolean
  attendanceAuto: boolean
  cancelledReason: string | null
  live: boolean
  /** The course this lecture is for; empty means the course the batch was created for. */
  courseId: number | null
}

export interface AttendanceResponse {
  id: number
  sessionId: number
  studentId: number
  studentName: string
  status: string
  remark: string | null
  source: string
  attendedMinutes: number | null
  markedAt: string
  markedBy: number | null
  correctedAt: string | null
  correctedBy: number | null
  sessionDate: string | null
  topic: string | null
}

export interface CreateBatchInput {
  name?: string
  courseId: number
  trainerId?: number
  startDate: string
  endDate?: string
  startTime: string
  endTime: string
  classDays?: string[]
  mode?: string
  capacity?: number
  classroom?: string
  meetingUrl?: string
  /** What the whole batch costs. Leave empty to charge the course fee. */
  fee?: number
}

export interface CreateSessionInput {
  batchId: number
  sessionDate: string
  startTime?: string
  endTime?: string
  topic?: string
  mode?: string
  meetingUrl?: string
  room?: string
  /** One of the batch's courses; leave empty for the course the batch was created for. */
  courseId?: number
}

export async function searchBatches(params: {
  status?: string
  courseId?: number
  query?: string
  page?: number
}): Promise<PageResponse<BatchSummaryResponse>> {
  const { data } = await apiClient.get<PageResponse<BatchSummaryResponse>>('/api/batches', { params })
  return data
}

export async function createBatch(input: CreateBatchInput): Promise<BatchResponse> {
  const { data } = await apiClient.post<BatchResponse>('/api/batches', input)
  return data
}

/**
 * Whether the signed-in student has ever been placed in a batch. False for a new student whose course request has not
 * been approved; the portal then shows only the course list. Still true after a batch has ended.
 */
export async function fetchPlacement(): Promise<{ placed: boolean }> {
  const { data } = await apiClient.get<{ placed: boolean }>('/api/batches/mine/placed')
  return data
}

export async function getBatch(id: number | string): Promise<BatchResponse> {
  const { data } = await apiClient.get<BatchResponse>(`/api/batches/${id}`)
  return data
}

export interface ExtendBatchResult {
  batchId: number
  batchCode: string
  previousEndDate: string | null
  endDate: string
  status: string
  studentsExtended: number
}

/** A later end date for a batch, with the reason. A finished batch runs again; students and trainer are told. */
export async function extendBatch(batchId: number, input: { endDate: string; reason: string }): Promise<ExtendBatchResult> {
  const { data } = await apiClient.post<ExtendBatchResult>(`/api/batches/${batchId}/extend`, input)
  return data
}

/** Sets or extends how long one student may use the course. A student whose access ran out is on the course again. */
export async function extendAccess(enrollmentId: number, input: { accessUntil: string; reason: string }): Promise<EnrollmentResponse> {
  const { data } = await apiClient.post<EnrollmentResponse>(`/api/batches/enrollments/${enrollmentId}/access`, input)
  return data
}

export async function getRoster(batchId: number | string, includeExpired = false): Promise<EnrollmentResponse[]> {
  const { data } = await apiClient.get<EnrollmentResponse[]>(`/api/batches/${batchId}/students`, {
    params: includeExpired ? { includeExpired: true } : undefined,
  })
  return data
}

export interface EnrollmentOutcome {
  studentId: number
  success: boolean
  message: string
}

export interface EnrollmentResultResponse {
  batchId: number
  enrolled: number
  skipped: number
  seatsRemaining: number
  outcomes: EnrollmentOutcome[]
}

/** Reports per student rather than failing the whole call on one problem - a student
 * already enrolled elsewhere, for example, does not block the rest of the batch. */
export async function enrollStudents(
  batchId: number | string,
  studentIds: number[],
  /** Leave empty for the whole batch (its fee, every course); give a course to place them in that course alone. */
  courseId?: number,
): Promise<EnrollmentResultResponse> {
  const { data } = await apiClient.post<EnrollmentResultResponse>(`/api/batches/${batchId}/students`, {
    studentIds,
    courseId,
  })
  return data
}

/** Puts a course into the batch; everyone placed in the whole batch is enrolled in it at no extra fee. */
export async function addBatchCourse(batchId: number | string, input: { courseId: number; trainerId?: number }): Promise<BatchCourse[]> {
  const { data } = await apiClient.post<BatchCourse[]>(`/api/batches/${batchId}/courses`, input)
  return data
}

export async function changeBatchCourseTrainer(batchId: number | string, courseId: number, trainerId?: number): Promise<BatchCourse[]> {
  const { data } = await apiClient.put<BatchCourse[]>(`/api/batches/${batchId}/courses/${courseId}`, { trainerId })
  return data
}

/** Takes an added course out of the batch; the students' lessons for it are closed and their progress is kept. */
export async function removeBatchCourse(batchId: number | string, courseId: number): Promise<BatchCourse[]> {
  const { data } = await apiClient.delete<BatchCourse[]>(`/api/batches/${batchId}/courses/${courseId}`)
  return data
}

/** Sets what the whole batch costs. The rest of the batch is sent back as it is. */
export async function setBatchFee(batch: BatchResponse, fee: number | undefined): Promise<BatchResponse> {
  const { data } = await apiClient.put<BatchResponse>(`/api/batches/${batch.id}`, {
    name: batch.name,
    trainerId: batch.trainerId ?? undefined,
    startDate: batch.startDate,
    endDate: batch.endDate,
    startTime: batch.startTime,
    endTime: batch.endTime,
    classDays: batch.classDays,
    mode: batch.mode,
    capacity: batch.capacity,
    classroom: batch.classroom ?? undefined,
    meetingUrl: batch.meetingUrl ?? undefined,
    status: batch.status,
    coTrainerIds: batch.coTrainers.map((c) => c.trainerId),
    fee,
  })
  return data
}

export async function getBatchSessions(batchId: number | string): Promise<SessionResponse[]> {
  const { data } = await apiClient.get<SessionResponse[]>(`/api/batches/${batchId}/sessions`)
  return data
}

export async function getSession(id: number | string): Promise<SessionResponse> {
  const { data } = await apiClient.get<SessionResponse>(`/api/sessions/${id}`)
  return data
}

export async function createSession(input: CreateSessionInput): Promise<SessionResponse> {
  const { data } = await apiClient.post<SessionResponse>('/api/sessions', input)
  return data
}

export async function cancelSession(id: number | string, reason?: string): Promise<SessionResponse> {
  const { data } = await apiClient.post<SessionResponse>(`/api/sessions/${id}/cancel`, null, {
    params: { reason },
  })
  return data
}

export async function getSessionAttendance(sessionId: number | string): Promise<AttendanceResponse[]> {
  const { data } = await apiClient.get<AttendanceResponse[]>(`/api/sessions/${sessionId}/attendance`)
  return data
}

export interface MarkAttendanceEntry {
  studentId: number
  status: string
  remark?: string
}

export async function markAttendance(
  sessionId: number | string,
  entries: MarkAttendanceEntry[],
  correctionReason?: string,
): Promise<AttendanceResponse[]> {
  const { data } = await apiClient.post<AttendanceResponse[]>(`/api/sessions/${sessionId}/attendance`, {
    entries,
    correctionReason,
  })
  return data
}

export interface BatchAttendanceSummary {
  studentId: number
  studentName: string
  batchId: number
  attendedSessions: number
  totalSessions: number
  attendancePercent: number | null
  belowThreshold?: boolean
}

/** Every student's attendance across one batch, lowest first (trainer, coordinator or admin). */
export async function getBatchAttendance(batchId: number | string): Promise<BatchAttendanceSummary[]> {
  const { data } = await apiClient.get<BatchAttendanceSummary[]>(`/api/attendance/batches/${batchId}`)
  return data
}
