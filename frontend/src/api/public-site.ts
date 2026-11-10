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
