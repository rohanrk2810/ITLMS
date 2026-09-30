import { apiClient } from './client'
import type { PageResponse } from './types'

export interface CertificateResponse {
  id: number
  certificateNo: string
  verificationCode: string
  verificationUrl: string
  studentId: number
  studentCode: string | null
  studentName: string
  courseId: number
  courseTitle: string
  batchId: number | null
  batchName: string | null
  issueDate: string
  status: 'ISSUED' | 'REVOKED'
  revokedAt: string | null
  revokedReason: string | null
}

export type EligibilityOutcome = 'MET' | 'NOT_MET' | 'UNAVAILABLE' | 'NOT_REQUIRED'

export interface EligibilityCriterion {
  key: string
  name: string
  outcome: EligibilityOutcome
  detail: string | null
}

export interface EligibilityResponse {
  studentId: number
  courseId: number
  batchId: number | null
  eligible: boolean
  criteria: EligibilityCriterion[]
  outstandingWork: string[]
  existingCertificateNo: string | null
}

/** My certificates, most recent first. */
export async function myCertificates(): Promise<CertificateResponse[]> {
  const { data } = await apiClient.get<CertificateResponse[]>('/api/certificates/me')
  return data
}

export async function certificateEligibility(studentId: number, courseId: number): Promise<EligibilityResponse> {
  const { data } = await apiClient.get<EligibilityResponse>('/api/certificates/eligibility', {
    params: { studentId, courseId },
  })
  return data
}

export type CertificateRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ISSUED'

export interface CertificateRequestResponse {
  id: number
  studentId: number
  studentCode: string | null
  studentName: string
  courseId: number
  courseTitle: string
  batchId: number | null
  batchName: string | null
  status: CertificateRequestStatus
  requestedAt: string
  reviewedAt: string | null
  rejectionReason: string | null
  certificateId: number | null
}

export interface CertificateRequestDetailResponse {
  request: CertificateRequestResponse
  /** Re-checked now. Absent for a trainer. */
  eligibility: EligibilityResponse | null
}

export interface CertificateRequestFilter {
  status?: CertificateRequestStatus[]
  courseId?: number
  q?: string
  from?: string
  to?: string
  page?: number
}

export async function requestCertificate(courseId: number): Promise<CertificateRequestResponse> {
  const { data } = await apiClient.post<CertificateRequestResponse>('/api/certificates/requests', { courseId })
  return data
}

export async function myCertificateRequests(): Promise<CertificateRequestResponse[]> {
  const { data } = await apiClient.get<CertificateRequestResponse[]>('/api/certificates/requests/me')
  return data
}

export async function searchCertificateRequests(
  filter: CertificateRequestFilter,
): Promise<PageResponse<CertificateRequestResponse>> {
  const { data } = await apiClient.get<PageResponse<CertificateRequestResponse>>('/api/certificates/requests', {
    params: {
      status: filter.status?.join(','),
      courseId: filter.courseId || undefined,
      q: filter.q || undefined,
      from: filter.from || undefined,
      to: filter.to || undefined,
      page: filter.page ?? 0,
      size: 20,
    },
  })
  return data
}

export async function getCertificateRequest(id: number): Promise<CertificateRequestDetailResponse> {
  const { data } = await apiClient.get<CertificateRequestDetailResponse>(`/api/certificates/requests/${id}`)
  return data
}

export async function approveCertificateRequest(id: number): Promise<CertificateRequestResponse> {
  const { data } = await apiClient.post<CertificateRequestResponse>(`/api/certificates/requests/${id}/approve`)
  return data
}

export async function rejectCertificateRequest(id: number, reason: string): Promise<CertificateRequestResponse> {
  const { data } = await apiClient.post<CertificateRequestResponse>(`/api/certificates/requests/${id}/reject`, {
    reason,
  })
  return data
}

export async function issueCertificateForRequest(id: number): Promise<CertificateResponse> {
  const { data } = await apiClient.post<CertificateResponse>(`/api/certificates/requests/${id}/issue`)
  return data
}

/** Every certificate, staff view. */
export async function listCertificates(page = 0): Promise<PageResponse<CertificateResponse>> {
  const { data } = await apiClient.get<PageResponse<CertificateResponse>>('/api/certificates', {
    params: { page, size: 50 },
  })
  return data
}

export async function revokeCertificate(id: number, reason: string): Promise<CertificateResponse> {
  const { data } = await apiClient.post<CertificateResponse>(`/api/certificates/${id}/revoke`, { reason })
  return data
}

export interface VerificationResponse {
  certificateNo: string
  status: 'VALID' | 'REVOKED'
  studentName: string
  courseTitle: string
  issueDate: string
  issuedBy: string
}

/** Public: no sign-in. The code is printed beside the certificate number. */
export async function verifyCertificate(certificateNo: string, code: string): Promise<VerificationResponse> {
  const { data } = await apiClient.get<VerificationResponse>(
    `/api/certificates/verify/${encodeURIComponent(certificateNo)}`,
    { params: { code } },
  )
  return data
}

/** Downloads the PDF and hands back an object URL the caller must revoke when done with it. */
export async function certificatePdfObjectUrl(id: number | string): Promise<string> {
  const { data } = await apiClient.get<ArrayBuffer>(`/api/certificates/${id}/pdf`, {
    responseType: 'arraybuffer',
  })
  return URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
}
