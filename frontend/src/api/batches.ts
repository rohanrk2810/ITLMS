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

export async function getBatch(id: number | string): Promise<BatchResponse> {
  const { data } = await apiClient.get<BatchResponse>(`/api/batches/${id}`)
  return data
}

export async function getRoster(batchId: number | string): Promise<EnrollmentResponse[]> {
  const { data } = await apiClient.get<EnrollmentResponse[]>(`/api/batches/${batchId}/students`)
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
): Promise<EnrollmentResultResponse> {
  const { data } = await apiClient.post<EnrollmentResultResponse>(`/api/batches/${batchId}/students`, { studentIds })
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
