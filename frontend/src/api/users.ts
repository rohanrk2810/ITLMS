import { apiClient } from './client'
import type { ApiMessage, PageResponse, UserResponse } from './types'

export interface CreateUserInput {
  firstName: string
  lastName: string
  email: string
  phone?: string
  role: string
  /** Leave blank to generate a temporary password the user must change at first sign-in. */
  password?: string
}

export interface UpdateUserInput {
  firstName: string
  lastName: string
  email: string
  phone?: string
}

/** Account administration (Doc S11). ADMIN has full control; COORDINATOR may look but not change. */
export async function searchUsers(params: {
  role?: string
  status?: string
  query?: string
  page?: number
}): Promise<PageResponse<UserResponse>> {
  const { data } = await apiClient.get<PageResponse<UserResponse>>('/api/users', { params })
  return data
}

export async function getUser(id: number | string): Promise<UserResponse> {
  const { data } = await apiClient.get<UserResponse>(`/api/users/${id}`)
  return data
}

/** Admin only. Leave the password blank to have a temporary one generated and emailed. */
export async function createUser(input: CreateUserInput): Promise<UserResponse> {
  const { data } = await apiClient.post<UserResponse>('/api/users', input)
  return data
}

export async function updateUser(id: number | string, input: UpdateUserInput): Promise<UserResponse> {
  const { data } = await apiClient.put<UserResponse>(`/api/users/${id}`, input)
  return data
}

/** ACTIVE, INACTIVE or BLOCKED. A reason is required - this is audited (Doc S12). */
export async function updateUserStatus(
  id: number | string,
  status: string,
  reason: string,
): Promise<UserResponse> {
  const { data } = await apiClient.patch<UserResponse>(`/api/users/${id}/status`, { status, reason })
  return data
}

/** Issues a temporary password by email and revokes existing sessions. */
export async function resetUserPassword(id: number | string): Promise<ApiMessage> {
  const { data } = await apiClient.post<ApiMessage>(`/api/users/${id}/reset-password`)
  return data
}
