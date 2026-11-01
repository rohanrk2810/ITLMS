import { apiClient } from './client'

export type IdCardPersonType = 'STUDENT' | 'TRAINER' | 'STAFF'
export type IdCardStatus = 'ISSUED' | 'REVOKED' | 'EXPIRED'

export interface IdCardResponse {
  id: number
  cardNo: string
  verificationCode: string
  verificationUrl: string
  personType: IdCardPersonType
  personId: number | null
  userId: number | null
  personCode: string | null
  fullName: string
  designation: string | null
  batchId: number | null
  batchName: string | null
  hasPhoto: boolean
  issuedOn: string
  validUntil: string
  status: IdCardStatus
  revokedReason: string | null
}

export interface IssueIdCardInput {
  personType: IdCardPersonType
  personId?: number
  fullName?: string
  designation?: string
  batchId?: number
  validUntil?: string
}

export interface IdCardVerification {
  cardNo: string
  status: 'VALID' | 'EXPIRED' | 'REVOKED'
  fullName: string
  personType: IdCardPersonType
  designation: string | null
  validUntil: string
  issuedBy: string
}

export async function issueIdCard(input: IssueIdCardInput): Promise<IdCardResponse> {
  const { data } = await apiClient.post<IdCardResponse>('/api/id-cards', input)
  return data
}

export async function listIdCards(params: {
  type?: IdCardPersonType
  status?: 'ISSUED' | 'REVOKED'
  q?: string
}): Promise<IdCardResponse[]> {
  const { data } = await apiClient.get<IdCardResponse[]>('/api/id-cards', { params })
  return data
}

export async function myIdCards(): Promise<IdCardResponse[]> {
  const { data } = await apiClient.get<IdCardResponse[]>('/api/id-cards/me')
  return data
}

export async function uploadIdCardPhoto(id: number, file: File): Promise<IdCardResponse> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.put<IdCardResponse>(`/api/id-cards/${id}/photo`, form)
  return data
}

export async function revokeIdCard(id: number, reason: string): Promise<IdCardResponse> {
  const { data } = await apiClient.post<IdCardResponse>(`/api/id-cards/${id}/revoke`, { reason })
  return data
}

/** The photo as an object URL (the request carries the token, so a plain <img src> cannot be used). Revoke it when done. */
export async function idCardPhotoObjectUrl(id: number): Promise<string> {
  const { data } = await apiClient.get<Blob>(`/api/id-cards/${id}/photo`, { responseType: 'blob' })
  return URL.createObjectURL(data)
}

/** Downloads a PDF and hands back an object URL the caller must revoke when done with it. */
async function pdfObjectUrl(path: string, params?: Record<string, unknown>): Promise<string> {
  const { data } = await apiClient.get<ArrayBuffer>(path, { responseType: 'arraybuffer', params })
  return URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
}

export function idCardPdfObjectUrl(id: number): Promise<string> {
  return pdfObjectUrl(`/api/id-cards/${id}/pdf`)
}

export function idCardSheetObjectUrl(ids: number[]): Promise<string> {
  return pdfObjectUrl('/api/id-cards/sheet', { ids: ids.join(',') })
}

export async function verifyIdCard(cardNo: string, code: string): Promise<IdCardVerification> {
  const { data } = await apiClient.get<IdCardVerification>(`/api/id-cards/verify/${encodeURIComponent(cardNo)}`, {
    params: { code },
  })
  return data
}

/** Opens a PDF object URL in a new tab and frees it a minute later. */
export function openPdf(url: string) {
  window.open(url, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
