import { apiClient } from './client'
import type { CodeLanguageCode } from './code'
import type { PageResponse } from './types'

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
  /** Secure test mode: leaving the test window is reported, and too many reports end the attempt. */
  secureMode: boolean
  maxViolations: number
  /** The camera must be on: allowed before starting and watched for a visible face. */
  requireCamera: boolean
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

export type QuestionType = 'SINGLE_CHOICE' | 'MULTI_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'CODING'

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE_CHOICE: 'Single choice (MCQ)',
  MULTI_CHOICE: 'Multiple select',
  TRUE_FALSE: 'True / False',
  SHORT_ANSWER: 'Short answer',
  CODING: 'Coding',
}

export interface SampleTest {
  input: string
  expectedOutput: string
}

export interface AttemptQuestion {
  id: number
  questionText: string
  type: QuestionType
  marks: number
  options: AttemptOption[]
  /** CODING only. */
  codeLanguage: CodeLanguageCode | null
  starterCode: string | null
  /** The cases the student may see. Hidden ones are never sent. */
  sampleTests: SampleTest[]
  hiddenTestCount: number
}

export interface SavedAnswer {
  questionId: number
  selectedOptionIds: number[]
  answerText: string | null
  testsPassed: number | null
  testsTotal: number | null
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
  secureMode: boolean
  maxViolations: number
  /** The camera must stay on and show the student's face. */
  requireCamera: boolean
  /** Violations that have counted so far in this attempt. */
  violationCount: number
  questions: AttemptQuestion[]
  /** What this attempt already has saved, so a resumed sitting shows it again. */
  savedAnswers: SavedAnswer[]
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
  /** SHORT_ANSWER: what was typed. CODING: the submitted code. */
  answerText: string | null
  testsPassed: number | null
  testsTotal: number | null
  acceptedAnswers: string[]
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
  violationCount: number
  /** Camera events recorded (no face, several faces, camera off). Filled on the staff results sheet only. */
  cameraEventCount: number
  /** Set when the attempt was ended by the secure-test rules. */
  terminatedReason: string | null
  answers: AnswerResult[] | null
}

export type ViolationType =
  | 'TAB_SWITCH'
  | 'WINDOW_BLUR'
  | 'FULLSCREEN_EXIT'
  | 'COPY_ATTEMPT'
  | 'PASTE_ATTEMPT'
  | 'RIGHT_CLICK'
  | 'SHORTCUT_BLOCKED'
  | 'FACE_NOT_DETECTED'
  | 'MULTIPLE_FACES'
  | 'CAMERA_DISABLED'
  | 'CAMERA_PERMISSION_DENIED'

export const VIOLATION_LABEL: Record<ViolationType, string> = {
  FACE_NOT_DETECTED: 'Face not visible',
  MULTIPLE_FACES: 'More than one face',
  CAMERA_DISABLED: 'Camera turned off',
  CAMERA_PERMISSION_DENIED: 'Camera permission removed',
  TAB_SWITCH: 'Left the test tab',
  WINDOW_BLUR: 'Left the test window',
  FULLSCREEN_EXIT: 'Left fullscreen',
  COPY_ATTEMPT: 'Tried to copy',
  PASTE_ATTEMPT: 'Pasted',
  RIGHT_CLICK: 'Right-clicked',
  SHORTCUT_BLOCKED: 'Used a blocked shortcut',
}

export interface ViolationOutcome {
  counted: boolean
  violationCount: number
  maxViolations: number
  /** Further warnings before the attempt ends. */
  warningsLeft: number
  terminated: boolean
  message: string | null
}

export interface ViolationEntry {
  id: number
  type: ViolationType
  counted: boolean
  detail: string | null
  occurredAt: string
  clientAt: string | null
}

/** Tells the server what the browser noticed. The server decides whether it counts and whether the attempt ends. */
export async function reportViolation(
  attemptId: number | string,
  type: ViolationType,
  detail?: string,
): Promise<ViolationOutcome> {
  const { data } = await apiClient.post<ViolationOutcome>(`/api/quiz-attempts/${attemptId}/violations`, {
    type,
    detail,
    clientAt: new Date().toISOString(),
  })
  return data
}

/** Everything the browser reported for one attempt - trainers of the test and staff. */
export async function listViolations(attemptId: number | string): Promise<ViolationEntry[]> {
  const { data } = await apiClient.get<ViolationEntry[]>(`/api/quiz-attempts/${attemptId}/violations`)
  return data
}

export interface AnswerSaveResponse {
  attemptId: number
  answeredCount: number
  questionCount: number
  expiresAt: string
  secondsRemaining: number
}

export type SubmitAnswer = { questionId: number; selectedOptionIds?: number[]; answerText?: string }

export interface CodingCaseResult {
  number: number
  hidden: boolean
  passed: boolean
  /** Null for a hidden case. */
  input: string | null
  expectedOutput: string | null
  actualOutput: string | null
  error: string | null
  /** Measured for this test; also given for hidden tests, since a measurement is not an answer. */
  timeSeconds: number | null
  memoryKb: number | null
}

