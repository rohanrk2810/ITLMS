import { apiClient } from './client'
import type { ApiMessage, PageResponse } from './types'

export interface NotificationResponse {
  id: number
  type: string
  title: string
  message: string
  actionUrl: string | null
  read: boolean
  readAt: string | null
  createdAt: string
}

export async function listNotifications(unreadOnly = false): Promise<PageResponse<NotificationResponse>> {
  const { data } = await apiClient.get<PageResponse<NotificationResponse>>('/api/notifications', {
    params: { unreadOnly, size: 10 },
  })
  return data
}

export async function unreadNotificationCount(): Promise<number> {
  const { data } = await apiClient.get<{ unread: number }>('/api/notifications/unread-count')
  return data.unread
}

export async function markNotificationRead(id: number): Promise<NotificationResponse> {
  const { data } = await apiClient.put<NotificationResponse>(`/api/notifications/${id}/read`)
  return data
}

export async function markAllNotificationsRead(): Promise<ApiMessage> {
  const { data } = await apiClient.put<ApiMessage>('/api/notifications/read-all')
  return data
}
