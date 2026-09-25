import { apiClient } from './client'
import type { PageResponse } from './types'

/** Must match Announcement.Category in notification-service. */
export const ANNOUNCEMENT_CATEGORIES = [
  { code: 'GENERAL', label: 'General' },
  { code: 'COURSE', label: 'Course' },
  { code: 'BATCH', label: 'Batch' },
  { code: 'LIVE_CLASS', label: 'Live class' },
  { code: 'TEST', label: 'Test' },
  { code: 'ASSIGNMENT', label: 'Assignment' },
  { code: 'INSTITUTE', label: 'Institute' },
] as const

export type AnnouncementCategory = (typeof ANNOUNCEMENT_CATEGORIES)[number]['code']

export function announcementCategoryLabel(code: string): string {
  return ANNOUNCEMENT_CATEGORIES.find((c) => c.code === code)?.label ?? code
}

export interface AnnouncementResponse {
  id: number
  title: string
  message: string
  audience: 'ALL' | 'ROLE' | 'BATCH' | 'COURSE'
  category: AnnouncementCategory
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
  category?: AnnouncementCategory
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
