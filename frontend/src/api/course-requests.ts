import { apiClient } from './client'
import type { PageResponse } from './types'

export type CourseRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface CourseRequestResponse {
  id: number
  status: CourseRequestStatus
  studentId: number
  studentCode: string | null
  studentName: string | null
  studentEmail: string | null
  studentPhone: string | null
  courseId: number
  courseCode: string | null
  courseTitle: string | null
  preferredBatchId: number | null
  message: string | null
  decidedBy: number | null
  decidedByName: string | null
  decidedAt: string | null
  decisionNote: string | null
  approvedBatchId: number | null
  enrollmentId: number | null
  createdAt: string
}

export interface CreateCourseRequestInput {
  courseId: number
  batchId?: number
  message?: string
}

export async function createCourseRequest(input: CreateCourseRequestInput): Promise<CourseRequestResponse> {
  const { data } = await apiClient.post<CourseRequestResponse>('/api/course-requests', input)
  return data
}

export async function myCourseRequests(): Promise<CourseRequestResponse[]> {
  const { data } = await apiClient.get<CourseRequestResponse[]>('/api/course-requests/mine')
  return data
}

export async function cancelCourseRequest(id: number): Promise<CourseRequestResponse> {
  const { data } = await apiClient.post<CourseRequestResponse>(`/api/course-requests/${id}/cancel`)
  return data
}

export async function listCourseRequests(params: {
  status?: CourseRequestStatus
  page?: number
}): Promise<PageResponse<CourseRequestResponse>> {
  const { data } = await apiClient.get<PageResponse<CourseRequestResponse>>('/api/course-requests', { params })
  return data
}

export async function approveCourseRequest(
  id: number,
  input: { batchId?: number; note?: string },
): Promise<CourseRequestResponse> {
  const { data } = await apiClient.post<CourseRequestResponse>(`/api/course-requests/${id}/approve`, input)
  return data
}

export async function rejectCourseRequest(id: number, note: string): Promise<CourseRequestResponse> {
  const { data } = await apiClient.post<CourseRequestResponse>(`/api/course-requests/${id}/reject`, { note })
  return data
}
