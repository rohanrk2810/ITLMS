import { apiClient } from './client'
import type { PageResponse } from './types'

export interface SubmissionFileResponse {
  fileRef: string
  fileName: string | null
  contentType: string | null
  sizeBytes: number | null
  uploadedAt: string
}

export interface SubmissionResponse {
  id: number
  assignmentId: number
  studentId: number
  studentName: string
  textAnswer: string | null
  submittedAt: string
  submissionCount: number
  /** SUBMITTED, LATE, EVALUATED or RETURNED. */
  status: string
  marks: number | null
  feedback: string | null
  evaluatedAt: string | null
  files: SubmissionFileResponse[]
}

export interface AssignmentResponse {
  id: number
  batchId: number
  courseId: number
  title: string
  instructions: string | null
  attachmentRef: string | null
  dueAt: string
  maxMarks: number
  allowLate: boolean
  mandatory: boolean
  status: string
  trainerId: number | null
  publishedAt: string | null
  overdue: boolean
  submissionCount: number | null
  evaluatedCount: number | null
  /** The signed-in student's own submission, when there is one. */
  mySubmission: SubmissionResponse | null
}

export interface SubmitAssignmentInput {
  textAnswer?: string
  files?: { fileRef: string; fileName?: string; contentType?: string; sizeBytes?: number }[]
}

export interface EvaluateSubmissionInput {
  marks?: number
  feedback?: string
  returnForRework?: boolean
}

/** Published work in the signed-in student's batches, with their own submissions. */
export async function myAssignments(): Promise<AssignmentResponse[]> {
  const { data } = await apiClient.get<AssignmentResponse[]>('/api/assignments/mine')
  return data
}

export async function getAssignment(id: number | string): Promise<AssignmentResponse> {
  const { data } = await apiClient.get<AssignmentResponse>(`/api/assignments/${id}`)
  return data
}

/** With submitted/evaluated counts - the trainer's view of one batch's assignments. */
export async function getBatchAssignments(batchId: number | string): Promise<PageResponse<AssignmentResponse>> {
  const { data } = await apiClient.get<PageResponse<AssignmentResponse>>(`/api/assignments/batches/${batchId}`)
  return data
}

/** Submitting again before it is marked replaces the earlier version. */
export async function submitAssignment(
  id: number | string,
  input: SubmitAssignmentInput,
): Promise<SubmissionResponse> {
  const { data } = await apiClient.post<SubmissionResponse>(`/api/assignments/${id}/submissions`, input)
  return data
}

export async function getSubmissions(assignmentId: number | string): Promise<SubmissionResponse[]> {
  const { data } = await apiClient.get<SubmissionResponse[]>(`/api/assignments/${assignmentId}/submissions`)
  return data
}

export async function getSubmission(id: number | string): Promise<SubmissionResponse> {
  const { data } = await apiClient.get<SubmissionResponse>(`/api/submissions/${id}`)
  return data
}

/** Marks and feedback, or returnForRework to send it back without a mark. */
export async function evaluateSubmission(
  id: number | string,
  input: EvaluateSubmissionInput,
): Promise<SubmissionResponse> {
  const { data } = await apiClient.put<SubmissionResponse>(`/api/submissions/${id}/evaluate`, input)
  return data
}
