import { apiClient } from './client'
import type { PageResponse } from './types'

export interface StudentSummaryResponse {
  id: number
  userId: number
  studentCode: string
  fullName: string
  email: string
  phone: string
  status: string
}

export interface StudentResponse {
  id: number
  userId: number
  studentCode: string
  fullName: string
  email: string
  phone: string
  dateOfBirth: string | null
  gender: string | null
  highestEducation: string | null
  college: string | null
  graduationYear: number | null
  addressLine: string | null
  city: string | null
  state: string | null
  pincode: string | null
  guardianName: string | null
  guardianPhone: string | null
  emergencyContact: string | null
  admissionDate: string | null
  admissionSource: string | null
  status: string
  remarks: string | null
  createdAt: string
}

export async function searchStudents(params: {
  status?: string
  query?: string
  page?: number
}): Promise<PageResponse<StudentSummaryResponse>> {
  const { data } = await apiClient.get<PageResponse<StudentSummaryResponse>>('/api/students', { params })
  return data
}

export async function getStudent(id: number | string): Promise<StudentResponse> {
  const { data } = await apiClient.get<StudentResponse>(`/api/students/${id}`)
  return data
}

/** ACTIVE, ALUMNI, DROPPED or SUSPENDED - separate from the login account's own status. */
export async function updateStudentStatus(
  id: number | string,
  status: string,
  reason?: string,
): Promise<StudentResponse> {
  const { data } = await apiClient.patch<StudentResponse>(`/api/students/${id}/status`, null, {
    params: { status, reason },
  })
  return data
}
