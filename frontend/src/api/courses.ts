import { apiClient } from './client'

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
