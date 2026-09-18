import { apiClient } from './client'
import type { PageResponse } from './types'

export interface AnnouncementResponse {
  id: number
  title: string
  message: string
  audience: 'ALL' | 'ROLE' | 'BATCH' | 'COURSE'
  targetRole: string | null
  targetId: number | null
  sendEmail: boolean
  expiresAt: string | null
  withdrawn: boolean
  /** Staff only - null for everyone else. */
  recipientCount: number | null
  createdBy: number | null
  createdAt: string
  updatedAt: string
}

export interface AnnouncementInput {
  title: string
  message: string
  audience: AnnouncementResponse['audience']
  targetRole?: string
  targetId?: number
  sendEmail?: boolean
  expiresAt?: string
}

export interface AnnouncementUpdateInput {
  title: string
  message: string
  expiresAt?: string
}

export async function listAnnouncements(page = 0): Promise<PageResponse<AnnouncementResponse>> {
  const { data } = await apiClient.get<PageResponse<AnnouncementResponse>>('/api/announcements', {
    params: { page, size: 20 },
  })
  return data
}

export async function getAnnouncement(id: number | string): Promise<AnnouncementResponse> {
  const { data } = await apiClient.get<AnnouncementResponse>(`/api/announcements/${id}`)
  return data
}

export async function createAnnouncement(input: AnnouncementInput): Promise<AnnouncementResponse> {
  const { data } = await apiClient.post<AnnouncementResponse>('/api/announcements', input)
  return data
}

export async function updateAnnouncement(
  id: number | string,
  input: AnnouncementUpdateInput,
): Promise<AnnouncementResponse> {
  const { data } = await apiClient.put<AnnouncementResponse>(`/api/announcements/${id}`, input)
  return data
}

export async function withdrawAnnouncement(id: number | string): Promise<AnnouncementResponse> {
  const { data } = await apiClient.post<AnnouncementResponse>(`/api/announcements/${id}/withdraw`)
  return data
}
