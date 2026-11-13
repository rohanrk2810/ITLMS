import { apiClient } from './client'
import type { CourseSummaryResponse } from './courses'
import type { PageResponse } from './types'

/** Anonymous: the website lists published courses before anyone signs in. */
export async function getPublicCourses(): Promise<PageResponse<CourseSummaryResponse>> {
  const { data } = await apiClient.get<PageResponse<CourseSummaryResponse>>('/api/courses/public', {
    params: { size: 12 },
  })
  return data
}

/** Every published course, for the public /courses page. */
export async function getAllPublicCourses(): Promise<PageResponse<CourseSummaryResponse>> {
  const { data } = await apiClient.get<PageResponse<CourseSummaryResponse>>('/api/courses/public', {
    params: { size: 100 },
  })
  return data
}

/** A batch the website sells: what it covers, when it meets, its fee and the seats left. */
export interface PublicBatch {
  id: number
  batchCode: string
  name: string | null
  courseId: number
  courseTitles: string[]
  startDate: string
  endDate: string | null
  startTime: string
  endTime: string
  classDays: string[]
  mode: string
  seatsLeft: number
  fee: number
}

/** Anonymous: the batches that have a fee of their own and a free seat. */
export async function getPublicBatches(): Promise<PublicBatch[]> {
  const { data } = await apiClient.get<PublicBatch[]>('/api/public/batches')
  return data
}

export interface EnquiryInput {
  name: string
  phone: string
  email?: string
  interestedCourseId?: number
  message?: string
}

/** Anonymous: lands in the admission team's leads list. */
export async function sendEnquiry(input: EnquiryInput): Promise<void> {
  await apiClient.post('/api/leads/enquiry', input)
}
