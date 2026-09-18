import { apiClient } from './client'

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

export async function claimCertificate(courseId: number): Promise<CertificateResponse> {
  const { data } = await apiClient.post<CertificateResponse>('/api/certificates/claim', { courseId })
  return data
}

/** Downloads the PDF and hands back an object URL the caller must revoke when done with it. */
export async function certificatePdfObjectUrl(id: number | string): Promise<string> {
  const { data } = await apiClient.get<ArrayBuffer>(`/api/certificates/${id}/pdf`, {
    responseType: 'arraybuffer',
  })
  return URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
}
