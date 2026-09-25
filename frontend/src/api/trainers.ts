import { apiClient } from './client'
import type { PageResponse } from './types'

/** Doc S6.3: a trainer is a profile in admission-service, separate from the
 * identity-service user id - batch-service's trainerId refers to this id. */
export interface TrainerSummaryResponse {
  id: number
  userId: number
  employeeCode: string
  fullName: string
  email: string
  phone: string
  specialization: string | null
  status: string
}

export interface TrainerResponse extends TrainerSummaryResponse {
  qualification: string | null
  experienceYears: number | null
  bio: string | null
  joinedOn: string | null
  courseIds: number[]
}

export interface CreateTrainerInput {
  firstName: string
  lastName: string
  email: string
  phone: string
  specialization?: string
  qualification?: string
  experienceYears?: number
  bio?: string
}

export async function listTrainers(params: {
  status?: string
  query?: string
  page?: number
}): Promise<PageResponse<TrainerSummaryResponse>> {
  const { data } = await apiClient.get<PageResponse<TrainerSummaryResponse>>('/api/trainers', { params })
  return data
}

/** Creates the login account and the trainer profile together (Doc S6.3). */
export async function createTrainer(input: CreateTrainerInput): Promise<TrainerResponse> {
  const { data } = await apiClient.post<TrainerResponse>('/api/trainers', input)
  return data
}