export interface CodingRunResponse {
  questionId: number
  passed: number
  total: number
  compileError: string | null
  cases: CodingCaseResult[]
}

/** Runs a coding question's test cases against the code, and keeps the code as the answer. */
export async function runCodingTests(
  attemptId: number | string,
  questionId: number,
  sourceCode: string,
): Promise<CodingRunResponse> {
  const { data } = await apiClient.post<CodingRunResponse>(
    `/api/quiz-attempts/${attemptId}/questions/${questionId}/run-tests`,
    { sourceCode },
  )
  return data
}

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

// -----------------------------------------------------------------
// Authoring (Doc S6.11) - trainers and staff. QuizResponse carries the
// answer key and is only ever returned to them, never to a student.
// -----------------------------------------------------------------

export interface QuizOption {
  id: number
  optionText: string
  correct: boolean
  sequenceNo: number
}

export interface QuizQuestionWithKey {
  id: number
  questionText: string
  type: string
  marks: number
  sequenceNo: number
  explanation: string | null
  options: QuizOption[]
  codeLanguage: CodeLanguageCode | null
  starterCode: string | null
  acceptedAnswers: string[]
  testCases: QuizTestCase[]
}

export interface QuizTestCase {
  id: number
  sequenceNo: number
  input: string
  expectedOutput: string
  hidden: boolean
  weight: number
}

export interface QuizResponse {
  id: number
  courseId: number
  batchId: number | null
  title: string
  instructions: string | null
  durationMinutes: number
  passPercentage: number
  attemptsAllowed: number
  totalMarks: number
  availableFrom: string | null
  availableUntil: string | null
  shuffleQuestions: boolean
  showResultImmediately: boolean
  mandatory: boolean
  secureMode: boolean
  maxViolations: number
  requireCamera: boolean
  status: string
  trainerId: number | null
  publishedAt: string | null
  questionCount: number
  /** Present on the single-test view only. */
  questions: QuizQuestionWithKey[] | null
}

export interface QuizInput {
  courseId: number
  batchId?: number
  title: string
  instructions?: string
  durationMinutes: number
  passPercentage?: number
  attemptsAllowed?: number
  availableFrom?: string
  availableUntil?: string
  shuffleQuestions?: boolean
  showResultImmediately?: boolean
  mandatory?: boolean
  secureMode?: boolean
  maxViolations?: number
  requireCamera?: boolean
}

export interface QuestionOptionInput {
  optionText: string
  correct: boolean
}

export interface TestCaseInput {
  input: string
  expectedOutput: string
  hidden: boolean
  weight: number
}

export interface QuestionInput {
  questionText: string
  /** Default SINGLE_CHOICE. */
  type?: QuestionType
  marks?: number
  explanation?: string
  /** Choice types only. */
  options?: QuestionOptionInput[]
  /** CODING only. */
  codeLanguage?: CodeLanguageCode
  starterCode?: string
  /** SHORT_ANSWER only. */
  acceptedAnswers?: string[]
  /** CODING only. */
  testCases?: TestCaseInput[]
}

export async function listQuizzes(courseId?: number): Promise<PageResponse<QuizResponse>> {
  const { data } = await apiClient.get<PageResponse<QuizResponse>>('/api/quizzes', { params: { courseId } })
  return data
}

/** With the answer key - trainers of the course/batch, and staff. */
export async function getQuiz(quizId: number | string): Promise<QuizResponse> {
  const { data } = await apiClient.get<QuizResponse>(`/api/quizzes/${quizId}`)
  return data
}

export async function createQuiz(input: QuizInput): Promise<QuizResponse> {
  const { data } = await apiClient.post<QuizResponse>('/api/quizzes', input)
  return data
}

export async function addQuestion(quizId: number | string, input: QuestionInput): Promise<QuizResponse> {
  const { data } = await apiClient.post<QuizResponse>(`/api/quizzes/${quizId}/questions`, input)
  return data
}

export async function deleteQuestion(questionId: number | string): Promise<QuizResponse> {
  const { data } = await apiClient.delete<QuizResponse>(`/api/quizzes/questions/${questionId}`)
  return data
}

/** Needs at least one question. The batch is notified. */
export async function publishQuiz(quizId: number | string): Promise<QuizResponse> {
  const { data } = await apiClient.post<QuizResponse>(`/api/quizzes/${quizId}/publish`)
  return data
}

/** No new attempts; running attempts are scored as they stand. */
export async function closeQuiz(quizId: number | string): Promise<QuizResponse> {
  const { data } = await apiClient.post<QuizResponse>(`/api/quizzes/${quizId}/close`)
  return data
}

/** The trainer's results sheet, best score first. */
export async function getQuizResults(quizId: number | string): Promise<AttemptResultResponse[]> {
  const { data } = await apiClient.get<AttemptResultResponse[]>(`/api/quizzes/${quizId}/results`)
  return data
}
