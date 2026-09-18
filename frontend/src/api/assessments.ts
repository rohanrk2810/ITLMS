import { apiClient } from './client'

/** A test as it appears on the student's list: no questions, just whether they can sit it and how they did. */
export interface StudentQuizResponse {
  id: number
  courseId: number
  batchId: number | null
  title: string
  instructions: string | null
  durationMinutes: number
  totalMarks: number
  passPercentage: number
  attemptsAllowed: number
  attemptsUsed: number
  availableFrom: string | null
  availableUntil: string | null
  mandatory: boolean
  openNow: boolean
  canStart: boolean
  inProgressAttemptId: number | null
  bestPercentage: number | null
  passed: boolean | null
}

export interface AttemptOption {
  id: number
  optionText: string
}

export interface AttemptQuestion {
  id: number
  questionText: string
  type: 'SINGLE_CHOICE' | 'MULTI_CHOICE' | 'TRUE_FALSE'
  marks: number
  options: AttemptOption[]
}

/** The paper for a running attempt. Never carries the answer key (Doc S14). */
export interface AttemptViewResponse {
  attemptId: number
  quizId: number
  title: string
  instructions: string | null
  attemptNo: number
  attemptsAllowed: number
  totalMarks: number
  passPercentage: number
  startedAt: string
  expiresAt: string
  secondsRemaining: number
  questions: AttemptQuestion[]
}

export interface AnswerResult {
  questionId: number
  questionText: string
  type: string
  marks: number
  options: AttemptOption[]
  selectedOptionIds: number[]
  correctOptionIds: number[]
  correct: boolean
  marksAwarded: number
  explanation: string | null
}

export interface AttemptResultResponse {
  attemptId: number
  quizId: number
  quizTitle: string
  studentId: number
  studentName: string
  attemptNo: number
  status: string
  startedAt: string
  submittedAt: string | null
  resultVisible: boolean
  score: number | null
  totalMarks: number
  percentage: number | null
  passed: boolean | null
  passPercentage: number
  answers: AnswerResult[] | null
}

export interface AnswerSaveResponse {
  attemptId: number
  answeredCount: number
  questionCount: number
  expiresAt: string
  secondsRemaining: number
}

export type SubmitAnswer = { questionId: number; selectedOptionIds: number[] }

/** Tests I can take, with my attempts and best result so far. */
export async function availableQuizzes(): Promise<StudentQuizResponse[]> {
  const { data } = await apiClient.get<StudentQuizResponse[]>('/api/quizzes/available')
  return data
}

/** Starts a new attempt, or resumes the one already running. */
export async function startOrResumeAttempt(quizId: number | string): Promise<AttemptViewResponse> {
  const { data } = await apiClient.post<AttemptViewResponse>(`/api/quizzes/${quizId}/attempts`)
  return data
}

export async function getAttemptPaper(attemptId: number | string): Promise<AttemptViewResponse> {
  const { data } = await apiClient.get<AttemptViewResponse>(`/api/quiz-attempts/${attemptId}`)
  return data
}

export async function saveAttemptAnswers(
  attemptId: number | string,
  answers: SubmitAnswer[],
): Promise<AnswerSaveResponse> {
  const { data } = await apiClient.put<AnswerSaveResponse>(`/api/quiz-attempts/${attemptId}/answers`, { answers })
  return data
}

export async function submitAttempt(
  attemptId: number | string,
  answers: SubmitAnswer[],
): Promise<AttemptResultResponse> {
  const { data } = await apiClient.post<AttemptResultResponse>(`/api/quiz-attempts/${attemptId}/submit`, { answers })
  return data
}

export async function getAttemptResult(attemptId: number | string): Promise<AttemptResultResponse> {
  const { data } = await apiClient.get<AttemptResultResponse>(`/api/quiz-attempts/${attemptId}/result`)
  return data
}

/** Every attempt the signed-in student has made at a test, most recent first. */
export async function getMyAttempts(quizId: number | string): Promise<AttemptResultResponse[]> {
  const { data } = await apiClient.get<AttemptResultResponse[]>(`/api/quizzes/${quizId}/attempts/mine`)
  return data
}
