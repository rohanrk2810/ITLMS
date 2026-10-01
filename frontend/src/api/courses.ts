import type { CodeLanguageCode } from './code'
import { apiClient } from './client'
import type { PageResponse } from './types'

/** Doc S7.2: how far the signed-in student has got in one course. */
export interface ProgressResponse {
  enrollmentId: number
  studentId: number
  courseId: number
  batchId: number | null
  status: string
  progressPercent: number
  completedLessons: number
  totalLessons: number
  allLessonsComplete: boolean
  completedAt: string | null
}

export interface LessonResponse {
  id: number
  moduleId: number
  title: string
  type: 'VIDEO' | 'PDF' | 'NOTE' | 'LINK' | 'TEXT'
  /** Null unless the caller is enrolled or the lesson is a preview. */
  contentUrl: string | null
  /** A file-service id, for PDF lessons. */
  contentFileRef: string | null
  textContent: string | null
  durationMinutes: number | null
  sequenceNo: number
  preview: boolean
  mandatory: boolean
  accessible: boolean
  completed: boolean | null
  watchedSeconds: number | null
  /** The lesson's practice editor language; null when it has none (and while the lesson is locked). */
  codeLanguage: CodeLanguageCode | null
  starterCode: string | null
  /** Practice editor: the student may pick another language. */
  allowLanguageChoice: boolean
}

export interface ModuleResponse {
  id: number
  courseId: number
  title: string
  description: string | null
  sequenceNo: number
  lessons: LessonResponse[]
}

export interface CourseResponse {
  id: number
  title: string
  code: string
  summary: string | null
  description: string | null
  learningOutcomes: string | null
  prerequisites: string | null
  technologyStack: string | null
  durationHours: number | null
  level: string
  fee: number | null
  thumbnailRef: string | null
  status: string
  publishedAt: string | null
  moduleCount: number
  lessonCount: number
  createdAt: string
}

export interface CourseDetailResponse {
  course: CourseResponse
  modules: ModuleResponse[]
  enrolled: boolean
  progress: ProgressResponse | null
}

/** The catalog-card shape: title, code and summary, nothing else. */
export interface CourseSummaryResponse {
  id: number
  title: string
  code: string
  summary: string | null
  technologyStack: string | null
  durationHours: number | null
  level: string
  fee: number | null
  thumbnailRef: string | null
  status: string
}

/** Every course the signed-in student is taking (Doc S15). */
export async function myCourseProgress(): Promise<ProgressResponse[]> {
  const { data } = await apiClient.get<ProgressResponse[]>('/api/progress/me')
  return data
}

/** The curriculum, with lesson content unlocked for an enrolled student. */
export async function getCourseDetail(courseId: number | string): Promise<CourseDetailResponse> {
  const { data } = await apiClient.get<CourseDetailResponse>(`/api/courses/${courseId}`)
  return data
}

/** Titles and summaries for a set of course ids, in one call. */
export async function lookupCourses(courseIds: number[]): Promise<CourseSummaryResponse[]> {
  if (courseIds.length === 0) return []
  const { data } = await apiClient.post<CourseSummaryResponse[]>('/api/courses/internal/lookup', courseIds)
  return data
}

export async function recordLessonProgress(
  lessonId: number | string,
  body: { watchedSeconds?: number; completed?: boolean },
): Promise<ProgressResponse> {
  const { data } = await apiClient.post<ProgressResponse>(`/api/progress/lessons/${lessonId}`, body)
  return data
}

// -----------------------------------------------------------------
// Authoring (Doc S6.5, S6.8) - staff creates a course, staff and
// trainers build its modules and lessons.
// -----------------------------------------------------------------

export interface CourseInput {
  title: string
  code: string
  summary?: string
  description?: string
  learningOutcomes?: string
  prerequisites?: string
  technologyStack?: string
  durationHours?: number
  level?: string
  fee?: number
  thumbnailRef?: string
}

export interface ModuleInput {
  title: string
  description?: string
  sequenceNo?: number
}

export interface LessonInput {
  title: string
  type: LessonResponse['type']
  contentUrl?: string
  contentFileRef?: string
  textContent?: string
  durationMinutes?: number
  sequenceNo?: number
  preview?: boolean
  mandatory?: boolean
  /** Adds a practice editor to the lesson. Starter code needs it. */
  codeLanguage?: CodeLanguageCode
  starterCode?: string
  /** Practice editor: let the student pick the language. Ignored for SQL. */
  allowLanguageChoice?: boolean
}

/** The staff view across every status, not just published courses. */
export async function searchCourses(params: {
  status?: string
  level?: string
  query?: string
  page?: number
}): Promise<PageResponse<CourseSummaryResponse>> {
  const { data } = await apiClient.get<PageResponse<CourseSummaryResponse>>('/api/courses', { params })
  return data
}

export async function createCourse(input: CourseInput): Promise<CourseResponse> {
  const { data } = await apiClient.post<CourseResponse>('/api/courses', input)
  return data
}

export async function updateCourse(courseId: number | string, input: CourseInput): Promise<CourseResponse> {
  const { data } = await apiClient.put<CourseResponse>(`/api/courses/${courseId}`, input)
  return data
}

/** Refused unless the summary, description and at least one mandatory lesson are in place (Doc S14). */
export async function publishCourse(courseId: number | string): Promise<CourseResponse> {
  const { data } = await apiClient.post<CourseResponse>(`/api/courses/${courseId}/publish`)
  return data
}

export async function archiveCourse(courseId: number | string): Promise<CourseResponse> {
  const { data } = await apiClient.post<CourseResponse>(`/api/courses/${courseId}/archive`)
  return data
}

export async function addModule(courseId: number | string, input: ModuleInput): Promise<ModuleResponse> {
  const { data } = await apiClient.post<ModuleResponse>(`/api/courses/${courseId}/modules`, input)
  return data
}

export async function deleteModule(moduleId: number | string): Promise<void> {
  await apiClient.delete(`/api/modules/${moduleId}`)
}

export async function addLesson(moduleId: number | string, input: LessonInput): Promise<LessonResponse> {
  const { data } = await apiClient.post<LessonResponse>(`/api/modules/${moduleId}/lessons`, input)
  return data
}

export async function deleteLesson(lessonId: number | string): Promise<void> {
  await apiClient.delete(`/api/lessons/${lessonId}`)
}
