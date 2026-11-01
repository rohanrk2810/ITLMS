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

export interface StudentProfileInput {
  dateOfBirth?: string
  gender?: string
  highestEducation?: string
  college?: string
  graduationYear?: number
  addressLine?: string
  city?: string
  state?: string
  pincode?: string
  guardianName?: string
  guardianPhone?: string
  emergencyContact?: string
  remarks?: string
}

/** Staff edit of the profile. Name, email and phone belong to the login account (see updateUser). */
export async function updateStudent(id: number | string, input: StudentProfileInput): Promise<StudentResponse> {
  const { data } = await apiClient.put<StudentResponse>(`/api/students/${id}`, input)
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

/** Every student with the full profile, for a staff export. The server records the export in the audit log. */
export async function exportStudents(params: { status?: string; query?: string }): Promise<StudentResponse[]> {
  const { data } = await apiClient.get<StudentResponse[]>('/api/students/export', { params })
  return data
}
